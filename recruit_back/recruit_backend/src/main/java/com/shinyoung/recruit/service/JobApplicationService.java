package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPositionRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.condition.AdminApplicationSearchCondition;
import com.shinyoung.recruit.dto.request.AdminApplicationSearchRequest;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import com.shinyoung.recruit.dto.request.ApplicationUpdateRequest;
import com.shinyoung.recruit.dto.response.AdminApplicationDetailResponse;
import com.shinyoung.recruit.dto.response.AdminApplicationSummaryResponse;
import com.shinyoung.recruit.dto.response.ApplicationDetailResponse;
import com.shinyoung.recruit.dto.response.MyApplicationResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.FinalSchoolCondition;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.enumeration.ReceptionStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobApplicationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicantRepository applicantRepository;
    private final JobPostingRepository jobPostingRepository;
    private final JobPositionRepository jobPositionRepository;
    private final StageResultRepository stageResultRepository;
    private final ApplicationBasicInfoRepository applicationBasicInfoRepository;
    private final ApplicationEducationRepository applicationEducationRepository;
    private final ApplicationAttachmentRepository applicationAttachmentRepository;
    private final ApplicationSubmitValidator applicationSubmitValidator;
    private final Clock clock;

    @Transactional
    public Long create(Long applicantId, ApplicationCreateRequest request) {
        Applicant applicant = findApplicant(applicantId);
        JobPosting jobPosting = findJobPosting(request.jobPostingId());
        validatePublishedAcceptingAndVisibleForCreate(jobPosting);
        validateApplicationFormConfig(jobPosting);
        validateNotDuplicated(applicantId, jobPosting.getId());

        JobPosition jobPosition = findJobPosition(request.jobPositionId(), jobPosting.getId());
        String workLocationCode = normalizeWorkLocationCode(request.workLocationCode());
        validateWorkLocationChoice(jobPosition, workLocationCode);

        JobApplication application = JobApplication.create(
                applicant,
                jobPosting,
                jobPosition,
                resolveApplicantNameSnapshot(applicant),
                jobPosting.getTitle(),
                jobPosition.getPositionName()
        );
        application.updateWorkLocation(workLocationCode, jobPosition.findWorkLocationName(workLocationCode));

        return jobApplicationRepository.save(application).getId();
    }

    public ApplicationDetailResponse getApplication(Long applicantId, Long applicationId) {
        JobApplication application = findApplication(applicantId, applicationId);
        return ApplicationDetailResponse.from(application);
    }

    public ApplicationDetailResponse getMyApplicationByJobPosting(Long applicantId, Long jobPostingId) {
        JobApplication application = jobApplicationRepository.findByApplicantIdAndJobPostingId(applicantId, jobPostingId)
                .orElseThrow(() -> new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. jobPostingId=" + jobPostingId));
        return ApplicationDetailResponse.from(application);
    }

    public PageResponse<MyApplicationResponse> getMyApplications(Long applicantId, int page, int size) {
        validatePageRequest(page, size);
        Page<JobApplication> applications = jobApplicationRepository.findMyApplications(
                applicantId,
                createPageRequest(page, size)
        );
        Map<Long, ApplicationResultSummary> summaries = loadApplicationResultSummaries(applications.getContent());

        return PageResponse.from(applications.map(application -> {
            ApplicationResultSummary summary = summaries.getOrDefault(
                    application.getId(),
                    ApplicationResultSummary.empty()
            );
            return MyApplicationResponse.from(
                    application,
                    isAccepting(application.getJobPosting()),
                    summary.announcedResultCount(),
                    summary.latestAnnouncedStageName(),
                    summary.latestResultStatus()
            );
        }));
    }

    @Transactional
    public Long updateDraft(Long applicantId, Long applicationId, ApplicationUpdateRequest request) {
        JobApplication application = findApplication(applicantId, applicationId);
        validatePublishedAndAccepting(application.getJobPosting());
        validateWritableForUpdate(application);

        JobPosition jobPosition = findJobPosition(request.jobPositionId(), application.getJobPosting().getId());
        String workLocationCode = normalizeWorkLocationCode(request.workLocationCode());
        validateWorkLocationChoice(jobPosition, workLocationCode);

        application.updateDraft(jobPosition, jobPosition.getPositionName());
        application.updateWorkLocation(workLocationCode, jobPosition.findWorkLocationName(workLocationCode));

        return application.getId();
    }

    @Transactional
    public Long submit(Long applicantId, Long applicationId) {
        JobApplication application = findApplication(applicantId, applicationId);
        validatePublishedAndAccepting(application.getJobPosting());
        validateDraftForSubmit(application);
        validateApplicationFormConfig(application.getJobPosting());
        validateSelectedJobPosition(application);
        validateWorkLocationChoice(application.getJobPosition(), application.getWorkLocationCode());
        applicationSubmitValidator.validate(application);
        application.submit(LocalDateTime.now(clock));

        return application.getId();
    }

    @Transactional
    public Long withdraw(Long applicantId, Long applicationId) {
        JobApplication application = findApplication(applicantId, applicationId);
        validatePublishedAndAccepting(application.getJobPosting());
        validateSubmittedForWithdraw(application);
        application.withdraw(LocalDateTime.now(clock));

        return application.getId();
    }

    public PageResponse<AdminApplicationSummaryResponse> getApplicationsForAdmin(
            Long jobPostingId,
            AdminApplicationSearchRequest request,
            int page,
            int size
    ) {
        validatePageRequest(page, size);
        if (jobPostingId != null) {
            validateJobPostingExists(jobPostingId);
        }
        return searchForAdmin(buildSearchCondition(jobPostingId, request), page, size);
    }

    public AdminApplicationDetailResponse getApplicationForAdmin(Long applicationId) {
        JobApplication application = jobApplicationRepository.findAdminDetailById(applicationId)
                .orElseThrow(() -> new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. id=" + applicationId));
        return AdminApplicationDetailResponse.from(application);
    }

    public PageResponse<AdminApplicationSummaryResponse> getApplicationsByJobPostingForAdmin(
            Long jobPostingId,
            AdminApplicationSearchRequest request,
            int page,
            int size
    ) {
        validatePageRequest(page, size);
        validateJobPostingExists(jobPostingId);
        return searchForAdmin(buildSearchCondition(jobPostingId, request), page, size);
    }

    private PageResponse<AdminApplicationSummaryResponse> searchForAdmin(
            AdminApplicationSearchCondition condition,
            int page,
            int size
    ) {
        Page<JobApplication> applications = jobApplicationRepository.searchForAdmin(
                condition.jobPostingId(),
                condition.jobPositionId(),
                condition.status(),
                condition.applicationType(),
                condition.workLocation(),
                condition.name(),
                condition.phoneNumber(),
                condition.birthDateFrom(),
                condition.birthDateTo(),
                condition.finalEducationRank(),
                condition.schoolName(),
                condition.graduationStatus(),
                condition.finalSchoolConditionName(),
                condition.certificateName(),
                condition.languageName(),
                condition.languageLevel(),
                condition.stageType(),
                condition.stageResultStatus(),
                createPageRequest(page, size)
        );
        Map<Long, AdminApplicationSummaryResponse.Enrichment> enrichments =
                loadAdminSummaryEnrichments(applications.getContent());

        return PageResponse.from(applications.map(application -> AdminApplicationSummaryResponse.from(
                application,
                enrichments.getOrDefault(application.getId(), AdminApplicationSummaryResponse.Enrichment.empty())
        )));
    }

    /**
     * 목록 파생 필드(생년월일/나이, 최종학력·최종학교, 최신 전형 결과, 경력기술서 다운로드 링크)를
     * 페이지 지원서 id 들로 배치 조회해 조합한다(페이지 최대 100건 — N+1 없음).
     */
    private Map<Long, AdminApplicationSummaryResponse.Enrichment> loadAdminSummaryEnrichments(
            List<JobApplication> applications
    ) {
        List<Long> applicationIds = applications.stream()
                .map(JobApplication::getId)
                .toList();
        if (applicationIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ApplicationBasicInfo> basicInfos = applicationBasicInfoRepository
                .findByJobApplicationIdIn(applicationIds).stream()
                .collect(Collectors.toMap(info -> info.getJobApplication().getId(), info -> info));

        // 최종학력 행 = 최고 EducationLevel(선언 순서 = 서열), 동률이면 id 가 큰 행 — 검색 필터의 최종 판정과 동일 기준.
        // 같은 규칙을 AdminStageResultEnricher(전형결과 그리드)도 쓴다 — 한쪽만 바꾸면 두 화면의 값이 어긋난다.
        Comparator<ApplicationEducation> finalEducationComparator = Comparator
                .comparingInt((ApplicationEducation education) -> education.getEducationLevel().ordinal())
                .thenComparing(ApplicationEducation::getId);
        Map<Long, ApplicationEducation> finalEducations = applicationEducationRepository
                .findByJobApplicationIdIn(applicationIds).stream()
                .collect(Collectors.toMap(
                        education -> education.getJobApplication().getId(),
                        education -> education,
                        (left, right) -> finalEducationComparator.compare(left, right) >= 0 ? left : right
                ));

        // 최신 전형 결과 = stageOrder(동률이면 stage id) 최대 — 발표 여부 무관(관리자 화면).
        Comparator<StageResult> latestResultComparator = Comparator
                .comparing((StageResult result) -> result.getStage().getStageOrder())
                .thenComparing(result -> result.getStage().getId());
        Map<Long, StageResult> latestResults = stageResultRepository
                .findWithStageByJobApplicationIdIn(applicationIds).stream()
                .collect(Collectors.toMap(
                        result -> result.getJobApplication().getId(),
                        result -> result,
                        (left, right) -> latestResultComparator.compare(left, right) >= 0 ? left : right
                ));

        // 경력기술서: 다운로드 가능한(STORED·미삭제) 첨부 중 최신(id 최대) 1건.
        Map<Long, Long> careerAttachmentIds = applicationAttachmentRepository
                .findByJobApplicationIdInAndAttachmentTypeAndPhysicalFileStatusAndDeletedAtIsNull(
                        applicationIds, AttachmentType.CAREER_DESCRIPTION, PhysicalFileStatus.STORED
                ).stream()
                .collect(Collectors.toMap(
                        attachment -> attachment.getJobApplication().getId(),
                        ApplicationAttachment::getId,
                        Long::max
                ));

        LocalDate today = LocalDate.now(clock);
        return applicationIds.stream().collect(Collectors.toMap(applicationId -> applicationId, applicationId -> {
            ApplicationBasicInfo basicInfo = basicInfos.get(applicationId);
            LocalDate birthDate = basicInfo == null ? null : basicInfo.getBirthDate();
            ApplicationEducation finalEducation = finalEducations.get(applicationId);
            StageResult latestResult = latestResults.get(applicationId);
            Long careerAttachmentId = careerAttachmentIds.get(applicationId);
            return new AdminApplicationSummaryResponse.Enrichment(
                    birthDate,
                    birthDate == null ? null : Period.between(birthDate, today).getYears(),
                    finalEducation == null ? null : finalEducation.getEducationLevel(),
                    finalEducation == null ? null : finalEducation.getSchoolName(),
                    latestResult == null ? null : latestResult.getStage().getStageType(),
                    latestResult == null ? null : latestResult.getResultStatus(),
                    careerAttachmentId == null ? null : "/admin/applications/%d/attachments/%d/download"
                            .formatted(applicationId, careerAttachmentId)
            );
        }));
    }

    private AdminApplicationSearchCondition buildSearchCondition(Long jobPostingId, AdminApplicationSearchRequest request) {
        if (request.birthDateFrom() != null && request.birthDateTo() != null
                && request.birthDateFrom().isAfter(request.birthDateTo())) {
            throw new InvalidJobApplicationException("생년월일 검색 범위가 올바르지 않습니다.");
        }
        return new AdminApplicationSearchCondition(
                jobPostingId,
                request.jobPositionId(),
                parseStatus(request.status()),
                parseSearchEnum(JobPositionApplicationType.class, request.applicationType(), "지원구분"),
                normalizeSearchText(request.workLocation()),
                normalizeSearchText(request.name()),
                normalizePhoneNumber(request.phoneNumber()),
                request.birthDateFrom(),
                request.birthDateTo(),
                parseSearchEnum(EducationLevel.class, request.finalEducationLevel(), "최종학력"),
                normalizeSearchText(request.schoolName()),
                parseSearchEnum(GraduationStatus.class, request.graduationStatus(), "졸업여부"),
                parseSearchEnum(FinalSchoolCondition.class, request.finalSchoolCondition(), "최종학교조건"),
                normalizeSearchText(request.certificateName()),
                normalizeSearchText(request.languageName()),
                normalizeSearchText(request.languageLevel()),
                parseSearchEnum(StageType.class, request.stageType(), "전형단계"),
                parseSearchEnum(StageResultStatus.class, request.stageResultStatus(), "전형결과")
        );
    }

    private <E extends Enum<E>> E parseSearchEnum(Class<E> type, String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidJobApplicationException(label + " 값이 올바르지 않습니다. value=" + value);
        }
    }

    /**
     * 휴대폰 검색어에서 숫자만 남긴다. 저장값의 하이픈/공백 유무가 제각각이라 양쪽을 같은 방식으로
     * 정규화해야 {@code 010-1234-5678} 저장분을 {@code 01012345678} 로도 찾을 수 있다.
     */
    private String normalizePhoneNumber(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    private String normalizeSearchText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private JobApplication findApplication(Long applicantId, Long applicationId) {
        return jobApplicationRepository.findByIdAndApplicantId(applicationId, applicantId)
                .orElseThrow(() -> new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. id=" + applicationId));
    }

    private Applicant findApplicant(Long applicantId) {
        return applicantRepository.findById(applicantId)
                .orElseThrow(() -> new InvalidJobApplicationException("지원자를 찾을 수 없습니다. applicantId=" + applicantId));
    }

    private JobPosting findJobPosting(Long jobPostingId) {
        return jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + jobPostingId));
    }

    private JobPosition findJobPosition(Long jobPositionId, Long jobPostingId) {
        return jobPositionRepository.findByIdAndJobPostingId(jobPositionId, jobPostingId)
                .orElseThrow(() -> new InvalidJobApplicationException("모집분야를 찾을 수 없습니다. jobPositionId=" + jobPositionId));
    }

    private void validatePublishedAndAccepting(JobPosting jobPosting) {
        if (jobPosting.getStatus() != JobPostingStatus.PUBLISHED) {
            throw new InvalidJobApplicationException("게시 중인 채용공고에만 지원서를 처리할 수 있습니다.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(jobPosting.getReceptionStartDateTime()) || now.isAfter(jobPosting.getReceptionEndDateTime())) {
            throw new InvalidJobApplicationException("접수기간 내에만 지원서를 처리할 수 있습니다.");
        }
    }

    private void validatePublishedAcceptingAndVisibleForCreate(JobPosting jobPosting) {
        validatePublishedAndAccepting(jobPosting);

        LocalDateTime now = LocalDateTime.now(clock);
        if (!jobPosting.isVisible() || !isWithinDisplayPeriod(jobPosting, now)) {
            throw new InvalidJobApplicationException("현재 지원서를 생성할 수 없는 채용공고입니다.");
        }
    }

    private boolean isAccepting(JobPosting jobPosting) {
        if (jobPosting.getStatus() != JobPostingStatus.PUBLISHED) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        return ReceptionStatus.from(
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                now
        ) == ReceptionStatus.ACCEPTING;
    }

    private boolean isWithinDisplayPeriod(JobPosting jobPosting, LocalDateTime now) {
        return (jobPosting.getDisplayStartDateTime() == null || !now.isBefore(jobPosting.getDisplayStartDateTime()))
                && (jobPosting.getDisplayEndDateTime() == null || !now.isAfter(jobPosting.getDisplayEndDateTime()));
    }

    private void validateWritableForUpdate(JobApplication application) {
        if (application.getStatus() == JobApplicationStatus.WITHDRAWN) {
            throw new InvalidJobApplicationException("철회된 지원서는 수정할 수 없습니다.");
        }
    }

    private void validateDraftForSubmit(JobApplication application) {
        if (application.getStatus() != JobApplicationStatus.DRAFT) {
            throw new InvalidJobApplicationException("임시저장 상태의 지원서만 제출할 수 있습니다.");
        }
    }

    private void validateSubmittedForWithdraw(JobApplication application) {
        if (application.getStatus() != JobApplicationStatus.SUBMITTED) {
            throw new InvalidJobApplicationException("제출된 지원서만 철회할 수 있습니다.");
        }
    }

    private void validateApplicationFormConfig(JobPosting jobPosting) {
        if (jobPosting.getApplicationFormConfig() == null) {
            throw new InvalidJobApplicationException("지원서 항목 설정이 없는 채용공고에는 지원할 수 없습니다.");
        }
    }

    private void validateSelectedJobPosition(JobApplication application) {
        Long jobPositionId = application.getJobPosition().getId();
        Long jobPostingId = application.getJobPosting().getId();
        if (jobPositionRepository.findByIdAndJobPostingId(jobPositionId, jobPostingId).isEmpty()) {
            throw new InvalidJobApplicationException("지원서의 모집분야가 채용공고에 속하지 않습니다.");
        }
    }

    private String normalizeWorkLocationCode(String workLocationCode) {
        if (workLocationCode == null || workLocationCode.isBlank()) {
            return null;
        }
        return workLocationCode.trim();
    }

    /**
     * 후보 근무지 개수가 곧 분기다 — 후보가 있으면 그 중 하나를 반드시 골라야 하고, 후보가 없으면 아예 보낼 수 없다.
     * 공고 수정으로 후보 목록이 바뀌면 임시저장분의 선택이 무효가 될 수 있어 제출 시에도 같은 규칙으로 재검증한다.
     */
    private void validateWorkLocationChoice(JobPosition jobPosition, String workLocationCode) {
        boolean hasCandidates = !jobPosition.getWorkLocations().isEmpty();
        if (!hasCandidates) {
            if (workLocationCode != null) {
                throw new InvalidJobApplicationException("해당 모집분야는 근무지를 선택할 수 없습니다.");
            }
            return;
        }
        if (workLocationCode == null) {
            throw new InvalidJobApplicationException("근무지는 필수 선택입니다.");
        }
        if (!jobPosition.hasWorkLocation(workLocationCode)) {
            throw new InvalidJobApplicationException("모집분야의 근무지가 아닙니다. workLocationCode=" + workLocationCode);
        }
    }

    private void validateNotDuplicated(Long applicantId, Long jobPostingId) {
        if (jobApplicationRepository.existsByApplicantIdAndJobPostingId(applicantId, jobPostingId)) {
            throw new InvalidJobApplicationException("이미 해당 채용공고에 지원서가 존재합니다.");
        }
    }

    private JobApplicationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return JobApplicationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidJobApplicationException("지원서 상태 값이 올바르지 않습니다. status=" + status);
        }
    }

    private PageRequest createPageRequest(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new InvalidJobApplicationException("page는 0 이상이어야 합니다.");
        }
        if (size <= 0) {
            throw new InvalidJobApplicationException("size는 1 이상이어야 합니다.");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new InvalidJobApplicationException("size는 100 이하이어야 합니다.");
        }
    }

    private void validateJobPostingExists(Long jobPostingId) {
        if (!jobPostingRepository.existsById(jobPostingId)) {
            throw new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + jobPostingId);
        }
    }

    private String resolveApplicantNameSnapshot(Applicant applicant) {
        if (applicant.getUserName() != null && !applicant.getUserName().isBlank()) {
            return applicant.getUserName();
        }
        if (applicant.getName() != null && !applicant.getName().isBlank()) {
            return applicant.getName();
        }
        throw new InvalidJobApplicationException("지원자 이름을 확인할 수 없습니다.");
    }

    private Map<Long, ApplicationResultSummary> loadApplicationResultSummaries(List<JobApplication> applications) {
        List<Long> applicationIds = applications.stream()
                .map(JobApplication::getId)
                .toList();
        if (applicationIds.isEmpty()) {
            return Map.of();
        }

        return stageResultRepository.findVisibleByJobApplicationIdsForApplicantSummary(applicationIds).stream()
                .collect(Collectors.groupingBy(
                        result -> result.getJobApplication().getId(),
                        Collectors.collectingAndThen(Collectors.toList(), ApplicationResultSummary::from)
                ));
    }

    private record ApplicationResultSummary(
            long announcedResultCount,
            String latestAnnouncedStageName,
            StageResultStatus latestResultStatus
    ) {
        private static final Comparator<StageResult> LATEST_RESULT_COMPARATOR =
                Comparator
                        .comparing((StageResult result) -> result.getStage().getStageOrder())
                        .thenComparing(result -> result.getStage().getId());

        static ApplicationResultSummary empty() {
            return new ApplicationResultSummary(0, null, null);
        }

        static ApplicationResultSummary from(List<StageResult> results) {
            return results.stream()
                    .max(LATEST_RESULT_COMPARATOR)
                    .map(result -> new ApplicationResultSummary(
                            results.size(),
                            result.getStage().getStageName(),
                            result.getResultStatus()
                    ))
                    .orElseGet(ApplicationResultSummary::empty);
        }
    }
}
