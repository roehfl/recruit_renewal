package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationPiiPurgeRepository;
import com.shinyoung.recruit.domain.repository.RetentionHoldRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.DataSubjectDetailResponse;
import com.shinyoung.recruit.dto.response.DataSubjectSummaryResponse;
import com.shinyoung.recruit.exception.ApplicantNotFoundException;
import com.shinyoung.recruit.exception.InvalidRetentionRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 파기 대상자 조회(Phase 10, 읽기 전용). 검색은 조건 1개 이상 필수 + 상한 50건 —
 * 전체 나열을 막는다(PII 대량 노출 방지).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataSubjectLookupService {

    private static final int SEARCH_LIMIT = 50;

    private final ApplicantRepository applicantRepository;
    private final ApplicationPiiPurgeRepository applicationPiiPurgeRepository;
    private final RetentionHoldRepository retentionHoldRepository;
    private final RetentionPolicyService retentionPolicyService;
    private final RetentionEligibilityService retentionEligibilityService;
    private final StageRepository stageRepository;
    private final StageResultRepository stageResultRepository;
    private final Clock clock;

    public List<DataSubjectSummaryResponse> search(String name, String phoneNumber, String email) {
        String normalizedName = trimToNull(name);
        String normalizedPhone = digitsToNull(phoneNumber);
        String normalizedEmail = trimToNull(email);
        if (normalizedName == null && normalizedPhone == null && normalizedEmail == null) {
            throw new InvalidRetentionRequestException("검색 조건을 1개 이상 입력해야 합니다.");
        }

        List<Applicant> applicants = applicantRepository
                .searchDataSubjects(normalizedName, normalizedPhone, normalizedEmail,
                        PageRequest.of(0, SEARCH_LIMIT));
        if (applicants.isEmpty()) {
            return List.of();
        }

        List<Long> applicantIds = applicants.stream().map(Applicant::getId).toList();
        Map<Long, List<JobApplication>> applicationsByApplicant = applicationPiiPurgeRepository
                .findByApplicantIdIn(applicantIds).stream()
                .collect(Collectors.groupingBy(application -> application.getApplicant().getId()));
        Set<Long> heldApplicationIds = activeHoldApplicationIds(applicationsByApplicant.values().stream()
                .flatMap(List::stream)
                .map(JobApplication::getId)
                .toList());

        return applicants.stream()
                .map(applicant -> toSummary(
                        applicant,
                        applicationsByApplicant.getOrDefault(applicant.getId(), List.of()),
                        heldApplicationIds))
                .toList();
    }

    public DataSubjectDetailResponse getDetail(Long applicantId) {
        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new ApplicantNotFoundException("지원자를 찾을 수 없습니다."));

        LocalDateTime scanAt = LocalDateTime.now(clock);
        List<JobApplication> applications = applicationPiiPurgeRepository.findByApplicantId(applicantId);
        Set<Long> heldApplicationIds = activeHoldApplicationIds(
                applications.stream().map(JobApplication::getId).toList());
        boolean hasActiveHold = applications.stream()
                .anyMatch(application -> heldApplicationIds.contains(application.getId()));

        List<DataSubjectDetailResponse.ApplicationRow> rows = applications.stream()
                .map(application -> toRow(application, scanAt, heldApplicationIds))
                .toList();

        return new DataSubjectDetailResponse(
                applicant.getId(), applicant.getUserName(), applicant.getEmail(), applicant.getPhoneNumber(),
                hasActiveHold, rows);
    }

    /** hold 존재 여부 일괄 조회(Phase 10 N+1 회피) — 빈 목록이면 쿼리를 내지 않는다. */
    private Set<Long> activeHoldApplicationIds(List<Long> applicationIds) {
        return applicationIds.isEmpty()
                ? Set.of()
                : retentionHoldRepository.findActiveApplicationIds(applicationIds);
    }

    private DataSubjectSummaryResponse toSummary(
            Applicant applicant, List<JobApplication> applications, Set<Long> heldApplicationIds) {
        long purged = applications.stream().filter(a -> a.getPurgeResult() != null).count();
        LocalDateTime lastAppliedAt = applications.stream()
                .map(JobApplication::getSubmittedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        boolean hasActiveHold = applications.stream()
                .anyMatch(a -> heldApplicationIds.contains(a.getId()));

        return new DataSubjectSummaryResponse(
                applicant.getId(), applicant.getUserName(), applicant.getEmail(), applicant.getPhoneNumber(),
                applications.size(), purged, lastAppliedAt, hasActiveHold);
    }

    private DataSubjectDetailResponse.ApplicationRow toRow(
            JobApplication application, LocalDateTime scanAt, Set<Long> heldApplicationIds) {
        Long jobPostingId = application.getJobPosting().getId();
        List<Stage> finalStages = stageRepository
                .findByJobPostingIdOrderByStageOrderAscIdAsc(jobPostingId).stream()
                .filter(Stage::isFinalStage)
                .toList();
        StageResult finalStageResult = finalStages.size() == 1
                ? stageResultRepository
                        .findByStageIdAndJobApplicationIdIn(finalStages.get(0).getId(), List.of(application.getId()))
                        .stream().findFirst().orElse(null)
                : null;

        RetentionEligibilityService.EligibilityDecision decision = retentionEligibilityService.evaluate(
                application,
                application.getJobPosting(),
                retentionPolicyService.selectPolicy(jobPostingId, scanAt),
                heldApplicationIds.contains(application.getId()),
                finalStages,
                finalStageResult,
                scanAt);

        return new DataSubjectDetailResponse.ApplicationRow(
                application.getId(),
                application.getJobPostingTitleSnapshot(),
                application.getStatus().name(),
                application.getSubmittedAt(),
                application.getPurgeResult() == null ? null : application.getPurgeResult().name(),
                decision.eligible(),
                decision.reasonCode() == null ? null : decision.reasonCode().name());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** 휴대폰은 숫자만 남긴다. 숫자가 없으면 조건 미입력으로 본다. */
    private String digitsToNull(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }
}
