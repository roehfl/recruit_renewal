package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationCareerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicationCareerProfileRepository extends JpaRepository<ApplicationCareerProfile, Long> {

    Optional<ApplicationCareerProfile> findByJobApplicationId(Long applicationId);

    void deleteByJobApplicationId(Long applicationId);

    boolean existsByJobApplicationId(Long applicationId);
}
