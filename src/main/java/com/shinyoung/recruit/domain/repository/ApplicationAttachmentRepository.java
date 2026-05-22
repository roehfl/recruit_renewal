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

    List<ApplicationAttachment> findByJobApplicationId(Long applicationId);

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
