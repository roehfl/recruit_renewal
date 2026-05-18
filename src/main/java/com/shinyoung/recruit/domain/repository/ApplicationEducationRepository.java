package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationEducationRepository extends JpaRepository<ApplicationEducation, Long> {

    List<ApplicationEducation> findByJobApplicationIdOrderBySortOrderAscIdAsc(Long applicationId);

    List<ApplicationEducation> findByJobApplicationId(Long applicationId);

    boolean existsByJobApplicationId(Long applicationId);

    void deleteByJobApplicationId(Long applicationId);
}
