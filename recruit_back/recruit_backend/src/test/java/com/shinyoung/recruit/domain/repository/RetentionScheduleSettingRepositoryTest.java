package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.RetentionScheduleSetting;
import com.shinyoung.recruit.enumeration.RetentionScheduleRunResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RetentionScheduleSettingRepositoryTest {

    @Autowired
    private RetentionScheduleSettingRepository retentionScheduleSettingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("단일 행 설정을 저장하고 다시 읽는다")
    void saveAndFind() {
        RetentionScheduleSetting setting = RetentionScheduleSetting.initial();
        setting.updateEnabled(true, "tester", LocalDateTime.now());

        retentionScheduleSettingRepository.save(setting);
        entityManager.flush();
        entityManager.clear();

        RetentionScheduleSetting found = retentionScheduleSettingRepository
                .findById(RetentionScheduleSetting.SINGLETON_ID).orElseThrow();
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getUpdatedBy()).isEqualTo("tester");
    }

    @Test
    @DisplayName("실행 결과를 기록한다")
    void recordRun() {
        RetentionScheduleSetting setting =
                retentionScheduleSettingRepository.save(RetentionScheduleSetting.initial());

        setting.recordRun(RetentionScheduleRunResult.SKIPPED_NOT_DUE, null, LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        RetentionScheduleSetting found = retentionScheduleSettingRepository
                .findById(RetentionScheduleSetting.SINGLETON_ID).orElseThrow();
        assertThat(found.getLastRunResult()).isEqualTo(RetentionScheduleRunResult.SKIPPED_NOT_DUE);
        assertThat(found.getLastRunBatchId()).isNull();
        assertThat(found.getLastRunAt()).isNotNull();
    }

    @Test
    @DisplayName("initial() 은 꺼진 상태다 — 안전 기본값")
    void initialIsDisabled() {
        assertThat(RetentionScheduleSetting.initial().isEnabled()).isFalse();
    }
}
