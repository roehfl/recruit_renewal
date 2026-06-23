package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.CommonCodeRepository;
import com.shinyoung.recruit.dto.request.BasicInfoSaveRequest;
import com.shinyoung.recruit.dto.response.BasicInfoResponse;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ApplicationBasicInfoService {

    static final String GROUP_NATIONALITY = "NATIONALITY";
    static final String GROUP_DISABILITY_GRADE = "DISABILITY_GRADE";
    static final String GROUP_DISABILITY_TYPE = "DISABILITY_TYPE";

    static final int MIN_AGE = 14;
    static final int MAX_AGE = 100;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9-]{9,20}$");

    private final ApplicationSectionAccessService sectionAccessService;
    private final ApplicationBasicInfoRepository basicInfoRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BasicInfoResponse getBasicInfo(Long applicantId, Long applicationId) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        return basicInfoRepository.findByJobApplicationId(applicationId)
                .map(BasicInfoResponse::of)
                .orElseGet(() -> BasicInfoResponse.prefill(application.getApplicant()));
    }

    @Transactional
    public BasicInfoResponse saveBasicInfo(Long applicantId, Long applicationId, BasicInfoSaveRequest request) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        sectionAccessService.validateWritable(application);
        // Basic info is an always-required section (no ApplicationFormConfig flag), so there is no validateBasicInfoEnabled guard.
        validateRequest(request);

        // Upsert mirrors ApplicationMilitaryService: create-on-first-save then update() applies request fields uniformly for both new and existing rows.
        ApplicationBasicInfo basicInfo = basicInfoRepository.findByJobApplicationId(applicationId)
                .orElseGet(() -> basicInfoRepository.save(toBasicInfo(application, request)));

        basicInfo.update(
                request.nameKorean(), request.nameEnglish(), request.nationalityType(), request.countryCode(),
                request.birthDate(), request.mobilePhone(), request.emergencyPhone(), request.email(),
                request.veteranStatus(), request.veteranType(), request.disabilityStatus(),
                request.disabilityGradeCode(), request.disabilityTypeCode(),
                request.zipCode(), request.addressBasic(), request.addressDetail());

        return BasicInfoResponse.of(basicInfo);
    }

    private void validateRequest(BasicInfoSaveRequest request) {
        if (request == null) {
            throw new InvalidJobApplicationException("Basic info request is required.");
        }
        validateNationality(request);
        validateDisability(request);
        validateBirthDate(request.birthDate());
        validatePhone("Mobile phone", request.mobilePhone(), true);
        validatePhone("Emergency phone", request.emergencyPhone(), false);
    }

    private void validateNationality(BasicInfoSaveRequest request) {
        if (request.nationalityType() == NationalityType.FOREIGN) {
            if (isBlank(request.countryCode())) {
                throw new InvalidJobApplicationException("Country code is required for a foreign applicant.");
            }
            if (!commonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue(GROUP_NATIONALITY, request.countryCode())) {
                throw new InvalidJobApplicationException("Country code is not an active common code.");
            }
        } else if (!isBlank(request.countryCode())) {
            throw new InvalidJobApplicationException("Country code is not allowed for a domestic applicant.");
        }
    }

    private void validateDisability(BasicInfoSaveRequest request) {
        if (request.disabilityStatus() == DisabilityStatus.SUBJECT) {
            if (isBlank(request.disabilityGradeCode()) || isBlank(request.disabilityTypeCode())) {
                throw new InvalidJobApplicationException("Disability grade and type are required for a disability subject.");
            }
            if (!commonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue(GROUP_DISABILITY_GRADE, request.disabilityGradeCode())) {
                throw new InvalidJobApplicationException("Disability grade code is not an active common code.");
            }
            if (!commonCodeRepository.existsByGroupCodeAndCodeAndActiveTrue(GROUP_DISABILITY_TYPE, request.disabilityTypeCode())) {
                throw new InvalidJobApplicationException("Disability type code is not an active common code.");
            }
        } else if (!isBlank(request.disabilityGradeCode()) || !isBlank(request.disabilityTypeCode())) {
            throw new InvalidJobApplicationException("Disability grade/type are not allowed when not a disability subject.");
        }
    }

    private void validateBirthDate(LocalDate birthDate) {
        LocalDate today = LocalDate.now(clock);
        int age = Period.between(birthDate, today).getYears();
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new InvalidJobApplicationException("Birth date must be between age " + MIN_AGE + " and " + MAX_AGE + ".");
        }
    }

    private void validatePhone(String label, String phone, boolean required) {
        if (isBlank(phone)) {
            if (required) {
                throw new InvalidJobApplicationException(label + " is required.");
            }
            return;
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new InvalidJobApplicationException(label + " must contain only digits and hyphens (9-20 chars).");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ApplicationBasicInfo toBasicInfo(JobApplication application, BasicInfoSaveRequest request) {
        return ApplicationBasicInfo.create(
                application,
                request.nameKorean(), request.nameEnglish(), request.nationalityType(), request.countryCode(),
                request.birthDate(), request.mobilePhone(), request.emergencyPhone(), request.email(),
                request.veteranStatus(), request.veteranType(), request.disabilityStatus(),
                request.disabilityGradeCode(), request.disabilityTypeCode(),
                request.zipCode(), request.addressBasic(), request.addressDetail());
    }
}
