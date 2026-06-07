package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentDeleteActorType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "application_attachment",
        indexes = {
                @Index(name = "idx_application_attachment_application", columnList = "job_application_id"),
                @Index(name = "idx_application_attachment_sort", columnList = "job_application_id,sort_order"),
                @Index(name = "idx_application_attachment_type", columnList = "job_application_id,attachment_type"),
                @Index(name = "idx_application_attachment_section", columnList = "job_application_id,section_type")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationAttachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttachmentType attachmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationSectionType sectionType;

    private Long sectionRecordId;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 255)
    private String storedFileName;

    /** 파기 saga 의 물리 삭제 전까지 필요 — <b>최종 소멸(BINARY_DELETED) 시 null</b>(9d-2, 인벤토리 §6). */
    @Column(length = 1000)
    private String storagePath;

    /** 파기 시 원본 파일명 HMAC(인벤토리 §6 신규 — 원문 제거 후 dedup/추적용). */
    @Column(length = 128)
    private String filenameHash;

    /** 물리 소멸 확인 시점(9d-2 saga 최종 단계에서만 세팅). */
    private LocalDateTime binaryDeletedAt;

    /** 바이너리 삭제 실패의 sanitized 코드(09e — reconciliation 추적용, PII 미포함). 성공 시 null. */
    @Column(length = 100)
    private String binaryDeleteFailureCode;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PhysicalFileStatus physicalFileStatus = PhysicalFileStatus.METADATA_ONLY;

    private LocalDateTime deletedAt;

    @Column(length = 255)
    private String deletedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AttachmentDeleteActorType deletedByType;

    @Column(length = 1000)
    private String deletionReason;

    private ApplicationAttachment(
            JobApplication jobApplication,
            AttachmentType attachmentType,
            ApplicationSectionType sectionType,
            Long sectionRecordId,
            String originalFileName,
            String storedFileName,
            String storagePath,
            String contentType,
            Long fileSize,
            Integer sortOrder,
            PhysicalFileStatus physicalFileStatus
    ) {
        this.jobApplication = jobApplication;
        this.attachmentType = attachmentType;
        this.sectionType = sectionType;
        this.sectionRecordId = sectionRecordId;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sortOrder = sortOrder;
        this.physicalFileStatus = physicalFileStatus == null ? PhysicalFileStatus.METADATA_ONLY : physicalFileStatus;
    }

    public static ApplicationAttachment create(
            JobApplication jobApplication,
            AttachmentType attachmentType,
            ApplicationSectionType sectionType,
            Long sectionRecordId,
            String originalFileName,
            String storedFileName,
            String storagePath,
            String contentType,
            Long fileSize,
            Integer sortOrder
    ) {
        return new ApplicationAttachment(
                jobApplication,
                attachmentType,
                sectionType,
                sectionRecordId,
                originalFileName,
                storedFileName,
                storagePath,
                contentType,
                fileSize,
                sortOrder,
                PhysicalFileStatus.METADATA_ONLY
        );
    }

    public static ApplicationAttachment createStored(
            JobApplication jobApplication,
            AttachmentType attachmentType,
            ApplicationSectionType sectionType,
            Long sectionRecordId,
            String originalFileName,
            String storedFileName,
            String storagePath,
            String contentType,
            Long fileSize,
            Integer sortOrder
    ) {
        return new ApplicationAttachment(
                jobApplication,
                attachmentType,
                sectionType,
                sectionRecordId,
                originalFileName,
                storedFileName,
                storagePath,
                contentType,
                fileSize,
                sortOrder,
                PhysicalFileStatus.STORED
        );
    }

    public void markDeleted(
            String deletedBy,
            AttachmentDeleteActorType deletedByType,
            String deletionReason,
            LocalDateTime deletedAt
    ) {
        // 1단계 마이그레이션(인벤토리 §8): 신규 soft-delete 는 SOFT_DELETED 로 기록(legacy DELETED 는 조회 호환만).
        this.physicalFileStatus = PhysicalFileStatus.SOFT_DELETED;
        this.deletedBy = deletedBy;
        this.deletedByType = deletedByType;
        this.deletionReason = deletionReason;
        this.deletedAt = deletedAt;
    }

    // ---- 파기 saga(Phase 09d-2, 설계 §5.4 / 인벤토리 §6) ----

    /**
     * saga ① — 첨부 metadata PII 제거(item 트랜잭션 내). originalFileName 원문은 hash 로 대체 후 placeholder,
     * 삭제 행위자/사유 원문 NULLIFY. storagePath 는 물리 삭제 전까지 보존한다.
     */
    public void purgeMetadataPii(String filenameHash) {
        this.filenameHash = filenameHash;
        this.originalFileName = JobApplication.PURGED_PLACEHOLDER;
        this.deletedBy = null;
        this.deletionReason = null;
    }

    /** saga ① — 물리 삭제 대기 마킹(바이너리 소멸 미확인 상태 전부 대상). */
    public void markBinaryDeletePending() {
        this.physicalFileStatus = PhysicalFileStatus.BINARY_DELETE_PENDING;
    }

    /** saga ③ — 물리 소멸 확인 확정. storagePath 는 이 시점에 null(인벤토리 §6). 이전 실패 코드는 해소. */
    public void markBinaryDeleted(LocalDateTime binaryDeletedAt) {
        this.physicalFileStatus = PhysicalFileStatus.BINARY_DELETED;
        this.binaryDeletedAt = binaryDeletedAt;
        this.storagePath = null;
        this.binaryDeleteFailureCode = null;
    }

    /**
     * saga ③ — 물리 삭제 실패(재시도/reconciliation 대상, 9e). storagePath 보존.
     * {@code failureCode} 는 sanitized 코드(PII 미포함) — reconciliation/health-scan 추적용(09e 리뷰 Low 2).
     */
    public void markBinaryDeleteFailed(String failureCode) {
        this.physicalFileStatus = PhysicalFileStatus.BINARY_DELETE_FAILED;
        this.binaryDeleteFailureCode = failureCode;
    }
}
