package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 관계형 PII tombstone bulk 쿼리 모음(Phase 09d-1). <b>PII 인벤토리(phase-09-pii-field-inventory.md)의
 * 필드 분류표가 이 인터페이스의 유일한 계약</b>이다 — 필드 추가/변경 시 인벤토리부터 갱신한다.
 *
 * <p>전용 인터페이스로 응집한 이유: ① {@code createdBy} 는 {@code @Column(updatable=false)} 라 엔티티
 * dirty-update 로 지워지지 않아 JPQL bulk update 가 필수(인벤토리 §9), ② 파기 쿼리를 한 곳에 모아
 * 인벤토리와의 대조 검증을 쉽게 한다. 섹션 엔티티를 영속성 컨텍스트에 올리지 않으므로 stale flush 위험 없음.
 *
 * <p>placeholder = {@code "__PURGED__"}(NOT NULL String PII), 나머지 PII 는 null. KEEP_TOMBSTONE
 * 필드(enum/coded/sortOrder/schoolId/날짜 외 metric)는 건드리지 않는다. 첨부(§6)는 9d-2 saga.
 */
public interface ApplicationPiiPurgeRepository extends Repository<JobApplication, Long> {

    /**
     * BasicInfo — 전 PII 컬럼 null(암호화 컬럼은 placeholder 저장 시 복호화 불가, NULLIFY 전용).
     * 인벤토리 §3-BasicInfo: nameKorean/nameEnglish/email/mobilePhone/emergencyPhone/birthDate/
     * nationalityType/countryCode/veteranStatus/veteranType/disabilityStatus/disabilityGradeCode/disabilityTypeCode/
     * zipCode/addressBasic/addressDetail + audit(createdBy/updatedBy) — KEEP_TOMBSTONE 없음.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationBasicInfo b
            set b.nameKorean = null, b.nameEnglish = null, b.email = null,
                b.mobilePhone = null, b.emergencyPhone = null,
                b.birthDate = null, b.nationalityType = null, b.countryCode = null,
                b.veteranStatus = null, b.veteranType = null, b.disabilityStatus = null,
                b.disabilityGradeCode = null, b.disabilityTypeCode = null,
                b.zipCode = null, b.addressBasic = null, b.addressDetail = null,
                b.createdBy = null, b.updatedBy = null
            where b.jobApplication.id = :applicationId""")
    int purgeBasicInfo(@Param("applicationId") Long applicationId);

    /** ref-count 판정용 — 해당 지원자의 전체 지원서. */
    @Query("select j from JobApplication j where j.applicant.id = :applicantId")
    List<JobApplication> findByApplicantId(@Param("applicantId") Long applicantId);

    /**
     * 바이너리 소멸 미확인 첨부 수(PURGED 승격 금지 판정). STORED 뿐 아니라 <b>soft-delete(DELETED)도 포함</b> —
     * soft-delete 의 after-commit 물리삭제는 실패해도 status 가 DELETED 로 남으므로(파일 잔존 가능),
     * 소멸이 확인되지 않은 상태는 전부 outstanding 으로 본다("DB PURGED + 파일 잔존" 불허 — 9d-1 적대 검증 반영).
     * METADATA_ONLY(파일 미업로드)/MISSING(부재 확인됨)은 제외. 최종 소멸 확인은 9d-2 saga.
     */
    @Query("""
            select count(a) from ApplicationAttachment a
            where a.jobApplication.id = :applicationId and a.physicalFileStatus in :statuses""")
    long countAttachmentsWithStatus(
            @Param("applicationId") Long applicationId,
            @Param("statuses") List<PhysicalFileStatus> statuses);

