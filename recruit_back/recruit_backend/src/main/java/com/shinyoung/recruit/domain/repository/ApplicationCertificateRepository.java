package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.dto.response.FunnelCertificateRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApplicationCertificateRepository extends JpaRepository<ApplicationCertificate, Long> {

    List<ApplicationCertificate> findByJobApplicationIdOrderBySortOrderAscIdAsc(Long applicationId);

    List<ApplicationCertificate> findByJobApplicationId(Long applicationId);

    boolean existsByJobApplicationId(Long applicationId);

    void deleteByJobApplicationId(Long applicationId);

    /**
     * CERTIFICATE funnel dimension 입력: 공고의 제출 이력(submittedAt != null) 코호트 자격들(applicationId/certificateName).
     * 자격명 정규화·distinct 집계는 service 가 in-memory 로 수행한다.
     */
    @Query("""
            select new com.shinyoung.recruit.dto.response.FunnelCertificateRow(
                c.jobApplication.id, c.certificateName)
            from ApplicationCertificate c
            where c.jobApplication.jobPosting.id = :jobPostingId
              and c.jobApplication.submittedAt is not null
            """)
    List<FunnelCertificateRow> findFunnelCertificates(@Param("jobPostingId") Long jobPostingId);
}
