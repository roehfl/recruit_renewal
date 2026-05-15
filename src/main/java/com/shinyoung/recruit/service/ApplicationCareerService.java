package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationCareer;
import com.shinyoung.recruit.domain.entity.ApplicationCareerProfile;
import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationCareerProfileRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.dto.request.CareerReplaceRequest;
import com.shinyoung.recruit.dto.request.CareerRequest;
import com.shinyoung.recruit.dto.response.CareerItemResponse;
import com.shinyoung.recruit.dto.response.CareerResponse;
import com.shinyoung.recruit.enumeration.CareerType;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApplicationCareerService {

    private static final int CAREER_TEXT_MAX_LENGTH = 2000;

    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationCareerProfileRepository careerProfileRepository;
    private final ApplicationCareerRepository careerRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CareerResponse getCareers(Long applicantId, Long applicationId) {
        findApplication(applicantId, applicationId);
        return getCareerResponse(applicationId);
    }

    @Transactional
    public CareerResponse replaceCareers(Long applicantId, Long applicationId, CareerReplaceRequest request) {
        JobApplication application = findApplication(applicantId, applicationId);
        validateCareerWritable(application);
        validateRequest(request);

        ApplicationCareerProfile profile = careerProfileRepository.findByJobApplicationId(applicationId)
                .orElseGet(() -> careerProfileRepository.save(
                        ApplicationCareerProfile.create(application, request.careerType())
                ));
        profile.updateCareerType(request.careerType());

        careerRepository.deleteByJobApplicationId(applicationId);
        List<ApplicationCareer> careers = request.careers().stream()
                .map(career -> toCareer(application, career))
                .toList();
        careerRepository.saveAll(careers);

        return getCareerResponse(applicationId);
    }

    private JobApplication findApplication(Long applicantId, Long applicationId) {
        return jobApplicationRepository.findByIdAndApplicantId(applicationId, applicantId)
                .orElseThrow(() -> new JobApplicationNotFoundException("Application was not found."));
    }

    private void validateCareerWritable(JobApplication application) {
        if (application.getStatus() != JobApplicationStatus.DRAFT) {
            throw new InvalidJobApplicationException("Career can be modified only in DRAFT status.");
        }
        if (application.getJobPosting().getStatus() != JobPostingStatus.PUBLISHED) {
            throw new InvalidJobApplicationException("Career can be modified only for a published job posting.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(application.getJobPosting().getReceptionStartDateTime())
                || now.isAfter(application.getJobPosting().getReceptionEndDateTime())) {
            throw new InvalidJobApplicationException("Career can be modified only during the reception period.");
        }

        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null || !config.isUseCareer()) {
            throw new InvalidJobApplicationException("Career section is not enabled for this job posting.");
        }
    }

    private void validateRequest(CareerReplaceRequest request) {
        if (request == null || request.careerType() == null) {
            throw new InvalidJobApplicationException("Career type is required.");
        }
        if (request.careers() == null) {
            throw new InvalidJobApplicationException("Career list is required.");
        }
        if (request.careerType() != CareerType.EXPERIENCED && !request.careers().isEmpty()) {
            throw new InvalidJobApplicationException("Career items are allowed only for EXPERIENCED career type.");
        }

        Set<Integer> sortOrders = new HashSet<>();
        for (CareerRequest career : request.careers()) {
            validateCareerRequiredFields(career);
            if (!sortOrders.add(career.sortOrder())) {
                throw new InvalidJobApplicationException("Career sort order must be unique.");
            }
        }
    }

    private void validateCareerRequiredFields(CareerRequest career) {
        if (career == null) {
            throw new InvalidJobApplicationException("Career item is required.");
        }
        if (career.companyName() == null || career.companyName().isBlank()) {
            throw new InvalidJobApplicationException("Company name is required.");
        }
        if (career.startDate() == null) {
            throw new InvalidJobApplicationException("Start date is required.");
        }
        if (career.currentlyEmployed() == null) {
            throw new InvalidJobApplicationException("Currently employed flag is required.");
        }
        if (career.sortOrder() == null || career.sortOrder() < 0) {
            throw new InvalidJobApplicationException("Sort order must be greater than or equal to 0.");
        }
        if (!career.currentlyEmployed() && career.endDate() == null) {
            throw new InvalidJobApplicationException("End date is required when currently employed is false.");
        }
        if (career.currentlyEmployed() && career.endDate() != null) {
            throw new InvalidJobApplicationException("End date must be empty when currently employed is true.");
        }
        if (career.endDate() != null && career.startDate().isAfter(career.endDate())) {
            throw new InvalidJobApplicationException("Start date cannot be after end date.");
        }
        if (isLongerThan(career.responsibilities(), CAREER_TEXT_MAX_LENGTH)) {
            throw new InvalidJobApplicationException("Responsibilities must be 2000 characters or less.");
        }
        if (isLongerThan(career.resignationReason(), CAREER_TEXT_MAX_LENGTH)) {
            throw new InvalidJobApplicationException("Resignation reason must be 2000 characters or less.");
        }
    }

    private boolean isLongerThan(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }

    private ApplicationCareer toCareer(JobApplication application, CareerRequest request) {
        return ApplicationCareer.create(
                application,
                request.companyName(),
                request.departmentName(),
                request.positionTitle(),
                request.employmentType(),
                request.startDate(),
                request.endDate(),
                request.currentlyEmployed(),
                request.responsibilities(),
                request.resignationReason(),
                request.sortOrder()
        );
    }

    private CareerResponse getCareerResponse(Long applicationId) {
        CareerType careerType = careerProfileRepository.findByJobApplicationId(applicationId)
                .map(ApplicationCareerProfile::getCareerType)
                .orElse(CareerType.NOT_SELECTED);
        List<CareerItemResponse> careers = careerRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(CareerItemResponse::from)
                .toList();
        return new CareerResponse(careerType, careers);
    }
}
