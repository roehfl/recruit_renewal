package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicationFormLayoutItemView;
import com.shinyoung.recruit.domain.repository.ApplicationFormPageRepository;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementPolicyCount;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionPolicyCount;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.AdminApplicationFormSummarySearchRequest;
import com.shinyoung.recruit.dto.response.AdminApplicationFormSummaryResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.ApplicationFormConfigState;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.ReceptionStatus;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 지원서 설정 현황판.
 * 설정 상태(configState)는 지원서 항목 설정·질문·첨부 요구사항·저장된 레이아웃을 합쳐야 나오므로
 * 공고별 개별 조회 대신 집계 쿼리로 한 번에 읽어 메모리에서 조합한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminApplicationFormSummaryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingQuestionRepository jobPostingQuestionRepository;
    private final JobPostingAttachmentRequirementRepository attachmentRequirementRepository;
    private final ApplicationFormPageRepository applicationFormPageRepository;
    private final Clock clock;

    public PageResponse<AdminApplicationFormSummaryResponse> getSummaries(
            AdminApplicationFormSummarySearchRequest request,
            int page,
            int size
    ) {
        validatePageRequest(page, size);

        /*
         * 상태·제목은 SQL 로 좁히고, 파생값(receptionStatus·configState·editable)은 계산 후 걸러야 한다.
         * 공고 테이블은 규모가 작아 필터링된 전체를 읽고 메모리에서 페이징한다.
         */
        List<JobPosting> jobPostings = jobPostingRepository.findAllForApplicationFormSummary(
                request.status(),
                request.normalizedKeyword()
        );
        if (jobPostings.isEmpty()) {
            return emptyPage(page, size);
        }

        List<Long> jobPostingIds = jobPostings.stream().map(JobPosting::getId).toList();
        Map<Long, JobPostingQuestionPolicyCount> questionCounts = toMap(
                jobPostingQuestionRepository.countActiveQuestionPolicyByJobPostingIds(jobPostingIds),
                JobPostingQuestionPolicyCount::jobPostingId
        );
        Map<Long, JobPostingAttachmentRequirementPolicyCount> attachmentCounts = toMap(
                attachmentRequirementRepository.countPolicyByJobPostingIds(jobPostingIds),
                JobPostingAttachmentRequirementPolicyCount::jobPostingId
        );
        Map<Long, LayoutSummary> layouts = toLayoutSummaries(
                applicationFormPageRepository.findLayoutItemsByJobPostingIds(jobPostingIds)
        );

        LocalDateTime now = LocalDateTime.now(clock);
        List<AdminApplicationFormSummaryResponse> summaries = jobPostings.stream()
                .map(jobPosting -> toSummary(
                        jobPosting,
                        now,
                        questionCounts.getOrDefault(jobPosting.getId(), JobPostingQuestionPolicyCount.empty(jobPosting.getId())),
                        attachmentCounts.getOrDefault(jobPosting.getId(), JobPostingAttachmentRequirementPolicyCount.empty(jobPosting.getId())),
                        layouts.getOrDefault(jobPosting.getId(), LayoutSummary.empty())
                ))
                .filter(summary -> matches(summary, request))
                .sorted(summaryComparator())
                .toList();

        return toPageResponse(summaries, page, size);
    }

    private AdminApplicationFormSummaryResponse toSummary(
            JobPosting jobPosting,
            LocalDateTime now,
            JobPostingQuestionPolicyCount questionCount,
            JobPostingAttachmentRequirementPolicyCount attachmentCount,
            LayoutSummary layout
    ) {
        ApplicationFormConfig config = jobPosting.getApplicationFormConfig();
        Set<ApplicationSectionType> enabledSections = ApplicationFormLayoutSectionPolicy.enabledSections(
                config,
                attachmentCount.totalRequirementCount() > 0,
                questionCount.activeQuestionCount() > 0
        );
        Set<ApplicationSectionType> requiredSections = ApplicationFormLayoutSectionPolicy.requiredSections(
                config,
                attachmentCount.requiredRequirementCount() > 0,
                questionCount.requiredQuestionCount() > 0
        );

        ReceptionStatus receptionStatus = ReceptionStatus.from(
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                now
        );

        return new AdminApplicationFormSummaryResponse(
                jobPosting.getId(),
                jobPosting.getTitle(),
                jobPosting.getPostingType(),
                jobPosting.getStatus(),
                receptionStatus,
                jobPosting.getReceptionStartDateTime(),
                jobPosting.getReceptionEndDateTime(),
                new AdminApplicationFormSummaryResponse.SectionSummary(enabledSections.size(), requiredSections.size()),
                toIntCount(questionCount.activeQuestionCount()),
                toIntCount(questionCount.requiredQuestionCount()),
                layout.stored(),
                layout.pageCount(),
                resolveConfigState(config, enabledSections, layout),
                ApplicationFormEditWindow.isEditable(jobPosting, now),
                jobPosting.getUpdatedAt()
        );
    }

    private ApplicationFormConfigState resolveConfigState(
            ApplicationFormConfig config,
            Set<ApplicationSectionType> enabledSections,
            LayoutSummary layout
    ) {
        if (config == null) {
            return ApplicationFormConfigState.MISSING;
        }
        if (!layout.stored()) {
            return ApplicationFormConfigState.DEFAULT;
        }
        // ApplicationFormLayoutValidator 가 강제하는 불변식. 어긋나면 지원자 form-page 조회가 막힌다.
        if (!layout.placedSections().equals(enabledSections)) {
            return ApplicationFormConfigState.RELAYOUT_REQUIRED;
        }
        return ApplicationFormConfigState.OK;
    }

    private boolean matches(
            AdminApplicationFormSummaryResponse summary,
            AdminApplicationFormSummarySearchRequest request
    ) {
        if (request.receptionStatus() != null && summary.receptionStatus() != request.receptionStatus()) {
            return false;
        }
        if (request.configState() != null && summary.configState() != request.configState()) {
            return false;
        }
        return !request.isEditableOnly() || summary.editable();
    }

    /** 손봐야 하는 공고를 먼저 보여준다: 설정 상태 심각도 → 접수 시작 임박순. */
    private Comparator<AdminApplicationFormSummaryResponse> summaryComparator() {
        return Comparator
                .comparingInt((AdminApplicationFormSummaryResponse summary) -> summary.configState().ordinal())
                .thenComparing(AdminApplicationFormSummaryResponse::receptionStartDateTime)
                .thenComparing(AdminApplicationFormSummaryResponse::jobPostingId);
    }

    private Map<Long, LayoutSummary> toLayoutSummaries(List<ApplicationFormLayoutItemView> items) {
        Map<Long, Set<Integer>> pageNos = new HashMap<>();
        Map<Long, Set<ApplicationSectionType>> sections = new HashMap<>();
        for (ApplicationFormLayoutItemView item : items) {
            pageNos.computeIfAbsent(item.jobPostingId(), key -> new HashSet<>()).add(item.pageNo());
            sections.computeIfAbsent(item.jobPostingId(), key -> EnumSet.noneOf(ApplicationSectionType.class))
                    .add(item.sectionType());
        }
        Map<Long, LayoutSummary> summaries = new HashMap<>();
        pageNos.forEach((jobPostingId, pages) -> summaries.put(
                jobPostingId,
                new LayoutSummary(true, pages.size(), Set.copyOf(sections.getOrDefault(jobPostingId, Set.of())))
        ));
        return summaries;
    }

    private <T> Map<Long, T> toMap(List<T> values, Function<T, Long> keyExtractor) {
        return values.stream().collect(Collectors.toMap(keyExtractor, Function.identity(), (left, right) -> left));
    }

    private PageResponse<AdminApplicationFormSummaryResponse> toPageResponse(
            List<AdminApplicationFormSummaryResponse> summaries,
            int page,
            int size
    ) {
        int from = Math.min(page * size, summaries.size());
        int to = Math.min(from + size, summaries.size());
        return PageResponse.from(new PageImpl<>(
                summaries.subList(from, to),
                PageRequest.of(page, size),
                summaries.size()
        ));
    }

    private PageResponse<AdminApplicationFormSummaryResponse> emptyPage(int page, int size) {
        return PageResponse.from(new PageImpl<>(List.of(), PageRequest.of(page, size), 0));
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new InvalidJobPostingException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidJobPostingException("페이지 크기는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
    }

    private int toIntCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private record LayoutSummary(boolean stored, int pageCount, Set<ApplicationSectionType> placedSections) {

        static LayoutSummary empty() {
            return new LayoutSummary(false, 0, Set.of());
        }
    }
}
