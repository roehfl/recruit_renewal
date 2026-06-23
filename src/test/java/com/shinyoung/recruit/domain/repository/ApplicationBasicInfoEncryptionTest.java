package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.common.crypto.CryptoHolder;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.CryptoConfig;
import com.shinyoung.recruit.config.JpaConfig;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CryptoConfig.class, JpaConfig.class, CryptoHolder.class})
@TestPropertySource(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationBasicInfoEncryptionTest {

    @Autowired private ApplicationBasicInfoRepository basicInfoRepository;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void string_pii_is_stored_as_ciphertext_and_decrypted_on_read() {
        JobApplication application = newApplication();

        ApplicationBasicInfo saved = basicInfoRepository.save(ApplicationBasicInfo.create(
                application, "홍길동", "Hong Gildong", NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01012345678", null, "test@example.com",
                VeteranStatus.NOT_SUBJECT, null, DisabilityStatus.NOT_SUBJECT, null, null,
                "06236", "서울시 강남구", "101동 1001호"));
        entityManager.flush();
        entityManager.clear();

        // read: 복호화된 평문
        ApplicationBasicInfo reloaded = basicInfoRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getNameKorean()).isEqualTo("홍길동");
        assertThat(reloaded.getEmail()).isEqualTo("test@example.com");
        assertThat(reloaded.getMobilePhone()).isEqualTo("01012345678");

        // raw 컬럼: 암호문(평문과 다름)
        Object rawName = entityManager.createNativeQuery(
                        "select name_korean from application_basic_info where id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();
        assertThat(rawName).isNotNull();
        assertThat(rawName.toString()).isNotEqualTo("홍길동");
    }

    private JobApplication newApplication() {
        Applicant applicant = new Applicant("enc-ci", HashUtil.sha256("enc-ci"));
        applicant.setLoginId("enc-applicant");
        applicant.setName("User-Enc");
        applicant.setUserName("Enc User");
        applicant.setPassword("encoded");
        applicant.setPhoneNumber("01000000000");
        applicant = applicantRepository.save(applicant);

        JobPosting posting = JobPosting.create(
                "2026 recruitment",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0));
        JobPosition position = JobPosition.create("Backend", 0);
        posting.replaceJobPositions(List.of(position));
        posting = jobPostingRepository.save(posting);
        posting.publish(LocalDateTime.now());

        JobPosition savedPosition = posting.getJobPositions().get(0);

        JobApplication application = JobApplication.create(
                applicant,
                posting,
                savedPosition,
                applicant.getName(),
                posting.getTitle(),
                savedPosition.getPositionName());
        return jobApplicationRepository.save(application);
    }
}
