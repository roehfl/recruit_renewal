package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.common.crypto.CryptoHolder;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.CryptoConfig;
import com.shinyoung.recruit.config.JpaConfig;
import com.shinyoung.recruit.domain.entity.Applicant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CryptoConfig.class, JpaConfig.class, CryptoHolder.class})
public class ApplicantRepositoryTest {

    @Autowired
    ApplicantRepository applicantRepository;

    @Test
    void 저장_조회_엔티티검증() {
        String ciValue = "testCIValue";
        Applicant applicant = new Applicant(ciValue, HashUtil.sha256(ciValue));
        applicant.setEmail("roehfl@gmail.com");
        applicant.setUserName("이솔");
        applicant.setPhoneNumber("01071939211");

        applicantRepository.save(applicant);

        Applicant found = applicantRepository.findApplicantByCiHash(HashUtil.sha256(ciValue)).get();

        assertThat(applicant).isEqualTo(found);
    }

}
