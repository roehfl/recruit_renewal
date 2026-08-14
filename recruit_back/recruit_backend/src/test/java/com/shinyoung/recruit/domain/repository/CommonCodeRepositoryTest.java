package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.CommonCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CommonCodeRepositoryTest {

    @Autowired
    private CommonCodeRepository commonCodeRepository;

    @Test
    void existsByGroupCodeAndCodeAndActiveTrue_only_matches_active_codes() {
        commonCodeRepository.save(CommonCode.create("NATIONALITY", "US", "United States", 1, true, null));
        commonCodeRepository.save(CommonCode.create("NATIONALITY", "JP", "Japan", 2, false, null));

        assertThat(commonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue("NATIONALITY", "US")).isTrue();
        assertThat(commonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue("NATIONALITY", "JP")).isFalse();
        assertThat(commonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue("NATIONALITY", "ZZ")).isFalse();
    }
}
