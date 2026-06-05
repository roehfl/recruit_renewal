package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.repository.ApplicationPiiPurgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관계형 PII tombstone 실행(Phase 09d-1). 호출자(item 트랜잭션)에 join 한다(REQUIRED — application 1건
 * all-or-nothing 의 일부, 설계 §5.4). 처리 범위는 PII 인벤토리 §3~§7 의 분류표 그대로:
 * answers/학력(+semesterGrade 감사필드)/경력(+profile)/자격(번호는 HMAC HASH_ONLY)/어학/병역/수상/공백/평가 comment.
 * 첨부(§6)는 9d-2 saga, Applicant 공통 PII(§2)는 ref-count 게이트(호출자 책임).
 */
@Service
@RequiredArgsConstructor
public class ApplicationPiiPurgeService {

    private final ApplicationPiiPurgeRepository purgeRepository;
    private final AuditHmac auditHmac;

    @Transactional(propagation = Propagation.REQUIRED)
    public void purgeRelationalPii(Long applicationId) {
        purgeRepository.purgeAnswers(applicationId);
        purgeRepository.purgeEducations(applicationId);
        purgeRepository.purgeEducationSemesterGrades(applicationId);
        purgeRepository.purgeCareers(applicationId);
        purgeRepository.purgeCareerProfile(applicationId);

        // certificateNumber = HASH_ONLY(인벤토리 §5) — 원문을 HMAC 으로 덮어써 dedup 능력만 보존.
        List<Object[]> certificateNumbers = purgeRepository.findCertificateNumbers(applicationId);
        purgeRepository.purgeCertificates(applicationId);
        for (Object[] row : certificateNumbers) {
            Long certificateId = (Long) row[0];
            String number = (String) row[1];
            purgeRepository.overwriteCertificateNumber(certificateId, auditHmac.hmacHex("CERT_NO:" + number));
        }

        purgeRepository.purgeLanguages(applicationId);
        purgeRepository.purgeMilitary(applicationId);
        purgeRepository.purgeAwards(applicationId);
        purgeRepository.purgeGapPeriods(applicationId);
        purgeRepository.purgeEvaluationComments(applicationId);
        // StageResult 자유서술 + 정정 이력 comment 계열(9d-1 리뷰 Major 1) — PURGED marker 전 필수 소거.
        purgeRepository.purgeStageResultComments(applicationId);
        purgeRepository.purgeStageResultCorrectionHistories(applicationId);
        purgeRepository.purgeJobApplicationAuditFields(applicationId);
    }
}
