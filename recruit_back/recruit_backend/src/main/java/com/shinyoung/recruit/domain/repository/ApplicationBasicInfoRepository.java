package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationBasicInfoRepository extends JpaRepository<ApplicationBasicInfo, Long> {

    Optional<ApplicationBasicInfo> findByJobApplicationId(Long applicationId);

    boolean existsByJobApplicationId(Long applicationId);

    List<ApplicationBasicInfo> findByJobApplicationIdIn(Collection<Long> applicationIds);
}
