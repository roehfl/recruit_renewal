package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationGapPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationGapPeriodRepository extends JpaRepository<ApplicationGapPeriod, Long> {

    List<ApplicationGapPeriod> findByJobApplicationIdOrderBySortOrderAscIdAsc(Long applicationId);

    List<ApplicationGapPeriod> findByJobApplicationId(Long applicationId);

    boolean existsByJobApplicationId(Long applicationId);

    void deleteByJobApplicationId(Long applicationId);
}
