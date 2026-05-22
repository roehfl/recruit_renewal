package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.repository.JobPositionRepository;
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
        return PageResponse.from(result.map(jobPosting -> JobPostingPublicListResponse.from(
                jobPosting,
                positionsByPostingId.getOrDefault(jobPosting.getId(), List.of()),
                now
        )));
    }

    public JobPostingPublicDetailResponse getJobPosting(Long id) {
        LocalDateTime now = LocalDateTime.now(clock);
        JobPosting jobPosting = jobPostingRepository.findPublicDetailById(id, JobPostingStatus.PUBLISHED, now)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + id));
        return JobPostingPublicDetailResponse.from(jobPosting, now);
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

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new InvalidJobPostingException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new InvalidJobPostingException("페이지 크기는 1 이상 100 이하이어야 합니다.");
        }
    }
}