    /** JobApplication 자체(인벤토리 §3): snapshot 은 엔티티 의미 메서드(markPurge*)가 처리, 여기선 감사 필드만. */
    @Modifying(flushAutomatically = true)
    @Query("update JobApplication j set j.createdBy = null, j.updatedBy = null where j.id = :applicationId")
    int purgeJobApplicationAuditFields(@Param("applicationId") Long applicationId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationAnswer a
            set a.answerText = null, a.createdBy = null, a.updatedBy = null
            where a.jobApplication.id = :applicationId""")
    int purgeAnswers(@Param("applicationId") Long applicationId);

    /** additionalMajorType 은 코드값이라 KEEP_TOMBSTONE(건드리지 않음). 자유텍스트 additionalMajorName/thesisTitle 만 NULLIFY. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationEducation e
            set e.schoolName = '__PURGED__', e.majorName = null,
                e.additionalMajorName = null, e.thesisTitle = null, e.countryCode = null,
                e.admissionDate = null, e.graduationDate = null, e.createdBy = null, e.updatedBy = null
            where e.jobApplication.id = :applicationId""")
    int purgeEducations(@Param("applicationId") Long applicationId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationEducationSemesterGrade g
            set g.createdBy = null, g.updatedBy = null
            where g.education.id in (
                select e.id from ApplicationEducation e where e.jobApplication.id = :applicationId)""")
    int purgeEducationSemesterGrades(@Param("applicationId") Long applicationId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationCareer c
            set c.companyName = '__PURGED__', c.departmentName = null, c.positionTitle = null,
                c.currentSalary = null, c.resignationReason = null, c.startDate = null, c.endDate = null,
                c.promotionDate = null,
                c.createdBy = null, c.updatedBy = null
            where c.jobApplication.id = :applicationId""")
    int purgeCareers(@Param("applicationId") Long applicationId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationCertificate c
            set c.certificateName = '__PURGED__', c.issuingOrganization = '__PURGED__', c.acquiredDate = null,
                c.expiredDate = null, c.scoreOrGrade = null, c.createdBy = null, c.updatedBy = null
            where c.jobApplication.id = :applicationId""")
    int purgeCertificates(@Param("applicationId") Long applicationId);

    /** certificateNumber HASH_ONLY 처리용 원문 조회(엔티티 비로딩 — scalar projection). */
    @Query("""
            select c.id, c.certificateNumber from ApplicationCertificate c
            where c.jobApplication.id = :applicationId and c.certificateNumber is not null""")
    List<Object[]> findCertificateNumbers(@Param("applicationId") Long applicationId);

    @Modifying(flushAutomatically = true)
    @Query("update ApplicationCertificate c set c.certificateNumber = :hash where c.id = :certificateId")
    int overwriteCertificateNumber(@Param("certificateId") Long certificateId, @Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationLanguage l
            set l.languageName = '__PURGED__', l.testName = '__PURGED__', l.examDate = null,
                l.scoreOrGrade = null, l.conversationalAbility = null,
                l.expiredDate = null, l.issuingOrganization = null,
                l.createdBy = null, l.updatedBy = null
            where l.jobApplication.id = :applicationId""")
    int purgeLanguages(@Param("applicationId") Long applicationId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationMilitary m
            set m.serviceStartDate = null, m.serviceEndDate = null, m.exemptionReason = null,
                m.createdBy = null, m.updatedBy = null
            where m.jobApplication.id = :applicationId""")
    int purgeMilitary(@Param("applicationId") Long applicationId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationAward a
            set a.awardName = '__PURGED__', a.awardingOrganization = '__PURGED__', a.awardDate = null,
                a.description = null, a.createdBy = null, a.updatedBy = null
            where a.jobApplication.id = :applicationId""")
    int purgeAwards(@Param("applicationId") Long applicationId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationGapPeriod g
            set g.reason = '__PURGED__', g.startDate = null, g.endDate = null, g.description = null,
                g.createdBy = null, g.updatedBy = null
            where g.jobApplication.id = :applicationId""")
    int purgeGapPeriods(@Param("applicationId") Long applicationId);

    /**
     * per-candidate 평가 자유서술만(인벤토리 §7). Interview-level 자유텍스트(memo/location 등)는
     * group 공유 행이라 건드리지 않는다(타 후보 영향 — 운영 가이드로 커버).
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update InterviewEvaluation ev
            set ev.comment = null
            where ev.candidateParticipant.id in (
                select p.id from InterviewParticipant p where p.jobApplication.id = :applicationId)""")
    int purgeEvaluationComments(@Param("applicationId") Long applicationId);

    /** 첨부 metadata 감사 필드(인벤토리 §6 공통 — createdBy 는 updatable=false 라 bulk 필수, 9d-2). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update ApplicationAttachment a
            set a.createdBy = null, a.updatedBy = null
            where a.jobApplication.id = :applicationId""")
    int purgeAttachmentAuditFields(@Param("applicationId") Long applicationId);

    /** saga ② 물리 삭제 대상(id, storagePath) — 엔티티 비로딩 scalar(9d-2). FAILED 는 재실행 재시도 포함. */
    @Query("""
            select a.id, a.storagePath from ApplicationAttachment a
            where a.jobApplication.id = :applicationId and a.physicalFileStatus in :statuses""")
    List<Object[]> findBinaryDeleteTargets(
            @Param("applicationId") Long applicationId,
            @Param("statuses") List<PhysicalFileStatus> statuses);

    /**
     * StageResult 자유서술(인벤토리 §7-1, 9d-1 리뷰 Major 1 — PURGED marker 전 소거 필수).
     * 결과/점수/decidedBy(직원)는 KEEP_TOMBSTONE.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update StageResult r
            set r.comment = null, r.createdBy = null, r.updatedBy = null
            where r.jobApplication.id = :applicationId""")
    int purgeStageResultComments(@Param("applicationId") Long applicationId);

    /**
     * 정정 이력의 자유입력(reason NOT NULL → placeholder)과 comment 전후 스냅샷(인벤토리 §7-1).
     * 상태/점수 스냅샷·correctedBy(직원)는 KEEP_TOMBSTONE — 정정 사실 자체는 증적으로 보존.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update StageResultCorrectionHistory h
            set h.reason = '__PURGED__', h.previousComment = null, h.newComment = null,
                h.createdBy = null, h.updatedBy = null
            where h.stageResult.jobApplication.id = :applicationId""")
    int purgeStageResultCorrectionHistories(@Param("applicationId") Long applicationId);
}
