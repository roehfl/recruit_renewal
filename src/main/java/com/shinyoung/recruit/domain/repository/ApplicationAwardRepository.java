package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationAward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationAwardRepository extends JpaRepository<ApplicationAward, Long> {

    List<ApplicationAward> findByJobApplicationIdOrderBySortOrderAscIdAsc(Long applicationId);

    List<ApplicationAward> findByJobApplicationId(Long applicationId);

    boolean existsByJobApplicationId(Long applicationId);

    void deleteByJobApplicationId(Long applicationId);
}
