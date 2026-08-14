package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.JobPostingAttachmentRequirement;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.AttachmentRequirementReplaceRequest;
import com.shinyoung.recruit.dto.request.AttachmentRequirementRequest;
import com.shinyoung.recruit.dto.response.JobPostingAttachmentRequirementResponse;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingAttachmentRequirementService {

    private static final int DISPLAY_NAME_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingAttachmentRequirementRepository requirementRepository;

    public List<JobPostingAttachmentRequirementResponse> getRequirements(Long jobPostingId) {
        ensurePostingExists(jobPostingId);
        return requirementRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(jobPostingId).stream()
                .map(JobPostingAttachmentRequirementResponse::from)
                .toList();
    }

    @Transactional
    public List<JobPostingAttachmentRequirementResponse> replaceRequirements(
            Long jobPostingId,
            AttachmentRequirementReplaceRequest request
    ) {
        JobPosting jobPosting = findPosting(jobPostingId);
        if (jobPosting.getStatus() != JobPostingStatus.DRAFT) {
            throw new InvalidJobPostingException("Attachment requirements can be changed only while the posting is DRAFT.");
        }

        List<AttachmentRequirementRequest> rows = request == null || request.requirements() == null
                ? List.of()
                : request.requirements();
        validateNoDuplicates(rows);

        requirementRepository.deleteByJobPostingId(jobPostingId);
        List<JobPostingAttachmentRequirement> saved = requirementRepository.saveAll(toRequirements(jobPosting, rows));
        return saved.stream()
                .sorted(requirementComparator())
                .map(JobPostingAttachmentRequirementResponse::from)
                .toList();
    }

    private void ensurePostingExists(Long jobPostingId) {
        if (!jobPostingRepository.existsById(jobPostingId)) {
            throw new JobPostingNotFoundException("Job posting not found. id=" + jobPostingId);
        }
    }

    private JobPosting findPosting(Long jobPostingId) {
        return jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("Job posting not found. id=" + jobPostingId));
    }

    private List<JobPostingAttachmentRequirement> toRequirements(
            JobPosting jobPosting,
            List<AttachmentRequirementRequest> requests
    ) {
        return java.util.stream.IntStream.range(0, requests.size())
                .mapToObj(index -> toRequirement(jobPosting, requests.get(index), index))
                .toList();
    }

    private JobPostingAttachmentRequirement toRequirement(
            JobPosting jobPosting,
            AttachmentRequirementRequest request,
            int index
    ) {
        if (request == null) {
            throw new InvalidJobPostingException("Attachment requirement row is required.");
        }
        if (request.attachmentType() == null) {
            throw new InvalidJobPostingException("Attachment type is required.");
        }
        if (request.sectionType() == null) {
            throw new InvalidJobPostingException("Attachment section type is required.");
        }

        boolean required = Boolean.TRUE.equals(request.required());
        int minCount = defaultMinCount(required, request.minCount());
        validateMinCount(required, minCount);
        String displayName = normalizeDisplayName(request.displayName());
        String description = normalizeDescription(request.description());
        int sortOrder = request.sortOrder() == null ? index : request.sortOrder();
        if (sortOrder < 0) {
            throw new InvalidJobPostingException("Attachment requirement sort order must be 0 or greater.");
        }

        return JobPostingAttachmentRequirement.create(
                jobPosting,
                request.attachmentType(),
                request.sectionType(),
                required,
                minCount,
                sortOrder,
                displayName,
                description
        );
    }

    private void validateNoDuplicates(List<AttachmentRequirementRequest> requests) {
        Set<String> keys = new HashSet<>();
        for (AttachmentRequirementRequest request : requests) {
            if (request == null || request.attachmentType() == null || request.sectionType() == null) {
                continue;
            }
            String key = request.attachmentType().name() + ":" + request.sectionType().name();
            if (!keys.add(key)) {
                throw new InvalidJobPostingException("Attachment requirement type and section must be unique.");
            }
        }
    }

    private int defaultMinCount(boolean required, Integer minCount) {
        if (minCount != null) {
            return minCount;
        }
        return required ? 1 : 0;
    }

    private void validateMinCount(boolean required, int minCount) {
        if (required && minCount < 1) {
            throw new InvalidJobPostingException("Required attachment min count must be 1 or greater.");
        }
        if (!required && minCount < 0) {
            throw new InvalidJobPostingException("Optional attachment min count must be 0 or greater.");
        }
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new InvalidJobPostingException("Attachment requirement display name is required.");
        }
        String normalized = displayName.trim();
        if (normalized.length() > DISPLAY_NAME_MAX_LENGTH) {
            throw new InvalidJobPostingException("Attachment requirement display name must be 100 characters or less.");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String normalized = description.trim();
        if (normalized.length() > DESCRIPTION_MAX_LENGTH) {
            throw new InvalidJobPostingException("Attachment requirement description must be 500 characters or less.");
        }
        return normalized;
    }

    private Comparator<JobPostingAttachmentRequirement> requirementComparator() {
        return Comparator.comparing(
                        JobPostingAttachmentRequirement::getSortOrder,
                        Comparator.nullsLast(Integer::compareTo)
                )
                .thenComparing(
                        JobPostingAttachmentRequirement::getId,
                        Comparator.nullsLast(Long::compareTo)
                );
    }
}
