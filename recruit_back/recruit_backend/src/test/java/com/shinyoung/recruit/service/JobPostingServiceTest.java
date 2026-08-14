package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.ApplicationFormLayoutSaveRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingUpdateRequest;
import com.shinyoung.recruit.dto.response.JobPostingDetailResponse;
import com.shinyoung.recruit.dto.response.JobPostingListResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.JobPostingType;
import com.shinyoung.recruit.enumeration.ReceptionStatus;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.posting-image.storage-root=build/test-posting-images"
})
@Transactional
class JobPostingServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-01T10:00:00Z"),
            ZoneId.of("UTC")
    );

    private static final byte[] PNG_HEAD = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private ApplicationFormLayoutService applicationFormLayoutService;

    @Autowired
    private JobPostingImageService jobPostingImageService;

    @Test
    void JobPosting_생성_성공() {
        Long id = jobPostingService.create(createRequest());
        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);

        assertThat(detail.status()).isEqualTo(JobPostingStatus.DRAFT);
        assertThat(detail.jobPositions()).hasSize(1);
    }

    @Test
    void 공고_생성시_확장필드를_저장한다() {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "2026 경력 채용",
                JobPostingType.EXPERIENCED_RECRUITMENT,
                "경력직 모집",
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                LocalDateTime.of(2026, 5, 25, 9, 0),
                LocalDateTime.of(2026, 6, 10, 18, 0),
                false,
                true,
                7,
                List.of(new JobPositionRequest(
                        "리서치",
                        JobPositionApplicationType.EXPERIENCED,
                        "Research",
                        "Equity Analyst",
                        "Seoul",
                        EmploymentType.CONTRACT,
                        0
                )),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );

        Long id = jobPostingService.create(request);

        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);
        assertThat(detail.postingType()).isEqualTo(JobPostingType.EXPERIENCED_RECRUITMENT);
        assertThat(detail.summary()).isEqualTo("경력직 모집");
        assertThat(detail.displayStartDateTime()).isEqualTo(LocalDateTime.of(2026, 5, 25, 9, 0));
        assertThat(detail.displayEndDateTime()).isEqualTo(LocalDateTime.of(2026, 6, 10, 18, 0));
        assertThat(detail.visible()).isFalse();
        assertThat(detail.pinned()).isTrue();
        assertThat(detail.displayOrder()).isEqualTo(7);
        assertThat(detail.positionCount()).isEqualTo(1);
        assertThat(detail.jobPositions().get(0).applicationType()).isEqualTo(JobPositionApplicationType.EXPERIENCED);
        assertThat(detail.jobPositions().get(0).jobGroup()).isEqualTo("Research");
        assertThat(detail.jobPositions().get(0).jobTitle()).isEqualTo("Equity Analyst");
        assertThat(detail.jobPositions().get(0).workLocation()).isEqualTo("Seoul");
        assertThat(detail.jobPositions().get(0).employmentType()).isEqualTo(EmploymentType.CONTRACT);
    }

    @Test
    void 공고_생성시_확장필드_null이면_기본값을_적용한다() {
        Long id = jobPostingService.create(createRequest());

        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);
        assertThat(detail.postingType()).isEqualTo(JobPostingType.PUBLIC_RECRUITMENT);
        assertThat(detail.summary()).isNull();
        assertThat(detail.displayStartDateTime()).isNull();
        assertThat(detail.displayEndDateTime()).isNull();
        assertThat(detail.visible()).isTrue();
        assertThat(detail.pinned()).isFalse();
        assertThat(detail.displayOrder()).isZero();
        assertThat(detail.jobPositions().get(0).applicationType()).isEqualTo(JobPositionApplicationType.NEW_GRADUATE_OR_EXPERIENCED);
        assertThat(detail.jobPositions().get(0).jobGroup()).isNull();
        assertThat(detail.jobPositions().get(0).jobTitle()).isNull();
        assertThat(detail.jobPositions().get(0).workLocation()).isNull();
        assertThat(detail.jobPositions().get(0).employmentType()).isEqualTo(EmploymentType.FULL_TIME);
    }

    @Test
    void 접수종료일시가_시작보다_빠르면_생성실패() {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "2026 상반기 채용",
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 2, 9, 0),
                LocalDateTime.of(2026, 6, 1, 18, 0),
                List.of(new JobPositionRequest("백엔드", 1)),
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
    void summary에_HTML_태그가_있으면_생성_수정_실패() {
        JobPostingCreateRequest create = new JobPostingCreateRequest(
                "2026 상반기 채용",
                null,
                "<b>요약</b>",
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                null,
                null,
                null,
                null,
                null,
                List.of(new JobPositionRequest("백엔드", 1)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );
        assertThatThrownBy(() -> jobPostingService.create(create)).isInstanceOf(InvalidJobPostingException.class);

        Long id = jobPostingService.create(createRequest());
        JobPostingUpdateRequest update = new JobPostingUpdateRequest(
                "수정",
                null,
                "<i>요약</i>",
                "<p>수정</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                null,
                null,
                null,
                null,
                null,
                List.of(new JobPositionRequest("백엔드", 1)),
                new ApplicationFormConfigRequest(true, false, false, false, false, false, false)
        );
        assertThatThrownBy(() -> jobPostingService.update(id, update)).isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void 노출기간이_역전되면_생성_수정_실패() {
        JobPostingCreateRequest create = new JobPostingCreateRequest(
                "2026 상반기 채용",
                null,
                "요약",
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                LocalDateTime.of(2026, 6, 3, 9, 0),
                LocalDateTime.of(2026, 6, 2, 9, 0),
                null,
                null,
                null,
                List.of(new JobPositionRequest("백엔드", 1)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );
        assertThatThrownBy(() -> jobPostingService.create(create)).isInstanceOf(InvalidJobPostingException.class);

        Long id = jobPostingService.create(createRequest());
        JobPostingUpdateRequest update = new JobPostingUpdateRequest(
                "수정",
                null,
                "요약",
                "<p>수정</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                LocalDateTime.of(2026, 6, 3, 9, 0),
                LocalDateTime.of(2026, 6, 2, 9, 0),
                null,
                null,
                null,
                List.of(new JobPositionRequest("백엔드", 1)),
                new ApplicationFormConfigRequest(true, false, false, false, false, false, false)
        );
        assertThatThrownBy(() -> jobPostingService.update(id, update)).isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void 모집분야_sortOrder가_중복되면_생성_실패() {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "2026 상반기 채용",
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                List.of(
                        new JobPositionRequest("백엔드", 1),
                        new JobPositionRequest("프론트엔드", 1)
                ),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );

        assertThatThrownBy(() -> jobPostingService.create(request)).isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void service_직접호출시_공고_확장필드_검증을_수행한다() {
        assertThatThrownBy(() -> jobPostingService.create(createExtendedRequest("a".repeat(501), null, List.of(new JobPositionRequest("백엔드", 1)))))
                .isInstanceOf(InvalidJobPostingException.class);

        assertThatThrownBy(() -> jobPostingService.create(createExtendedRequest("요약", -1, List.of(new JobPositionRequest("백엔드", 1)))))
                .isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void service_직접호출시_모집분야_필드_검증을_수행한다() {
        String tooLong = "a".repeat(101);

        assertThatThrownBy(() -> jobPostingService.create(createRequestWithPosition(new JobPositionRequest("", 0))))
                .isInstanceOf(InvalidJobPostingException.class);
        assertThatThrownBy(() -> jobPostingService.create(createRequestWithPosition(new JobPositionRequest(tooLong, 0))))
                .isInstanceOf(InvalidJobPostingException.class);
        assertThatThrownBy(() -> jobPostingService.create(createRequestWithPosition(new JobPositionRequest("백엔드", null))))
                .isInstanceOf(InvalidJobPostingException.class);
        assertThatThrownBy(() -> jobPostingService.create(createRequestWithPosition(new JobPositionRequest("백엔드", -1))))
                .isInstanceOf(InvalidJobPostingException.class);
        assertThatThrownBy(() -> jobPostingService.create(createRequestWithPosition(new JobPositionRequest(
                "백엔드",
                JobPositionApplicationType.EXPERIENCED,
                tooLong,
                "Backend",
                "Seoul",
                EmploymentType.FULL_TIME,
                0
        )))).isInstanceOf(InvalidJobPostingException.class);
        assertThatThrownBy(() -> jobPostingService.create(createRequestWithPosition(new JobPositionRequest(
                "백엔드",
                JobPositionApplicationType.EXPERIENCED,
                "IT",
                tooLong,
                "Seoul",
                EmploymentType.FULL_TIME,
                0
        )))).isInstanceOf(InvalidJobPostingException.class);
        assertThatThrownBy(() -> jobPostingService.create(createRequestWithPosition(new JobPositionRequest(
                "백엔드",
                JobPositionApplicationType.EXPERIENCED,
                "IT",
                "Backend",
                tooLong,
                EmploymentType.FULL_TIME,
                0
        )))).isInstanceOf(InvalidJobPostingException.class);
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
    void 게시와_마감_시간은_Clock_기준으로_저장된다() {
        Long id = jobPostingService.create(createRequest());

        jobPostingService.publish(id);
        JobPostingDetailResponse published = jobPostingService.getJobPosting(id);

        jobPostingService.close(id);
        JobPostingDetailResponse closed = jobPostingService.getJobPosting(id);

        assertThat(published.publishedAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 10, 0));
        assertThat(closed.closedAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 10, 0));
    }

    @Test
    void 존재하지않는_공고_조회시_예외() {
        assertThatThrownBy(() -> jobPostingService.getJobPosting(99999L)).isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void 목록_상세_조회_확인() {
        Long id = jobPostingService.create(createRequest());

        assertThat(jobPostingService.getJobPostings(0, 10).content()).isNotEmpty();
        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);
        assertThat(detail.id()).isEqualTo(id);
        assertThat(detail.applicationFormConfig().useEducation()).isTrue();
    }

    @Test
    void application_form_required_defaults_are_applied_when_omitted() {
        Long id = jobPostingService.create(createRequest(new ApplicationFormConfigRequest(
                true,
                null,
                true,
                null,
                true,
                null,
                true,
                null,
                true,
                null,
                true,
                null,
                true,
                null,
                false
        )));

        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);

        assertThat(detail.applicationFormConfig().requireEducation()).isTrue();
        assertThat(detail.applicationFormConfig().requireCareer()).isTrue();
        assertThat(detail.applicationFormConfig().requireMilitary()).isTrue();
        assertThat(detail.applicationFormConfig().requireCertificate()).isFalse();
        assertThat(detail.applicationFormConfig().requireLanguage()).isFalse();
        assertThat(detail.applicationFormConfig().requireAward()).isFalse();
        assertThat(detail.applicationFormConfig().requireGapPeriod()).isFalse();
    }

    @Test
    void application_form_required_update_preserves_omitted_values_when_section_stays_enabled() {
        Long id = jobPostingService.create(createRequest(new ApplicationFormConfigRequest(
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        )));

        jobPostingService.update(id, updateRequest(new ApplicationFormConfigRequest(true, true, false, false, false, false, false)));

        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);
        assertThat(detail.applicationFormConfig().requireEducation()).isTrue();
        assertThat(detail.applicationFormConfig().requireCareer()).isFalse();
    }

    @Test
    void application_form_required_update_resets_omitted_value_when_section_is_disabled() {
        Long id = jobPostingService.create(createRequest(new ApplicationFormConfigRequest(
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        )));

        jobPostingService.update(id, updateRequest(new ApplicationFormConfigRequest(true, false, false, false, false, false, false)));

        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);
        assertThat(detail.applicationFormConfig().useCareer()).isFalse();
        assertThat(detail.applicationFormConfig().requireCareer()).isFalse();
    }

    @Test
    void application_form_required_cannot_be_true_when_section_is_disabled() {
        JobPostingCreateRequest request = createRequest(new ApplicationFormConfigRequest(
                true,
                true,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        ));

        assertThatThrownBy(() -> jobPostingService.create(request))
                .isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void 관리자_목록_페이지_요청값이_잘못되면_예외() {
        assertThatThrownBy(() -> jobPostingService.getJobPostings(-1, 10))
                .isInstanceOf(InvalidJobPostingException.class);
        assertThatThrownBy(() -> jobPostingService.getJobPostings(0, 101))
                .isInstanceOf(InvalidJobPostingException.class);
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

    @Test
    void 공고_수정시_확장필드를_변경한다() {
        Long id = jobPostingService.create(createRequest());
        JobPostingUpdateRequest update = new JobPostingUpdateRequest(
                "수정",
                JobPostingType.INTERN_RECRUITMENT,
                "인턴 채용",
                "<p>수정</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                LocalDateTime.of(2026, 5, 30, 9, 0),
                LocalDateTime.of(2026, 6, 5, 18, 0),
                false,
                true,
                3,
                List.of(new JobPositionRequest(
                        "인턴",
                        JobPositionApplicationType.NEW_GRADUATE,
                        "IT",
                        "Backend Intern",
                        "Seoul",
                        EmploymentType.INTERN,
                        0
                )),
                new ApplicationFormConfigRequest(true, false, false, false, false, false, false)
        );

        jobPostingService.update(id, update);

        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);
        assertThat(detail.title()).isEqualTo("수정");
        assertThat(detail.postingType()).isEqualTo(JobPostingType.INTERN_RECRUITMENT);
        assertThat(detail.summary()).isEqualTo("인턴 채용");
        assertThat(detail.visible()).isFalse();
        assertThat(detail.pinned()).isTrue();
        assertThat(detail.displayOrder()).isEqualTo(3);
        assertThat(detail.jobPositions().get(0).employmentType()).isEqualTo(EmploymentType.INTERN);
    }

    @Test
    void 관리자_응답은_ReceptionStatus와_accepting을_계산한다() {
        Long acceptingId = jobPostingService.create(createRequest());
        jobPostingService.publish(acceptingId);
        Long upcomingId = jobPostingService.create(new JobPostingCreateRequest(
                "예정",
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 2, 9, 0),
                LocalDateTime.of(2026, 6, 3, 18, 0),
                List.of(new JobPositionRequest("백엔드", 1)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        ));
        Long closedByTimeId = jobPostingService.create(new JobPostingCreateRequest(
                "기간마감",
                "<p>내용</p>",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 2, 18, 0),
                List.of(new JobPositionRequest("백엔드", 1)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        ));

        assertThat(jobPostingService.getJobPosting(acceptingId).receptionStatus()).isEqualTo(ReceptionStatus.ACCEPTING);
        assertThat(jobPostingService.getJobPosting(acceptingId).accepting()).isTrue();
        assertThat(jobPostingService.getJobPosting(upcomingId).receptionStatus()).isEqualTo(ReceptionStatus.UPCOMING);
        assertThat(jobPostingService.getJobPosting(upcomingId).accepting()).isFalse();
        assertThat(jobPostingService.getJobPosting(closedByTimeId).receptionStatus()).isEqualTo(ReceptionStatus.CLOSED);
        assertThat(jobPostingService.getJobPosting(closedByTimeId).accepting()).isFalse();

        JobPostingListResponse listItem = jobPostingService.getJobPostings(0, 10).content().stream()
                .filter(it -> it.id().equals(acceptingId))
                .findFirst()
                .orElseThrow();
        assertThat(listItem.receptionStatus()).isEqualTo(ReceptionStatus.ACCEPTING);
        assertThat(listItem.accepting()).isTrue();
        assertThat(listItem.positionCount()).isEqualTo(1);
    }

    @Test
    void 게시_시_저장된_유효한_레이아웃이_있으면_성공() {
        Long id = jobPostingService.create(createFutureReceptionRequest(
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        ));
        applicationFormLayoutService.saveLayout(id, buildLayoutRequest(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.EDUCATION,
                ApplicationSectionType.CAREER,
                ApplicationSectionType.CERTIFICATE,
                ApplicationSectionType.LANGUAGE,
                ApplicationSectionType.MILITARY,
                ApplicationSectionType.AWARD,
                ApplicationSectionType.GAP_PERIOD
        ));

        jobPostingService.publish(id);

        assertThat(jobPostingService.getJobPosting(id).status()).isEqualTo(JobPostingStatus.PUBLISHED);
    }

    @Test
    void 게시_시_저장된_레이아웃이_현재_설정과_불일치하면_실패() {
        Long id = jobPostingService.create(createFutureReceptionRequest(
                new ApplicationFormConfigRequest(true, true, true, true, true, true, false)
        ));
        applicationFormLayoutService.saveLayout(id, buildLayoutRequest(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.EDUCATION,
                ApplicationSectionType.CAREER,
                ApplicationSectionType.CERTIFICATE,
                ApplicationSectionType.LANGUAGE,
                ApplicationSectionType.MILITARY,
                ApplicationSectionType.AWARD
        ));
        jobPostingService.update(id, new JobPostingUpdateRequest(
                "수정",
                "<p>수정</p>",
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 2, 18, 0),
                List.of(new JobPositionRequest("백엔드", 1)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        ));

        assertThatThrownBy(() -> jobPostingService.publish(id))
                .isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("레이아웃 검증 실패");
    }

    @Test
    void 게시_시_폴백_레이아웃이_유효하면_성공() {
        Long id = jobPostingService.create(createFutureReceptionRequest(
                new ApplicationFormConfigRequest(true, false, true, false, false, false, false)
        ));

        jobPostingService.publish(id);

        assertThat(jobPostingService.getJobPosting(id).status()).isEqualTo(JobPostingStatus.PUBLISHED);
    }

    @Test
    void contentHtml_없이_공고를_생성할_수_있다() {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "이미지 공고",
                null,
                null,
                null,   // contentHtml 없음
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                null, null, null, null, null,
                List.of(new JobPositionRequest("백엔드", 0)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );

        Long id = jobPostingService.create(request);

        // null 입력은 빈 문자열로 저장된다(기존 스키마 NOT NULL과의 호환 — JobPosting.defaultContentHtml).
        assertThat(jobPostingService.getJobPosting(id).contentHtml()).isEmpty();
    }

    @Test
    void 이미지와_contentHtml_모두_없으면_발행할_수_없다() {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "이미지 공고",
                null,
                null,
                null,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                null, null, null, null, null,
                List.of(new JobPositionRequest("백엔드", 0)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );
        Long id = jobPostingService.create(request);

        assertThatThrownBy(() -> jobPostingService.publish(id))
                .isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("이미지");
    }

    @Test
    void 상세조회에_이미지_목록이_정렬순으로_포함된다() {
        Long id = jobPostingService.create(createRequest());
        jobPostingImageService.addImage(id,
                new org.springframework.mock.web.MockMultipartFile("file", "b.png", "image/png", PNG_HEAD), "포스터 2", 1);
        jobPostingImageService.addImage(id,
                new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", PNG_HEAD), "포스터 1", 0);

        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);

        assertThat(detail.images()).hasSize(2);
        assertThat(detail.images().get(0).altText()).isEqualTo("포스터 1");
    }

    @Test
    void 이미지가_있으면_contentHtml_없이_발행된다() {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "이미지 공고",
                null,
                null,
                null,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                null, null, null, null, null,
                List.of(new JobPositionRequest("백엔드", 0)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );
        Long id = jobPostingService.create(request);
        jobPostingImageService.addImage(
                id,
                new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", PNG_HEAD),
                "채용 포스터",
                0
        );

        Long published = jobPostingService.publish(id);

        assertThat(published).isEqualTo(id);
    }

    private JobPostingCreateRequest createRequest() {
        return createRequest(new ApplicationFormConfigRequest(true, true, true, true, true, true, true));
    }

    private JobPostingCreateRequest createRequest(ApplicationFormConfigRequest applicationFormConfig) {
        return new JobPostingCreateRequest(
                "2026 상반기 채용",
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                List.of(new JobPositionRequest("백엔드", 1)),
                applicationFormConfig
        );
    }

    private JobPostingUpdateRequest updateRequest(ApplicationFormConfigRequest applicationFormConfig) {
        return new JobPostingUpdateRequest(
                "수정",
                "<p>수정</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                List.of(new JobPositionRequest("백엔드", 1)),
                applicationFormConfig
        );
    }

    private JobPostingCreateRequest createRequestWithPosition(JobPositionRequest jobPosition) {
        return createExtendedRequest("요약", null, List.of(jobPosition));
    }

    private JobPostingCreateRequest createExtendedRequest(String summary, Integer displayOrder, List<JobPositionRequest> jobPositions) {
        return new JobPostingCreateRequest(
                "2026 상반기 채용",
                JobPostingType.PUBLIC_RECRUITMENT,
                summary,
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 2, 18, 0),
                null,
                null,
                true,
                false,
                displayOrder,
                jobPositions,
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );
    }

    private JobPostingCreateRequest createFutureReceptionRequest(ApplicationFormConfigRequest config) {
        return new JobPostingCreateRequest(
                "2026 하반기 채용",
                "<p>내용</p>",
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 2, 18, 0),
                List.of(new JobPositionRequest("백엔드", 1)),
                config
        );
    }

    private ApplicationFormLayoutSaveRequest buildLayoutRequest(ApplicationSectionType... sections) {
        List<ApplicationFormLayoutSaveRequest.ItemRequest> items = new ArrayList<>();
        for (int i = 0; i < sections.length; i++) {
            items.add(new ApplicationFormLayoutSaveRequest.ItemRequest(sections[i], i));
        }
        return new ApplicationFormLayoutSaveRequest(List.of(
                new ApplicationFormLayoutSaveRequest.PageRequest(1, "Page 1", null, 0, items)
        ));
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
