package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.enumeration.AttachmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationAttachmentRepository extends JpaRepository<ApplicationAttachment, Long> {

    List<ApplicationAttachment> findByJobApplicationIdOrderBySortOrderAscIdAsc(Long applicationId);

    List<ApplicationAttachment> findByJobApplicationId(Long applicationId);

    void deleteByJobApplicationId(Long applicationId);

    boolean existsByJobApplicationIdAndAttachmentType(Long applicationId, AttachmentType attachmentType);
}
