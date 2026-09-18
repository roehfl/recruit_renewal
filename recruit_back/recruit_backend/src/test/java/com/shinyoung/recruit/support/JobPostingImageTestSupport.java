package com.shinyoung.recruit.support;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.JobPostingImage;
import com.shinyoung.recruit.domain.repository.JobPostingImageRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;

/**
 * 테스트 전용: 발행 조건(본문 이미지 ≥1장)을 충족시키기 위해 공고에 최소 이미지 메타 행을 리포지토리에 직접 시드한다.
 * 실파일은 저장하지 않으므로 이미지 바이너리 서빙을 검증하는 테스트에는 쓰지 않는다(그 경우 실제 업로드 API 사용).
 */
public final class JobPostingImageTestSupport {

    private JobPostingImageTestSupport() {
    }

    public static Long attachContentImage(
            JobPostingRepository jobPostingRepository,
            JobPostingImageRepository jobPostingImageRepository,
            Long jobPostingId
    ) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId).orElseThrow();
        int sortOrder = (int) jobPostingImageRepository.countByJobPostingId(jobPostingId);
        JobPostingImage image = JobPostingImage.create(
                jobPosting,
                "test-content.png",
                "test/job-posting-images/" + jobPostingId + "/test-content-" + sortOrder + ".png",
                "image/png",
                1L,
                sortOrder,
                "테스트 공고 본문 이미지"
        );
        return jobPostingImageRepository.save(image).getId();
    }
}
