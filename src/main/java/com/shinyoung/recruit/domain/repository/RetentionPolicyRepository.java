package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, Long> {

    List<RetentionPolicy> findAllByOrderByIdAsc();

    /** 공고 override 정책(enabled 만). effective window 평가는 서비스에서 scanAt 기준으로 한다. */
    List<RetentionPolicy> findByJobPostingIdAndEnabledTrue(Long jobPostingId);

    /** 전역 기본 정책(enabled 만). */
    List<RetentionPolicy> findByJobPostingIdIsNullAndEnabledTrue();
}
