package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingUpdateRequest;
import com.shinyoung.recruit.dto.response.JobPostingDetailResponse;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class JobPostingServiceTest {

    @Autowired
    private JobPostingService jobPostingService;

    @Test
    void JobPosting_생성_성공() {
        Long id = jobPostingService.create(createRequest());
        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);

        assertThat(detail.status()).isEqualTo(JobPostingStatus.DRAFT);
        assertThat(detail.jobPositions()).hasSize(1);
    }

    @Test
    void 접수종료일시가_시작보다_빠르면_생성실패() {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "2026 상반기 채용",
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 2, 9, 0),
                LocalDateTime.of(2026, 6, 1, 18, 0),
                List.of(new JobPositionRequest("백엔드", 2, 1)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );

        assertThatThrownBy(() -> jobPostingService.create(request)).isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void 모집분야_없이_생성불가() {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "2026 상반기 채용",
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                List.of(),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );

        assertThatThrownBy(() -> jobPostingService.create(request)).isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void DRAFT에서_PUBLISHED_전환_성공() {
        Long id = jobPostingService.create(createRequest());

        jobPostingService.publish(id);

        assertThat(jobPostingService.getJobPosting(id).status()).isEqualTo(JobPostingStatus.PUBLISHED);
    }

    @Test
    void CLOSED_상태에서_PUBLISHED_재전환_불가() {
        Long id = jobPostingService.create(createRequest());
        jobPostingService.publish(id);
        jobPostingService.close(id);

        assertThatThrownBy(() -> jobPostingService.publish(id)).isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void PUBLISHED에서_CLOSED_전환_성공() {
        Long id = jobPostingService.create(createRequest());
        jobPostingService.publish(id);

        jobPostingService.close(id);

        assertThat(jobPostingService.getJobPosting(id).status()).isEqualTo(JobPostingStatus.CLOSED);
    }

    @Test
    void 존재하지않는_공고_조회시_예외() {
        assertThatThrownBy(() -> jobPostingService.getJobPosting(99999L)).isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void 목록_상세_조회_확인() {
        Long id = jobPostingService.create(createRequest());

<<<<<<< codex/verify-codex-cloud-build-and-test-setup-job4wm
        assertThat(jobPostingService.getJobPostings(0, 10).content()).isNotEmpty();
=======
        assertThat(jobPostingService.getJobPostings()).isNotEmpty();
>>>>>>> main
        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);
        assertThat(detail.id()).isEqualTo(id);
        assertThat(detail.applicationFormConfig().useEducation()).isTrue();
    }

    @Test
    void 모집분야_없이_수정불가() {
        Long id = jobPostingService.create(createRequest());
        JobPostingUpdateRequest update = new JobPostingUpdateRequest(
                "수정",
                "<p>수정</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                List.of(),
                new ApplicationFormConfigRequest(true, false, false, false, false, false, false)
        );

        assertThatThrownBy(() -> jobPostingService.update(id, update)).isInstanceOf(InvalidJobPostingException.class);
    }

    private JobPostingCreateRequest createRequest() {
        return new JobPostingCreateRequest(
                "2026 상반기 채용",
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                List.of(new JobPositionRequest("백엔드", 2, 1)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );
    }
}
