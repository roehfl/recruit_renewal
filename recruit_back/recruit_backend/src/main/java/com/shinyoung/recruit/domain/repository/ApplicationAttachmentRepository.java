package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationAttachmentRepository extends JpaRepository<ApplicationAttachment, Long> {

    List<ApplicationAttachment> findByJobApplicationIdOrderBySortOrderAscIdAsc(Long applicationId);

    List<ApplicationAttachment> findByJobApplicationIdAndPhysicalFileStatusNotOrderBySortOrderAscIdAsc(
            Long applicationId,
            PhysicalFileStatus physicalFileStatus
    );

    /** soft-deleted(DELETED·SOFT_DELETED) + purge lifecycle 상태를 한 번에 제외하는 목록 조회(9d-2). */
    List<ApplicationAttachment> findByJobApplicationIdAndPhysicalFileStatusNotInOrderBySortOrderAscIdAsc(
            Long applicationId,
            Collection<PhysicalFileStatus> physicalFileStatuses
    );

    /** 파기 saga ③ 확정 대상 로드(9d-2). */
    List<ApplicationAttachment> findByJobApplicationIdAndPhysicalFileStatusIn(
            Long applicationId,
            Collection<PhysicalFileStatus> physicalFileStatuses
    );

    List<ApplicationAttachment> findByJobApplicationIdAndPhysicalFileStatus(
            Long applicationId,
            PhysicalFileStatus physicalFileStatus
    );

    List<ApplicationAttachment> findByJobApplicationId(Long applicationId);

    /** 관리자 지원현황 목록 enrichment — 다운로드 가능한(STORED, 미삭제) 특정 타입 첨부 배치 조회. */
    List<ApplicationAttachment> findByJobApplicationIdInAndAttachmentTypeAndPhysicalFileStatusAndDeletedAtIsNull(
            Collection<Long> applicationIds,
            AttachmentType attachmentType,
            PhysicalFileStatus physicalFileStatus
    );

    List<ApplicationAttachment> findByPhysicalFileStatusIn(Collection<PhysicalFileStatus> physicalFileStatuses);

    Optional<ApplicationAttachment> findByIdAndJobApplicationId(Long attachmentId, Long jobApplicationId);

    Optional<ApplicationAttachment> findByIdAndJobApplicationIdAndPhysicalFileStatus(
            Long attachmentId,
            Long jobApplicationId,
            PhysicalFileStatus physicalFileStatus
    );

    Optional<ApplicationAttachment> findByIdAndJobApplicationIdAndPhysicalFileStatusNot(
            Long attachmentId,
            Long jobApplicationId,
            PhysicalFileStatus physicalFileStatus
    );

    Optional<ApplicationAttachment> findByIdAndJobApplicationIdAndPhysicalFileStatusNotIn(
            Long attachmentId,
            Long jobApplicationId,
            Collection<PhysicalFileStatus> physicalFileStatuses
    );

    void deleteByJobApplicationId(Long applicationId);

    void deleteByJobApplicationIdAndPhysicalFileStatus(Long applicationId, PhysicalFileStatus physicalFileStatus);

    long countByJobApplicationIdAndPhysicalFileStatus(Long applicationId, PhysicalFileStatus physicalFileStatus);

    @Query("""
            select a.sortOrder
            from ApplicationAttachment a
            where a.jobApplication.id = :applicationId
              and a.physicalFileStatus = :physicalFileStatus
            """)
    List<Integer> findSortOrdersByJobApplicationIdAndPhysicalFileStatus(
            @Param("applicationId") Long applicationId,
            @Param("physicalFileStatus") PhysicalFileStatus physicalFileStatus
    );

    @Query("""
            select coalesce(sum(a.fileSize), 0)
            from ApplicationAttachment a
            where a.jobApplication.id = :applicationId
              and a.physicalFileStatus = :physicalFileStatus
            """)
    Long sumFileSizeByJobApplicationIdAndPhysicalFileStatus(
            @Param("applicationId") Long applicationId,
            @Param("physicalFileStatus") PhysicalFileStatus physicalFileStatus
    );

    @Query("""
            select max(a.sortOrder)
            from ApplicationAttachment a
            where a.jobApplication.id = :applicationId
            """)
    Integer findMaxSortOrderByJobApplicationId(@Param("applicationId") Long applicationId);

    boolean existsByJobApplicationIdAndAttachmentType(Long applicationId, AttachmentType attachmentType);
}
