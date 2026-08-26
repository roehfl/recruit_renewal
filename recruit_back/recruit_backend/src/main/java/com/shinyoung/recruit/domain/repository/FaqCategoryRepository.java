package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.FaqCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FaqCategoryRepository extends JpaRepository<FaqCategory, Long> {

    List<FaqCategory> findAllByOrderBySortOrderAscIdAsc();

    List<FaqCategory> findByActiveTrueOrderBySortOrderAscIdAsc();

    boolean existsByName(String name);

    Optional<FaqCategory> findByName(String name);
}
