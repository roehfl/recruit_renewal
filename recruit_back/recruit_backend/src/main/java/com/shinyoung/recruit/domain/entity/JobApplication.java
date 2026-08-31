package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.PurgeResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "job_application",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_job_application_applicant_job_posting",
                        columnNames = {"applicant_id", "job_posting_id"}
                )
        },
        indexes = {
                @Index(name = "idx_job_application_applicant", columnList = "applicant_id"),
                @Index(name = "idx_job_application_job_posting", columnList = "job_posting_id"),
                @Index(name = "idx_job_application_job_position", columnList = "job_position_id"),
                @Index(name = "idx_job_application_status", columnList = "status"),
                @Index(name = "idx_job_application_job_posting_status", columnList = "job_posting_id,status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_position_id", nullable = false)
    private JobPosition jobPosition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobApplicationStatus status;

    private LocalDateTime submittedAt;

    private LocalDateTime withdrawnAt;

    @Column(nullable = false)
    private String applicantNameSnapshot;

    @Column(nullable = false)
    private String jobPostingTitleSnapshot;

    @Column(nullable = false)
    private String jobPositionNameSnapshot;

    /** 지원자가 선택한 근무지(CommonCode 그룹 {@code WORK_LOCATION}). 후보가 없는 모집분야는 null. */
    @Column(name = "work_location_code", length = 100)
    private String workLocationCode;

    /** 선택 시점의 근무지 표시명 스냅샷({@code jobPositionNameSnapshot}과 동일 규약). */
    @Column(name = "work_location_name_snapshot", length = 200)
    private String workLocationNameSnapshot;

    /**
     * 파기 marker(Phase 09c 컬럼 도입 — 쓰기는 09d-1, 09c 는 eligibility 의 ALREADY_PURGED 판정에 읽기만).
     * {@code JobApplicationStatus} 와 직교(orthogonal) — 기존 status enum 불변(설계 §7.2).
     */
    @Column(name = "purge_batch_id")
    private Long purgeBatchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purge_result", length = 30)
    private PurgeResult purgeResult;

    /** 최종 PURGED 확정 시점에만 세팅(설계 §5.4 — 바이너리 소멸 확인 전 세팅 금지). */
    @Column(name = "purged_at")
    private LocalDateTime purgedAt;

    private JobApplication(
            Applicant applicant,
            JobPosting jobPosting,
            JobPosition jobPosition,
            String applicantNameSnapshot,
            String jobPostingTitleSnapshot,
            String jobPositionNameSnapshot
    ) {
        this.applicant = applicant;
        this.jobPosting = jobPosting;
        this.jobPosition = jobPosition;
        this.status = JobApplicationStatus.DRAFT;
        this.applicantNameSnapshot = applicantNameSnapshot;
        this.jobPostingTitleSnapshot = jobPostingTitleSnapshot;
        this.jobPositionNameSnapshot = jobPositionNameSnapshot;
    }

    public static JobApplication create(
            Applicant applicant,
            JobPosting jobPosting,
            JobPosition jobPosition,
            String applicantNameSnapshot,
            String jobPostingTitleSnapshot,
            String jobPositionNameSnapshot
    ) {
        return new JobApplication(
                applicant,
                jobPosting,
                jobPosition,
                applicantNameSnapshot,
                jobPostingTitleSnapshot,
                jobPositionNameSnapshot
        );
    }

    public void updateDraft(JobPosition jobPosition, String jobPositionNameSnapshot) {
        this.jobPosition = jobPosition;
        this.jobPositionNameSnapshot = jobPositionNameSnapshot;
    }

    /** 선택 근무지 세팅. 후보가 없는 모집분야면 {@code null, null}로 비운다. */
    public void updateWorkLocation(String workLocationCode, String workLocationNameSnapshot) {
        this.workLocationCode = workLocationCode;
        this.workLocationNameSnapshot = workLocationNameSnapshot;
    }

    public void submit(LocalDateTime submittedAt) {
        this.status = JobApplicationStatus.SUBMITTED;
        this.submittedAt = submittedAt;
    }

    public void withdraw(LocalDateTime withdrawnAt) {
        this.status = JobApplicationStatus.WITHDRAWN;
        this.withdrawnAt = withdrawnAt;
    }

    /** 파기 비식별 치환값(PII 인벤토리 §1 PLACEHOLDER). */
    public static final String PURGED_PLACEHOLDER = "__PURGED__";

    /**
     * 관계형 PII 제거 완료 + 첨부 바이너리 삭제 대기(9d-1, 설계 §5.4 saga ①).
     * purgedAt 은 세팅하지 않는다 — 최종 PURGED 는 바이너리 소멸 확인 후(9d-2)에만.
     */
    public void markPurgePending(Long purgeBatchId) {
        this.purgeBatchId = purgeBatchId;
        this.purgeResult = PurgeResult.PURGE_PENDING;
        this.applicantNameSnapshot = PURGED_PLACEHOLDER;
    }

    /** 최종 PURGED 확정(관계형 PII 제거 + 바이너리 소멸 확인 완료). purgedAt 은 이 시점에만 세팅. */
    public void markPurged(Long purgeBatchId, LocalDateTime purgedAt) {
        this.purgeBatchId = purgeBatchId;
        this.purgeResult = PurgeResult.PURGED;
        this.purgedAt = purgedAt;
        this.applicantNameSnapshot = PURGED_PLACEHOLDER;
    }
}
