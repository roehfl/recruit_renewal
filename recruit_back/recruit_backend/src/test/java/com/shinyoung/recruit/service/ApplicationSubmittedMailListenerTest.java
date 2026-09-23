package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationSubmittedMailListenerTest {

    @Autowired
    private ApplicationSubmittedMailListener listener;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private ApplicationBasicInfoRepository applicationBasicInfoRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;

    @MockitoBean
    private MailGateway mailGateway;
    @MockitoBean
    private SmsGateway smsGateway;

    private JobPosting posting;

    @BeforeEach
    void setUp() {
        posting = JobPosting.create("제출 메일 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("TX-SUBMIT-1"));
    }

    @Test
    void 기본정보_이메일로_제출_완료_메일을_보내고_시스템_이력에_공고를_남긴다() {
        JobApplication application = submitted("김회원", "member@example.com");
        basicInfo(application, "김제출", "basic@example.com");

        listener.sendSubmittedMail(application.getId());

        ArgumentCaptor<MailMessage> mail = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(mail.capture(), eq(List.of("basic@example.com")), eq(List.of("김제출")));
        assertThat(mail.getValue().subject()).isEqualTo("[신영증권 채용] 제출 메일 공고 지원서 제출 완료 안내");
        assertThat(mail.getValue().text()).contains("김제출님, 제출 메일 공고 지원서가 2026-09-10 09:00에 제출되었습니다.");
        MessageSend send = messageSendRepository.findAll().stream()
                .filter(row -> row.getType() == MessageType.APPLICATION_SUBMITTED)
                .max(Comparator.comparing(MessageSend::getId))
                .orElseThrow();
        assertThat(send.getOrigin()).isEqualTo(MessageOrigin.SYSTEM);
        assertThat(send.getJobPosting().getId()).isEqualTo(posting.getId());
    }

    @Test
    void 기본정보_이메일이_없으면_회원_이메일로_보낸다() {
        JobApplication application = submitted("김회원", "member@example.com");

        listener.sendSubmittedMail(application.getId());

        verify(mailGateway).send(any(), eq(List.of("member@example.com")), eq(List.of("김회원")));
    }

    @Test
    void 받을_주소가_없으면_보내지_않는다() {
        JobApplication application = submitted("김주소없음", null);

        listener.sendSubmittedMail(application.getId());

        verifyNoInteractions(mailGateway);
    }

    private JobApplication submitted(String name, String email) {
        Applicant applicant = new Applicant(HashUtil.sha256("test-ci-" + UUID.randomUUID()));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setUserName(name);
        applicant.setEmail(email);
        applicant.setPhoneNumber("01000000000");
        applicantRepository.saveAndFlush(applicant);
        JobPosition jobPosition = posting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, posting, jobPosition, name, posting.getTitle(), jobPosition.getPositionName());
        application.submit(LocalDateTime.of(2026, 9, 10, 9, 0));
        return jobApplicationRepository.saveAndFlush(application);
    }

    private void basicInfo(JobApplication application, String name, String email) {
        applicationBasicInfoRepository.saveAndFlush(ApplicationBasicInfo.create(
                application, name, null, NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01000000000", null, email,
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null, null));
    }
}
