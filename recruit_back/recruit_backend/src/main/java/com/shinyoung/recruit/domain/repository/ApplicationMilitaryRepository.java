package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationMilitaryRepository extends JpaRepository<ApplicationMilitary, Long> {

    Optional<ApplicationMilitary> findByJobApplicationId(Long applicationId);

    List<ApplicationMilitary> findByJobApplicationIdIn(Collection<Long> applicationIds);

    void deleteByJobApplicationId(Long applicationId);

    boolean existsByJobApplicationId(Long applicationId);
}
