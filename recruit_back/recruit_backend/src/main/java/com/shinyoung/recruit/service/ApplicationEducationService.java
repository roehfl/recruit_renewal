package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationEducationSemesterGrade;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationSemesterGradeRepository;
import com.shinyoung.recruit.dto.request.EducationReplaceRequest;
import com.shinyoung.recruit.dto.request.EducationRequest;
import com.shinyoung.recruit.dto.request.SemesterGradeRequest;
import com.shinyoung.recruit.dto.response.EducationResponse;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationEducationService {

    private final ApplicationSectionAccessService sectionAccessService;
    private final ApplicationEducationRepository educationRepository;
    private final ApplicationEducationSemesterGradeRepository semesterGradeRepository;

    @Transactional(readOnly = true)
    public List<EducationResponse> getEducations(Long applicantId, Long applicationId) {
        sectionAccessService.findOwnedApplication(applicantId, applicationId);
        return getEducationResponses(applicationId);
    }

    @Transactional
    public List<EducationResponse> replaceEducations(Long applicantId, Long applicationId, EducationReplaceRequest request) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        sectionAccessService.validateWritable(application);
        sectionAccessService.validateEducationEnabled(application);
        validateRequest(request);

        List<ApplicationEducation> existingEducations = educationRepository.findByJobApplicationId(applicationId);
        List<Long> existingEducationIds = existingEducations.stream()
                .map(ApplicationEducation::getId)
                .toList();
        if (!existingEducationIds.isEmpty()) {
            semesterGradeRepository.deleteByEducationIdIn(existingEducationIds);
            educationRepository.deleteByJobApplicationId(applicationId);
        }

        for (EducationRequest educationRequest : request.educations()) {
            ApplicationEducation education = educationRepository.save(toEducation(application, educationRequest));
            List<ApplicationEducationSemesterGrade> semesterGrades = semesterGradeRequests(educationRequest).stream()
                    .map(semesterGradeRequest -> toSemesterGrade(education, semesterGradeRequest))
                    .toList();
            semesterGradeRepository.saveAll(semesterGrades);
        }

        return getEducationResponses(applicationId);
    }

    private void validateRequest(EducationReplaceRequest request) {
        if (request == null || request.educations() == null) {
            throw new InvalidJobApplicationException("Education list is required.");
        }

        Set<Integer> sortOrders = new HashSet<>();
        int highSchoolCount = 0;
        for (EducationRequest education : request.educations()) {
            validateEducationRequiredFields(education);
            validateOverallGrades(education);
            if (!sortOrders.add(education.sortOrder())) {
                throw new InvalidJobApplicationException("Education sort order must be unique.");
            }
            List<SemesterGradeRequest> semesterGrades = semesterGradeRequests(education);
            if (education.educationLevel() == EducationLevel.HIGH_SCHOOL) {
                if (!semesterGrades.isEmpty()) {
                    throw new InvalidJobApplicationException("High school education cannot include semester grades.");
                }
                // 최종 고등학교 1개만 입력받는다.
                if (++highSchoolCount > 1) {
                    throw new InvalidJobApplicationException("High school education must not be more than one.");
                }
            }
            semesterGrades.forEach(this::validateSemesterGrade);
        }
    }

    private void validateEducationRequiredFields(EducationRequest education) {
        if (education == null) {
            throw new InvalidJobApplicationException("Education item is required.");
        }
        if (education.educationLevel() == null) {
            throw new InvalidJobApplicationException("Education level is required.");
        }
        if (education.schoolName() == null || education.schoolName().isBlank()) {
            throw new InvalidJobApplicationException("School name is required.");
        }
        if (education.graduationStatus() == null) {
            throw new InvalidJobApplicationException("Graduation status is required.");
        }
        if (education.sortOrder() == null || education.sortOrder() < 0) {
            throw new InvalidJobApplicationException("Sort order must be greater than or equal to 0.");
        }
        if (education.admissionDate() != null
                && education.graduationDate() != null
                && education.admissionDate().isAfter(education.graduationDate())) {
            throw new InvalidJobApplicationException("Admission date cannot be after graduation date.");
        }
    }

    private void validateSemesterGrade(SemesterGradeRequest semesterGrade) {
        if (semesterGrade == null) {
            throw new InvalidJobApplicationException("Semester grade item is required.");
        }
        if (semesterGrade.schoolYear() == null || semesterGrade.schoolYear() < 1) {
            throw new InvalidJobApplicationException("School year must be greater than or equal to 1.");
        }
        if (semesterGrade.semester() == null || semesterGrade.semester() < 1) {
            throw new InvalidJobApplicationException("Semester must be greater than or equal to 1.");
        }
        if (semesterGrade.earnedCredits() != null && isNegative(semesterGrade.earnedCredits())) {
            throw new InvalidJobApplicationException("Earned credits must be greater than or equal to 0.");
        }
        if (semesterGrade.gradePoint() == null || isNegative(semesterGrade.gradePoint())) {
            throw new InvalidJobApplicationException("Grade point must be greater than or equal to 0.");
        }
        if (semesterGrade.maxGradePoint() == null || isZeroOrNegative(semesterGrade.maxGradePoint())) {
            throw new InvalidJobApplicationException("Max grade point must be greater than 0.");
        }
        if (semesterGrade.gradePoint().compareTo(semesterGrade.maxGradePoint()) > 0) {
            throw new InvalidJobApplicationException("Grade point cannot exceed max grade point.");
        }
        if (semesterGrade.majorGradePoint() != null && semesterGrade.majorMaxGradePoint() == null) {
            throw new InvalidJobApplicationException("Major max grade point is required when major grade point is provided.");
        }
        if (semesterGrade.majorMaxGradePoint() != null && isZeroOrNegative(semesterGrade.majorMaxGradePoint())) {
            throw new InvalidJobApplicationException("Major max grade point must be greater than 0.");
        }
        if (semesterGrade.majorGradePoint() != null
                && semesterGrade.majorMaxGradePoint() != null
                && semesterGrade.majorGradePoint().compareTo(semesterGrade.majorMaxGradePoint()) > 0) {
            throw new InvalidJobApplicationException("Major grade point cannot exceed major max grade point.");
        }
    }

    private void validateOverallGrades(EducationRequest education) {
        BigDecimal overall = education.overallGradePoint();
        BigDecimal overallMax = education.overallMaxGradePoint();
        boolean highSchool = education.educationLevel() == EducationLevel.HIGH_SCHOOL;

        if (!highSchool && (overall == null || overallMax == null)) {
            throw new InvalidJobApplicationException("Overall grade point and max grade point are required.");
        }
        if ((overall == null) != (overallMax == null)) {
            throw new InvalidJobApplicationException("Overall grade point and max grade point must be provided together.");
        }
        if (overall != null) {
            if (isNegative(overall)) {
                throw new InvalidJobApplicationException("Overall grade point must be greater than or equal to 0.");
            }
            if (isZeroOrNegative(overallMax)) {
                throw new InvalidJobApplicationException("Overall max grade point must be greater than 0.");
            }
            if (overall.compareTo(overallMax) > 0) {
                throw new InvalidJobApplicationException("Overall grade point cannot exceed overall max grade point.");
            }
        }

        BigDecimal major = education.overallMajorGradePoint();
        BigDecimal majorMax = education.overallMajorMaxGradePoint();
        if ((major == null) != (majorMax == null)) {
            throw new InvalidJobApplicationException("Overall major grade point and max grade point must be provided together.");
        }
        if (major != null && isNegative(major)) {
            throw new InvalidJobApplicationException("Overall major grade point must be greater than or equal to 0.");
        }
        if (majorMax != null && isZeroOrNegative(majorMax)) {
            throw new InvalidJobApplicationException("Overall major max grade point must be greater than 0.");
        }
        if (major != null && majorMax != null && major.compareTo(majorMax) > 0) {
            throw new InvalidJobApplicationException("Overall major grade point cannot exceed overall major max grade point.");
        }
    }

    private boolean isNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0;
    }

    private boolean isZeroOrNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) <= 0;
    }

    private ApplicationEducation toEducation(JobApplication application, EducationRequest request) {
        return ApplicationEducation.create(
                application,
                request.educationLevel(),
                request.schoolName(),
                request.majorName(),
                request.additionalMajorType(),
                request.additionalMajorName(),
                request.thesisTitle(),
                request.admissionDate(),
                request.graduationDate(),
                request.graduationStatus(),
                request.dayNightType(),
                request.campusType(),
                request.transfer(),
                request.countryCode(),
                request.schoolId(),
                request.overallGradePoint(),
                request.overallMaxGradePoint(),
                request.overallMajorGradePoint(),
                request.overallMajorMaxGradePoint(),
                request.sortOrder()
        );
    }

    private ApplicationEducationSemesterGrade toSemesterGrade(
            ApplicationEducation education,
            SemesterGradeRequest request
    ) {
        return ApplicationEducationSemesterGrade.create(
                education,
                request.schoolYear(),
                request.semester(),
                request.earnedCredits(),
                request.gradePoint(),
                request.maxGradePoint(),
                request.majorGradePoint(),
                request.majorMaxGradePoint()
        );
    }

    private List<EducationResponse> getEducationResponses(Long applicationId) {
        List<ApplicationEducation> educations = educationRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId);
        if (educations.isEmpty()) {
            return List.of();
        }

        List<Long> educationIds = educations.stream()
                .map(ApplicationEducation::getId)
                .toList();
        Map<Long, List<ApplicationEducationSemesterGrade>> semesterGradesByEducationId = semesterGradeRepository
                .findByEducationIdInOrderBySchoolYearAscSemesterAscIdAsc(educationIds)
                .stream()
                .collect(Collectors.groupingBy(grade -> grade.getEducation().getId()));

        return educations.stream()
                .map(education -> EducationResponse.from(
                        education,
                        new ArrayList<>(semesterGradesByEducationId.getOrDefault(education.getId(), List.of()))
                ))
                .toList();
    }

    private List<SemesterGradeRequest> semesterGradeRequests(EducationRequest education) {
        if (education.semesterGrades() == null) {
            return List.of();
        }
        return education.semesterGrades();
    }
}
