package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.ApplicationFormConfigResponse;
import com.shinyoung.recruit.dto.response.JobPostingDetailResponse;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.posting-image.storage-root=build/test-posting-images"
})
@Transactional
class ApplicationFormConfigServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-01T10:00:00Z"),
            ZoneId.of("UTC")
    );

    /** 접수 시작 전이라 양식 수정이 허용되는 공고. */
    private static final LocalDateTime FUTURE_RECEPTION_START = LocalDateTime.of(2026, 7, 1, 9, 0);
    private static final LocalDateTime FUTURE_RECEPTION_END = LocalDateTime.of(2026, 7, 2, 18, 0);

    /** 고정 시계(2026-06-01T10:00) 기준으로 이미 접수가 시작된 공고. */
    private static final LocalDateTime STARTED_RECEPTION_START = LocalDateTime.of(2026, 6, 1, 9, 0);
    private static final LocalDateTime STARTED_RECEPTION_END = LocalDateTime.of(2026, 6, 2, 18, 0);

    @Autowired
    private ApplicationFormConfigService applicationFormConfigService;

    @Autowired
    private JobPostingService jobPostingService;

    @Test
    void 공고_등록시_양식을_생략하면_기본설정이_만들어진다() {
        Long id = jobPostingService.create(createRequest(FUTURE_RECEPTION_START, FUTURE_RECEPTION_END, null));

        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);
        assertThat(detail.applicationFormConfig()).isNotNull();
        assertThat(detail.applicationFormConfig().useEducation()).isTrue();
        assertThat(detail.applicationFormConfig().useCareer()).isTrue();
        assertThat(detail.applicationFormConfig().useMilitary()).isTrue();
        assertThat(detail.applicationFormConfig().requireEducation()).isTrue();
        assertThat(detail.applicationFormConfig().requireCareer()).isTrue();
        assertThat(detail.applicationFormConfig().requireMilitary()).isTrue();
        assertThat(detail.applicationFormConfig().requireCertificate()).isFalse();
        assertThat(detail.applicationFormConfig().useAttachment()).isFalse();
    }

    @Test
    void 양식을_저장하면_설정이_바뀐다() {
        Long id = jobPostingService.create(createRequest(FUTURE_RECEPTION_START, FUTURE_RECEPTION_END, null));

        ApplicationFormConfigResponse response = applicationFormConfigService.save(
                id,
                new ApplicationFormConfigRequest(true, false, false, false, false, false, false)
        );

        assertThat(response.useEducation()).isTrue();
        assertThat(response.useCareer()).isFalse();
        assertThat(jobPostingService.getJobPosting(id).applicationFormConfig().useCareer()).isFalse();
    }

    @Test
    void 섹션이_계속_활성이면_생략한_필수값을_유지한다() {
        Long id = jobPostingService.create(createRequest(
                FUTURE_RECEPTION_START,
                FUTURE_RECEPTION_END,
                new ApplicationFormConfigRequest(
                        true, true,
                        true, false,
                        false, false,
                        false, false,
                        false, false,
                        false, false,
                        false, false,
                        false
                )
        ));

        applicationFormConfigService.save(
                id,
                new ApplicationFormConfigRequest(true, true, false, false, false, false, false)
        );

        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);
        assertThat(detail.applicationFormConfig().requireEducation()).isTrue();
        assertThat(detail.applicationFormConfig().requireCareer()).isFalse();
    }

    @Test
    void 섹션을_끄면_생략한_필수값도_해제된다() {
        Long id = jobPostingService.create(createRequest(
                FUTURE_RECEPTION_START,
                FUTURE_RECEPTION_END,
                new ApplicationFormConfigRequest(
                        true, true,
                        true, true,
                        false, false,
                        false, false,
                        false, false,
                        false, false,
                        false, false,
                        false
                )
        ));

        applicationFormConfigService.save(
                id,
                new ApplicationFormConfigRequest(true, false, false, false, false, false, false)
        );

        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);
        assertThat(detail.applicationFormConfig().useCareer()).isFalse();
        assertThat(detail.applicationFormConfig().requireCareer()).isFalse();
    }

    @Test
    void 비활성_섹션을_필수로_저장할_수_없다() {
        Long id = jobPostingService.create(createRequest(FUTURE_RECEPTION_START, FUTURE_RECEPTION_END, null));

        ApplicationFormConfigRequest request = new ApplicationFormConfigRequest(
                true, true,
                false, true,
                false, false,
                false, false,
                false, false,
                false, false,
                false, false,
                false
        );

        assertThatThrownBy(() -> applicationFormConfigService.save(id, request))
                .isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void 접수가_시작되면_양식을_수정할_수_없다() {
        Long id = jobPostingService.create(createRequest(STARTED_RECEPTION_START, STARTED_RECEPTION_END, null));

        ApplicationFormConfigRequest request =
                new ApplicationFormConfigRequest(true, false, false, false, false, false, false);

        assertThatThrownBy(() -> applicationFormConfigService.save(id, request))
                .isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("접수가 시작된");
    }

    @Test
    void 마감된_공고의_양식은_수정할_수_없다() {
        Long id = jobPostingService.create(createRequest(STARTED_RECEPTION_START, STARTED_RECEPTION_END, null));
        jobPostingService.publish(id);
        jobPostingService.close(id);

        ApplicationFormConfigRequest request =
                new ApplicationFormConfigRequest(true, false, false, false, false, false, false);

        assertThatThrownBy(() -> applicationFormConfigService.save(id, request))
                .isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("마감된");
    }

    @Test
    void 존재하지_않는_공고면_예외() {
        ApplicationFormConfigRequest request =
                new ApplicationFormConfigRequest(true, false, false, false, false, false, false);

        assertThatThrownBy(() -> applicationFormConfigService.save(999999L, request))
                .isInstanceOf(JobPostingNotFoundException.class);
    }

    private JobPostingCreateRequest createRequest(
            LocalDateTime receptionStart,
            LocalDateTime receptionEnd,
            ApplicationFormConfigRequest applicationFormConfig
    ) {
        return new JobPostingCreateRequest(
                "2026 상반기 채용",
                "<p>내용</p>",
                receptionStart,
                receptionEnd,
                List.of(new JobPositionRequest("백엔드", 1)),
                applicationFormConfig
        );
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return FIXED_CLOCK;
        }
    }
}
