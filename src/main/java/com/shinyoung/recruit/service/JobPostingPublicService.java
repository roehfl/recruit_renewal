package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosting;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingPublicService {

    private final JobPostingRepository jobPostingRepository;
    private final Clock clock;

    public PageResponse<JobPostingPublicListResponse> getJobPostings(int page, int size) {
        validatePageRequest(page, size);
        LocalDateTime now = LocalDateTime.now(clock);
        Page<JobPostingPublicListProjection> result = jobPostingRepository.findAllByStatusOrderByCreatedAtDesc(
                JobPostingStatus.PUBLISHED,
                PageRequest.of(page, size)
        );
        return PageResponse.from(result.map(jobPosting -> JobPostingPublicListResponse.from(jobPosting, now)));
    }

    public JobPostingPublicDetailResponse getJobPosting(Long id) {
        LocalDateTime now = LocalDateTime.now(clock);
        JobPosting jobPosting = jobPostingRepository.findByIdAndStatus(id, JobPostingStatus.PUBLISHED)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + id));
        return JobPostingPublicDetailResponse.from(jobPosting, now);
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
