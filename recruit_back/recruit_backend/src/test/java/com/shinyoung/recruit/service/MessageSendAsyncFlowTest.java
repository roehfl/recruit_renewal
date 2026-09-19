package com.shinyoung.recruit.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.MessageRecipientRepository;
import com.shinyoung.recruit.domain.repository.MessageSendRepository;
import com.shinyoung.recruit.dto.request.MessageContentRequest;
import com.shinyoung.recruit.dto.request.MessageSendRequest;
import com.shinyoung.recruit.dto.response.MessageChannelCountResponse;
import com.shinyoung.recruit.dto.response.MessageRecipientResponse;
import com.shinyoung.recruit.dto.response.MessageSendDetailResponse;
import com.shinyoung.recruit.dto.response.MessageSendResultResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.MessageSendStatus;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 실제 발송 경로를 끝까지 검증한다: {@link MessageSendService#send}가 커밋되면
 * {@link MessageSendRequestedEvent}가 발행되고, {@link MessageDispatcher#onSendRequested}가
 * 커밋 후({@code AFTER_COMMIT}) 비동기로 실행되어 기본 로깅 게이트웨이({@link LoggingMailGateway},
 * {@link LoggingSmsGateway})를 호출하고 {@link MessageDispatchRecorder}가 접수 결과(REQUESTED + 거래 ID)를 기록한 뒤,
 * 3초 뒤 {@link MockDeliveryReportScheduler}의 가짜 결과를 {@link DeliveryReportHandler}가 반영하면
 * {@link MessageHistoryService}가 발송을 COMPLETED 로 계산한다.
 *
 * <p>{@code MessageSendServiceTest}는 {@code @Transactional}로 롤백되어 AFTER_COMMIT 이 발생하지
 * 않고, {@code MessageDispatcherTest}는 리스너를 직접 호출하므로 이 흐름 전체를 보여주지 못한다.
 *
 * <p>이 테스트는 {@code @Transactional}을 쓰지 않는다(롤백되면 커밋 후 이벤트가 발생하지 않는다).
 * 고정 픽스처(H2 in-memory, 이름 testdb)는 같은 JVM 에서 실행되는 다른 테스트와 공유되므로
 * {@link #tearDown()}에서 이 테스트가 만든 행을 전부 지운다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
class MessageSendAsyncFlowTest {

    private static final CustomUserDetails HR = CustomUserDetails.fromLdap(
            "hr.kim", "인사팀", "김인사", List.of(new SimpleGrantedAuthority("ROLE_RECRUIT_ADMIN")));
    private static final long POLL_TIMEOUT_MILLIS = 10_000L;
    private static final long POLL_INTERVAL_MILLIS = 100L;

    @Autowired
    private MessageSendService messageSendService;
    @Autowired
    private MessageHistoryService messageHistoryService;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private MessageSendRepository messageSendRepository;
    @Autowired
    private MessageRecipientRepository messageRecipientRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private JobPosting posting;
    private Applicant kimApplicant;
    private Applicant leeApplicant;
    private JobApplication kim;
    private JobApplication lee;
    private Long sendId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            posting = JobPosting.create("비동기 발송 흐름 공고", "Content",
                    LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
            posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
            posting = jobPostingRepository.saveAndFlush(posting);

            kimApplicant = newApplicant("김민준", "01000000000");
            kim = submitted(kimApplicant, "김민준");
            leeApplicant = newApplicant("이서연", null);
            lee = submitted(leeApplicant, "이서연");
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            if (sendId != null) {
                messageRecipientRepository.deleteAll(
                        messageRecipientRepository.findByMessageSendIdOrderByIdAsc(sendId));
                messageSendRepository.findById(sendId).ifPresent(messageSendRepository::delete);
            }
            if (lee != null) {
                jobApplicationRepository.findById(lee.getId()).ifPresent(jobApplicationRepository::delete);
            }
            if (kim != null) {
                jobApplicationRepository.findById(kim.getId()).ifPresent(jobApplicationRepository::delete);
            }
            if (leeApplicant != null) {
                applicantRepository.findById(leeApplicant.getId()).ifPresent(applicantRepository::delete);
            }
            if (kimApplicant != null) {
                applicantRepository.findById(kimApplicant.getId()).ifPresent(applicantRepository::delete);
            }
            if (posting != null) {
                jobPostingRepository.findById(posting.getId()).ifPresent(jobPostingRepository::delete);
            }
        });
    }

    @Test
    void 발송_요청은_커밋_후_비동기로_접수되고_목업_결과를_받아_완료로_계산된다() {
        Logger mailLogger = (Logger) LoggerFactory.getLogger(LoggingMailGateway.class);
        ListAppender<ILoggingEvent> mailLogAppender = new ListAppender<>();
        mailLogAppender.start();
        mailLogger.addAppender(mailLogAppender);
        try {
            MessageSendRequest request = new MessageSendRequest(MessageType.FREE, posting.getId(),
                    null, null, null, JobApplicationStatus.SUBMITTED,
                    List.of(kim.getId(), lee.getId()),
                    new MessageContentRequest(null, true, true,
                            "[신영증권] #{이름}님 안내", "#{이름}님, 안녕하세요.", "#{이름}님 안내 문자"));

            // 테스트 트랜잭션 밖(이 메서드는 @Transactional 이 아니다)에서 호출해야
            // send() 자체의 트랜잭션이 실제로 커밋되고 AFTER_COMMIT 리스너가 동작한다.
            MessageSendResultResponse result = messageSendService.send(request, HR);
            sendId = result.sendId();

            assertThat(result.status()).isEqualTo(MessageSendStatus.SENDING);
            assertThat(result.recipientCount()).isEqualTo(2);
            assertThat(result.excludedCount()).isEqualTo(0);

            MessageSendDetailResponse detail = awaitCompleted(sendId);

            assertThat(detail.delayed()).isFalse();
            assertThat(detail.recipientCount()).isEqualTo(2);
            assertThat(detail.mail()).isEqualTo(new MessageChannelCountResponse(0, 0, 2, 0, 0));
            assertThat(detail.sms()).isEqualTo(new MessageChannelCountResponse(0, 0, 1, 0, 1));
            assertThat(detail.recipients())
                    .extracting(MessageRecipientResponse::mailStatus, MessageRecipientResponse::smsStatus,
                            MessageRecipientResponse::smsFailureReason)
                    .containsExactlyInAnyOrder(
                            tuple(MessageDeliveryStatus.SENT, MessageDeliveryStatus.SENT, null),
                            tuple(MessageDeliveryStatus.SENT, MessageDeliveryStatus.SKIPPED, MessageContacts.NO_CONTACT));
            assertThat(messageRecipientRepository.findByMessageSendIdOrderByIdAsc(sendId))
                    .allSatisfy(recipient -> assertThat(recipient.getMailTransactionId()).isNotBlank());

            // 기본 로깅 게이트웨이가 실제로 호출됐는지, 그리고 이 호출이 테스트 스레드가 아닌
            // @Async 디스패치 스레드에서 일어났는지를 프로덕션 코드 변경 없이 로그로 확인한다.
            assertThat(mailLogAppender.list).as("LoggingMailGateway 가 호출됐어야 한다").isNotEmpty();
            String dispatchThreadName = mailLogAppender.list.get(0).getThreadName();
            assertThat(dispatchThreadName).as("디스패치는 비동기 스레드에서 실행돼야 한다")
                    .isNotEqualTo(Thread.currentThread().getName());
        } finally {
            mailLogger.detachAppender(mailLogAppender);
            mailLogAppender.stop();
        }
    }

    private MessageSendDetailResponse awaitCompleted(Long id) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            MessageSendDetailResponse detail = messageHistoryService.detail(id);
            if (detail.status() == MessageSendStatus.COMPLETED) {
                return detail;
            }
            sleepQuietly();
        }
        fail("메시지 발송이 " + POLL_TIMEOUT_MILLIS + "ms 안에 COMPLETED 로 계산되지 않았습니다(sendId=" + id + ").");
        throw new IllegalStateException("unreachable");
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Applicant newApplicant(String name, String phoneNumber) {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName(name);
        applicant.setPhoneNumber(phoneNumber);
        return applicantRepository.saveAndFlush(applicant);
    }

    private JobApplication submitted(Applicant applicant, String name) {
        JobPosition jobPosition = posting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, posting, jobPosition, name, posting.getTitle(), jobPosition.getPositionName());
        application.submit(LocalDateTime.of(2026, 9, 10, 9, 0));
        return jobApplicationRepository.saveAndFlush(application);
    }
}
