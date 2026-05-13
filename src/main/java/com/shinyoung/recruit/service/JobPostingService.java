package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingUpdateRequest;
import com.shinyoung.recruit.dto.response.JobPostingDetailResponse;
import com.shinyoung.recruit.dto.response.JobPostingListResponse;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;

    public List<JobPostingListResponse> getJobPostings() {
        return jobPostingRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(JobPostingListResponse::from)
                .toList();
    }

    public JobPostingDetailResponse getJobPosting(Long id) {
        JobPosting jobPosting = findJobPosting(id);
        return JobPostingDetailResponse.from(jobPosting);
    }

    @Transactional
    public Long create(JobPostingCreateRequest request) {
        validateRequest(request.title(), request.receptionStartDateTime(), request.receptionEndDateTime(), request.jobPositions());

        JobPosting jobPosting = JobPosting.create(
                request.title(),
                request.contentHtml(),
                request.receptionStartDateTime(),
                request.receptionEndDateTime()
        );

        jobPosting.replaceJobPositions(toJobPositions(request.jobPositions()));
        jobPosting.updateApplicationFormConfig(toApplicationFormConfig(request.applicationFormConfig()));

        JobPosting saved = jobPostingRepository.save(jobPosting);
        return saved.getId();
    }

    @Transactional
    public Long update(Long id, JobPostingUpdateRequest request) {
        validateRequest(request.title(), request.receptionStartDateTime(), request.receptionEndDateTime(), request.jobPositions());

        JobPosting jobPosting = findJobPosting(id);
        if (jobPosting.getStatus() == JobPostingStatus.CLOSED) {
            throw new InvalidJobPostingException("마감된 공고는 수정할 수 없습니다.");
        }

        jobPosting.updateBasicInfo(
                request.title(),
                request.contentHtml(),
                request.receptionStartDateTime(),
                request.receptionEndDateTime()
        );
        jobPosting.replaceJobPositions(toJobPositions(request.jobPositions()));
        jobPosting.updateApplicationFormConfig(toApplicationFormConfig(request.applicationFormConfig()));

        return jobPosting.getId();
    }

    @Transactional
    public Long publish(Long id) {
        JobPosting jobPosting = findJobPosting(id);

        if (jobPosting.getStatus() == JobPostingStatus.CLOSED) {
            throw new InvalidJobPostingException("마감된 공고는 다시 게시할 수 없습니다.");
        }
        if (jobPosting.getStatus() == JobPostingStatus.PUBLISHED) {
            throw new InvalidJobPostingException("이미 게시된 공고입니다.");
        }
        validateReceptionPeriod(jobPosting.getReceptionStartDateTime(), jobPosting.getReceptionEndDateTime());
        validateJobPositions(jobPosting.getJobPositions());

        jobPosting.publish(LocalDateTime.now());
        return jobPosting.getId();
    }

    @Transactional
    public Long close(Long id) {
        JobPosting jobPosting = findJobPosting(id);

        if (jobPosting.getStatus() != JobPostingStatus.PUBLISHED) {
            throw new InvalidJobPostingException("게시 상태의 공고만 마감할 수 있습니다.");
        }

        jobPosting.close(LocalDateTime.now());
        return jobPosting.getId();
    }

    private JobPosting findJobPosting(Long id) {
        return jobPostingRepository.findById(id)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + id));
    }

    private void validateRequest(String title, LocalDateTime start, LocalDateTime end, List<JobPositionRequest> jobPositions) {
        if (title == null || title.isBlank()) {
            throw new InvalidJobPostingException("공고 제목은 필수입니다.");
        }
        validateReceptionPeriod(start, end);
        validateJobPositions(jobPositions);
    }

    private void validateReceptionPeriod(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new InvalidJobPostingException("접수 종료일시는 시작일시 이후여야 합니다.");
        }
    }

    private void validateJobPositions(List<?> jobPositions) {
        if (jobPositions == null || jobPositions.isEmpty()) {
            throw new InvalidJobPostingException("모집분야는 최소 1개 이상이어야 합니다.");
        }
    }

    private List<JobPosition> toJobPositions(List<JobPositionRequest> requests) {
        return requests.stream()
                .map(it -> JobPosition.create(it.positionName(), it.headcount(), it.sortOrder()))
                .toList();
    }

    private ApplicationFormConfig toApplicationFormConfig(ApplicationFormConfigRequest request) {
        return ApplicationFormConfig.create(
                request.useEducation(),
                request.useCareer(),
                request.useCertificate(),
                request.useLanguage(),
                request.useMilitary(),
                request.useAward(),
                request.useGapPeriod()
        );
    }
}
