package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.Menu;
import com.shinyoung.recruit.enumeration.MenuSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    List<Menu> findAllBySiteOrderBySortOrderAscIdAsc(MenuSite site);

    Optional<Menu> findBySiteAndPath(MenuSite site, String path);
}
