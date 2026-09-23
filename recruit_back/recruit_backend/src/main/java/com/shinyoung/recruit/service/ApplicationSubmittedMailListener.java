package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.MessageVariable;
import com.shinyoung.recruit.enumeration.SystemMailOutcome;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 제출 완료 메일(설계서 6.5). 제출 트랜잭션이 커밋되면 비동기로 지원서·기본정보·공고를 읽고
 * SystemMailService 로 보낸다(이미 비동기 스레드라 동기 디스패치). 받는 주소는 기본정보 이메일 → 회원 이메일,
 * 둘 다 없으면 보내지 않는다. 예외는 경고 로그만 남기고 제출 결과에는 영향을 주지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ApplicationSubmittedMailListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSubmittedMailListener.class);
    private static final DateTimeFormatter SUBMITTED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationBasicInfoRepository applicationBasicInfoRepository;
    private final SystemMailService systemMailService;
    private final PlatformTransactionManager transactionManager;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(ApplicationSubmittedEvent event) {
        try {
            sendSubmittedMail(event.applicationId());
        } catch (RuntimeException e) {
            log.warn("제출 완료 메일 처리 실패: applicationId={}, error={}",
                    event.applicationId(), e.getClass().getSimpleName());
        }
    }

    /** 제출 완료 메일 1통. 읽기는 짧은 읽기 트랜잭션에서 끝내고, 발송은 트랜잭션 밖에서 부른다(이력 커밋 후 디스패치). */
    public void sendSubmittedMail(Long applicationId) {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        SubmittedMail mail = readOnly.execute(status -> load(applicationId));
        if (mail == null) {
            log.warn("제출 완료 메일 대상 지원서가 없습니다: applicationId={}", applicationId);
            return;
        }
        if (mail.email() == null) {
            log.warn("제출 완료 메일 받을 주소가 없어 보내지 않습니다: applicationId={}", applicationId);
            return;
        }
        SystemMailOutcome outcome = systemMailService.send(MessageType.APPLICATION_SUBMITTED, mail.email(), mail.name(),
                Map.of(MessageVariable.JOB_POSTING_TITLE.getKey(), mail.jobPostingTitle(),
                        MessageVariable.SUBMITTED_AT.getKey(), mail.submittedAt()),
                mail.jobPosting(), mail.application());
        if (outcome != SystemMailOutcome.ACCEPTED) {
            log.warn("제출 완료 메일을 보내지 못했습니다: applicationId={}, outcome={}", applicationId, outcome);
        }
    }

    private SubmittedMail load(Long applicationId) {
        JobApplication application = jobApplicationRepository.findById(applicationId).orElse(null);
        if (application == null) {
            return null;
        }
        ApplicationBasicInfo info = applicationBasicInfoRepository.findByJobApplicationId(applicationId).orElse(null);
        Applicant applicant = application.getApplicant();
        JobPosting jobPosting = application.getJobPosting();
        String email = MessageTargetService.firstNonBlank(info == null ? null : info.getEmail(), applicant.getEmail());
        String name = MessageTargetService.firstNonBlank(info == null ? null : info.getNameKorean(),
                applicant.getUserName(), application.getApplicantNameSnapshot());
        return new SubmittedMail(application, jobPosting, email, name, jobPosting.getTitle(),
                application.getSubmittedAt().format(SUBMITTED_AT_FORMAT));
    }

    private record SubmittedMail(JobApplication application, JobPosting jobPosting, String email, String name,
                                 String jobPostingTitle, String submittedAt) {
    }
}
