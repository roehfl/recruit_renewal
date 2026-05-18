package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.QuestionTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionTemplateRepository extends JpaRepository<QuestionTemplate, Long> {

    Page<QuestionTemplate> findByActive(Boolean active, Pageable pageable);

    boolean existsByIdAndActiveTrue(Long id);
}
