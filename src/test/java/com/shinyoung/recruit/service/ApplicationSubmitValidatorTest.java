package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationCareerProfile;
import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicationCareerProfileRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.enumeration.CareerType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationSubmitValidatorTest {

    private static final long APPLICATION_ID = 1L;

    @Mock
    private ApplicationEducationRepository educationRepository;

    @Mock
    private ApplicationCareerProfileRepository careerProfileRepository;

    @Mock
    private ApplicationCareerRepository careerRepository;

    @Mock
    private ApplicationMilitaryRepository militaryRepository;

    @InjectMocks
    private ApplicationSubmitValidator validator;

    @Test
    void education_required_fails_when_education_is_missing() {
        ApplicationFormConfig config = config();
        when(config.isUseEducation()).thenReturn(true);
        when(educationRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(false);

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void education_required_passes_when_education_exists() {
        ApplicationFormConfig config = config();
        when(config.isUseEducation()).thenReturn(true);
        when(educationRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(true);

        assertThatCode(() -> validator.validate(application(config)))
                .doesNotThrowAnyException();
    }

    @Test
    void education_disabled_passes_without_education() {
        assertThatCode(() -> validator.validate(application(config())))
                .doesNotThrowAnyException();
    }

    @Test
    void career_required_fails_when_profile_is_missing() {
        ApplicationFormConfig config = config();
        when(config.isUseCareer()).thenReturn(true);
        when(careerProfileRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void career_required_fails_when_career_type_is_not_selected() {
        ApplicationFormConfig config = config();
        when(config.isUseCareer()).thenReturn(true);
        when(careerProfileRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(profile(CareerType.NOT_SELECTED)));

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void experienced_career_fails_when_career_rows_are_missing() {
        ApplicationFormConfig config = config();
        when(config.isUseCareer()).thenReturn(true);
        when(careerProfileRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(profile(CareerType.EXPERIENCED)));
        when(careerRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(false);

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void experienced_career_passes_when_career_rows_exist() {
        ApplicationFormConfig config = config();
        when(config.isUseCareer()).thenReturn(true);
        when(careerProfileRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(profile(CareerType.EXPERIENCED)));
        when(careerRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(true);

        assertThatCode(() -> validator.validate(application(config)))
                .doesNotThrowAnyException();
    }

    @Test
    void newcomer_career_passes_without_career_rows() {
        ApplicationFormConfig config = config();
        when(config.isUseCareer()).thenReturn(true);
        when(careerProfileRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(profile(CareerType.NEWCOMER)));
        when(careerRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(false);

        assertThatCode(() -> validator.validate(application(config)))
                .doesNotThrowAnyException();
    }

    @Test
    void not_applicable_career_passes_without_career_rows() {
        ApplicationFormConfig config = config();
        when(config.isUseCareer()).thenReturn(true);
        when(careerProfileRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(profile(CareerType.NOT_APPLICABLE)));
        when(careerRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(false);

        assertThatCode(() -> validator.validate(application(config)))
                .doesNotThrowAnyException();
    }

    @Test
    void newcomer_or_not_applicable_career_fails_when_career_rows_exist() {
        ApplicationFormConfig config = config();
        when(config.isUseCareer()).thenReturn(true);
        when(careerProfileRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(profile(CareerType.NEWCOMER)));
        when(careerRepository.existsByJobApplicationId(APPLICATION_ID)).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void career_disabled_passes_without_profile() {
        assertThatCode(() -> validator.validate(application(config())))
                .doesNotThrowAnyException();
    }

    @Test
    void military_required_fails_when_record_is_missing() {
        ApplicationFormConfig config = config();
        when(config.isUseMilitary()).thenReturn(true);
        when(militaryRepository.findByJobApplicationId(APPLICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void military_subject_types_pass_when_record_exists() {
        assertMilitaryPasses(MilitarySubjectType.SUBJECT, null, null, null);
        assertMilitaryPasses(MilitarySubjectType.NOT_SUBJECT, null, null, null);
        assertMilitaryPasses(MilitarySubjectType.NOT_APPLICABLE, null, null, null);
    }

    @Test
    void completed_military_fails_without_service_period() {
        ApplicationFormConfig config = config();
        when(config.isUseMilitary()).thenReturn(true);
        when(militaryRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(military(MilitarySubjectType.COMPLETED, null, null, null)));

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void completed_military_passes_with_service_period() {
        assertMilitaryPasses(
                MilitarySubjectType.COMPLETED,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2021, 6, 30),
                null
        );
    }

    @Test
    void exempted_military_fails_without_exemption_reason() {
        ApplicationFormConfig config = config();
        when(config.isUseMilitary()).thenReturn(true);
        when(militaryRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(military(MilitarySubjectType.EXEMPTED, null, null, " ")));

        assertThatThrownBy(() -> validator.validate(application(config)))
                .isInstanceOf(InvalidJobApplicationException.class);
    }

    @Test
    void exempted_military_passes_with_exemption_reason() {
        assertMilitaryPasses(MilitarySubjectType.EXEMPTED, null, null, "test reason");
    }

    @Test
    void military_disabled_passes_without_record() {
        assertThatCode(() -> validator.validate(application(config())))
                .doesNotThrowAnyException();
    }

    @Test
    void optional_sections_and_attachment_absence_do_not_block_submit_validation() {
        ApplicationFormConfig config = config();
        lenient().when(config.isUseCertificate()).thenReturn(true);
        lenient().when(config.isUseLanguage()).thenReturn(true);
        lenient().when(config.isUseAward()).thenReturn(true);
        lenient().when(config.isUseGapPeriod()).thenReturn(true);

        assertThatCode(() -> validator.validate(application(config)))
                .doesNotThrowAnyException();
    }

    private void assertMilitaryPasses(
            MilitarySubjectType subjectType,
            LocalDate serviceStartDate,
            LocalDate serviceEndDate,
            String exemptionReason
    ) {
        ApplicationFormConfig config = config();
        when(config.isUseMilitary()).thenReturn(true);
        when(militaryRepository.findByJobApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(military(subjectType, serviceStartDate, serviceEndDate, exemptionReason)));

        assertThatCode(() -> validator.validate(application(config)))
                .doesNotThrowAnyException();
    }

    private JobApplication application(ApplicationFormConfig config) {
        JobPosting jobPosting = mock(JobPosting.class);
        when(jobPosting.getApplicationFormConfig()).thenReturn(config);

        JobApplication application = mock(JobApplication.class);
        when(application.getId()).thenReturn(APPLICATION_ID);
        when(application.getJobPosting()).thenReturn(jobPosting);
        return application;
    }

    private ApplicationFormConfig config() {
        return mock(ApplicationFormConfig.class);
    }

    private ApplicationCareerProfile profile(CareerType careerType) {
        return ApplicationCareerProfile.create(mock(JobApplication.class), careerType);
    }

    private ApplicationMilitary military(
            MilitarySubjectType subjectType,
            LocalDate serviceStartDate,
            LocalDate serviceEndDate,
            String exemptionReason
    ) {
        return ApplicationMilitary.create(
                mock(JobApplication.class),
                subjectType,
                null,
                null,
                null,
                serviceStartDate,
                serviceEndDate,
                exemptionReason
        );
    }
}
