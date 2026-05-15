package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationLanguageRepository extends JpaRepository<ApplicationLanguage, Long> {

    List<ApplicationLanguage> findByJobApplicationIdOrderBySortOrderAscIdAsc(Long applicationId);

    List<ApplicationLanguage> findByJobApplicationId(Long applicationId);

    void deleteByJobApplicationId(Long applicationId);
}
