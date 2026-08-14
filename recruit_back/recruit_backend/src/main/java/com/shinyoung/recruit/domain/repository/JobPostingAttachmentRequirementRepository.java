package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobPostingAttachmentRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JobPostingAttachmentRequirementRepository
        extends JpaRepository<JobPostingAttachmentRequirement, Long> {

    List<JobPostingAttachmentRequirement> findByJobPostingIdOrderBySortOrderAscIdAsc(Long jobPostingId);

    List<JobPostingAttachmentRequirement> findByJobPostingIdAndRequiredTrueOrderBySortOrderAscIdAsc(Long jobPostingId);

    boolean existsByJobPostingId(Long jobPostingId);

    boolean existsByJobPostingIdAndRequiredTrue(Long jobPostingId);

    List<JobPostingAttachmentRequirement> findByJobPostingIdIn(Collection<Long> jobPostingIds);

    void deleteByJobPostingId(Long jobPostingId);

    @Query("""
            select new com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementPolicyCount(
                requirement.jobPosting.id,
                count(requirement),
                coalesce(sum(case when requirement.required = true then 1 else 0 end), 0)
            )
            from JobPostingAttachmentRequirement requirement
            where requirement.jobPosting.id in :jobPostingIds
            group by requirement.jobPosting.id
            """)
    List<JobPostingAttachmentRequirementPolicyCount> countPolicyByJobPostingIds(
            @Param("jobPostingIds") Collection<Long> jobPostingIds
    );
}
