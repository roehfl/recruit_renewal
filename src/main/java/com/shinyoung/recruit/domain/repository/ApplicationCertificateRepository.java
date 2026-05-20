package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationCertificateRepository extends JpaRepository<ApplicationCertificate, Long> {

    List<ApplicationCertificate> findByJobApplicationIdOrderBySortOrderAscIdAsc(Long applicationId);

    List<ApplicationCertificate> findByJobApplicationId(Long applicationId);

    boolean existsByJobApplicationId(Long applicationId);

    void deleteByJobApplicationId(Long applicationId);
}
