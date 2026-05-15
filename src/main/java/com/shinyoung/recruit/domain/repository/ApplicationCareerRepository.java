package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationCareer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationCareerRepository extends JpaRepository<ApplicationCareer, Long> {

    List<ApplicationCareer> findByJobApplicationIdOrderBySortOrderAscIdAsc(Long applicationId);

    List<ApplicationCareer> findByJobApplicationId(Long applicationId);

    void deleteByJobApplicationId(Long applicationId);
}
