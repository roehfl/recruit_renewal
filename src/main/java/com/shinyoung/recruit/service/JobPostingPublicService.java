package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.JobPostingAttachmentRequirement;
import com.shinyoung.recruit.domain.repository.JobPositionRepository;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementPolicyCount;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionPolicyCount;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.domain.repository.JobPostingPublicListProjection;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.response.JobPostingPublicDetailResponse;
import com.shinyoung.recruit.dto.response.JobPostingPublicListResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingPublicService {

    private final JobPostingRepository jobPostingRepository;
    private final JobPositionRepository jobPositionRepository;
    private final JobPostingQuestionRepository jobPostingQuestionRepository;
    private final JobPostingAttachmentRequirementRepository attachmentRequirementRepository;
    private final Clock clock;

    public PageResponse<JobPostingPublicListResponse> getJobPostings(int page, int size) {
        validatePageRequest(page, size);
        LocalDateTime now = LocalDateTime.now(clock);
        Page<JobPostingPublicListProjection> result = jobPostingRepository.findPublicList(
                JobPostingStatus.PUBLISHED,
                now,
                PageRequest.of(page, size)
        );
        Map<Long, List<JobPosition>> positionsByPostingId = getPositionsByPostingId(result.getContent());
        Map<Long, JobPostingQuestionPolicyCount> questionPolicyCountByPostingId =
                getQuestionPolicyCountByPostingId(result.getContent());
        Map<Long, JobPostingAttachmentRequirementPolicyCount> attachmentPolicyCountByPostingId =
                getAttachmentPolicyCountByPostingId(result.getContent());
        return PageResponse.from(result.map(jobPosting -> JobPostingPublicListResponse.from(
                jobPosting,
                positionsByPostingId.getOrDefault(jobPosting.getId(), List.of()),
                now,
                questionPolicyCountByPostingId.getOrDefault(
                        jobPosting.getId(),
                        JobPostingQuestionPolicyCount.empty(jobPosting.getId())
                ),
                attachmentPolicyCountByPostingId.getOrDefault(
                        jobPosting.getId(),
                        JobPostingAttachmentRequirementPolicyCount.empty(jobPosting.getId())
                )
        )));
    }

    public JobPostingPublicDetailResponse getJobPosting(Long id) {
        LocalDateTime now = LocalDateTime.now(clock);
        JobPosting jobPosting = jobPostingRepository.findPublicDetailById(id, JobPostingStatus.PUBLISHED, now)
                .orElseThrow(() -> new JobPostingNotFoundException("Job posting not found. id=" + id));
        List<JobPostingAttachmentRequirement> attachmentRequirements =
                attachmentRequirementRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(id);
        return JobPostingPublicDetailResponse.from(
                jobPosting,
                now,
                getQuestionPolicyCount(id),
                getAttachmentPolicyCount(id),
                attachmentRequirements
        );
    }

    private Map<Long, List<JobPosition>> getPositionsByPostingId(List<JobPostingPublicListProjection> jobPostings) {
        List<Long> jobPostingIds = jobPostings.stream()
                .map(JobPostingPublicListProjection::getId)
                .toList();

        if (jobPostingIds.isEmpty()) {
            return Map.of();
        }

        return jobPositionRepository.findByJobPostingIdInOrderByJobPostingIdAscSortOrderAsc(jobPostingIds).stream()
                .collect(Collectors.groupingBy(position -> position.getJobPosting().getId()));
    }

    private Map<Long, JobPostingQuestionPolicyCount> getQuestionPolicyCountByPostingId(
            List<JobPostingPublicListProjection> jobPostings
    ) {
        List<Long> jobPostingIds = jobPostings.stream()
                .map(JobPostingPublicListProjection::getId)
                .toList();

        if (jobPostingIds.isEmpty()) {
            return Map.of();
        }

        return jobPostingQuestionRepository.countActiveQuestionPolicyByJobPostingIds(jobPostingIds).stream()
                .collect(Collectors.toMap(
                        JobPostingQuestionPolicyCount::jobPostingId,
                        questionPolicyCount -> questionPolicyCount
                ));
    }

    private Map<Long, JobPostingAttachmentRequirementPolicyCount> getAttachmentPolicyCountByPostingId(
            List<JobPostingPublicListProjection> jobPostings
    ) {
        List<Long> jobPostingIds = jobPostings.stream()
                .map(JobPostingPublicListProjection::getId)
                .toList();

        if (jobPostingIds.isEmpty()) {
            return Map.of();
        }

        return attachmentRequirementRepository.countPolicyByJobPostingIds(jobPostingIds).stream()
                .collect(Collectors.toMap(
                        JobPostingAttachmentRequirementPolicyCount::jobPostingId,
                        attachmentPolicyCount -> attachmentPolicyCount
                ));
    }

    private JobPostingQuestionPolicyCount getQuestionPolicyCount(Long jobPostingId) {
        return jobPostingQuestionRepository.countActiveQuestionPolicyByJobPostingIds(List.of(jobPostingId)).stream()
                .findFirst()
                .orElseGet(() -> JobPostingQuestionPolicyCount.empty(jobPostingId));
    }

    private JobPostingAttachmentRequirementPolicyCount getAttachmentPolicyCount(Long jobPostingId) {
        return attachmentRequirementRepository.countPolicyByJobPostingIds(List.of(jobPostingId)).stream()
                .findFirst()
                .orElseGet(() -> JobPostingAttachmentRequirementPolicyCount.empty(jobPostingId));
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new InvalidJobPostingException("Page number must be 0 or greater.");
        }
        if (size < 1 || size > 100) {
            throw new InvalidJobPostingException("Page size must be between 1 and 100.");
        }
    }
}
