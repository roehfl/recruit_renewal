package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.RetentionScheduleSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/** 단일 행 설정이라 id 조회·저장만 쓴다. */
public interface RetentionScheduleSettingRepository extends JpaRepository<RetentionScheduleSetting, Long> {
}
