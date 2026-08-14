package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationFormPageItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationFormPageItemRepository extends JpaRepository<ApplicationFormPageItem, Long> {

    List<ApplicationFormPageItem> findByPageIdOrderBySortOrderAscIdAsc(Long pageId);
}
