package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationCareerProfile;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationEducationSemesterGrade;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerProfileRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationSemesterGradeRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.dto.response.AdminAttachmentResponse;
import com.shinyoung.recruit.dto.response.AdminAwardResponse;
import com.shinyoung.recruit.dto.response.AdminCareerItemResponse;
import com.shinyoung.recruit.dto.response.AdminCareerResponse;
import com.shinyoung.recruit.dto.response.AdminCertificateResponse;
import com.shinyoung.recruit.dto.response.AdminEducationResponse;
import com.shinyoung.recruit.dto.response.AdminGapPeriodResponse;
import com.shinyoung.recruit.dto.response.AdminLanguageResponse;
import com.shinyoung.recruit.dto.response.AdminMilitaryResponse;
import com.shinyoung.recruit.enumeration.CareerType;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminApplicationSectionService {

    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationEducationRepository educationRepository;
    private final ApplicationEducationSemesterGradeRepository semesterGradeRepository;
    private final ApplicationCareerProfileRepository careerProfileRepository;
    private final ApplicationCareerRepository careerRepository;
    private final ApplicationCertificateRepository certificateRepository;
    private final ApplicationLanguageRepository languageRepository;
    private final ApplicationMilitaryRepository militaryRepository;
    private final ApplicationAwardRepository awardRepository;
    private final ApplicationGapPeriodRepository gapPeriodRepository;
    private final ApplicationAttachmentRepository attachmentRepository;

    public List<AdminEducationResponse> getEducations(Long applicationId) {
        validateApplicationExists(applicationId);
        List<ApplicationEducation> educations = educationRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId);
        List<Long> educationIds = educations.stream()
                .map(ApplicationEducation::getId)
                .toList();
        Map<Long, List<ApplicationEducationSemesterGrade>> semesterGradesByEducationId = educationIds.isEmpty()
                ? Map.of()
                : semesterGradeRepository.findByEducationIdInOrderBySchoolYearAscSemesterAscIdAsc(educationIds).stream()
                .collect(Collectors.groupingBy(grade -> grade.getEducation().getId()));

        return educations.stream()
                .map(education -> AdminEducationResponse.from(
                        education,
                        semesterGradesByEducationId.getOrDefault(education.getId(), List.of())
                ))
                .toList();
    }

    public AdminCareerResponse getCareers(Long applicationId) {
        validateApplicationExists(applicationId);
        CareerType careerType = careerProfileRepository.findByJobApplicationId(applicationId)
                .map(ApplicationCareerProfile::getCareerType)
                .orElse(CareerType.NOT_SELECTED);
        List<AdminCareerItemResponse> careers = careerRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(AdminCareerItemResponse::from)
                .toList();

        return new AdminCareerResponse(careerType, careers);
    }

    public List<AdminCertificateResponse> getCertificates(Long applicationId) {
        validateApplicationExists(applicationId);
        return certificateRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(certificate -> AdminCertificateResponse.from(
                        certificate,
                        maskCertificateNumber(certificate.getCertificateNumber())
                ))
                .toList();
    }

    public List<AdminLanguageResponse> getLanguages(Long applicationId) {
        validateApplicationExists(applicationId);
        return languageRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(AdminLanguageResponse::from)
                .toList();
    }

    public AdminMilitaryResponse getMilitary(Long applicationId) {
        validateApplicationExists(applicationId);
        return militaryRepository.findByJobApplicationId(applicationId)
                .map(military -> AdminMilitaryResponse.from(
                        military,
                        maskSensitiveText(military.getExemptionReason())
                ))
                .orElse(null);
    }

    public List<AdminAwardResponse> getAwards(Long applicationId) {
        validateApplicationExists(applicationId);
        return awardRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(AdminAwardResponse::from)
                .toList();
    }

    public List<AdminGapPeriodResponse> getGapPeriods(Long applicationId) {
        validateApplicationExists(applicationId);
        return gapPeriodRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(AdminGapPeriodResponse::from)
                .toList();
    }

    public List<AdminAttachmentResponse> getAttachments(Long applicationId) {
        validateApplicationExists(applicationId);
        return attachmentRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(AdminAttachmentResponse::from)
                .toList();
    }

    private void validateApplicationExists(Long applicationId) {
        if (!jobApplicationRepository.existsById(applicationId)) {
            throw new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. id=" + applicationId);
        }
    }

    private String maskCertificateNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        int visibleLength = Math.min(trimmed.length(), trimmed.length() <= 4 ? 2 : 3);
        return trimmed.substring(0, visibleLength) + "***";
    }

    private String maskSensitiveText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "***";
    }
}
