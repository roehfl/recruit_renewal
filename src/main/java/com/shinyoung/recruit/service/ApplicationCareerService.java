package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationCareer;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.dto.request.CareerReplaceRequest;
import com.shinyoung.recruit.dto.request.CareerRequest;
import com.shinyoung.recruit.dto.response.CareerItemResponse;
import com.shinyoung.recruit.dto.response.CareerResponse;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApplicationCareerService {

    private static final int CAREER_TEXT_MAX_LENGTH = 2000;

    private final ApplicationSectionAccessService sectionAccessService;
    private final ApplicationCareerRepository careerRepository;

    @Transactional(readOnly = true)
    public CareerResponse getCareers(Long applicantId, Long applicationId) {
        sectionAccessService.findOwnedApplication(applicantId, applicationId);
        return getCareerResponse(applicationId);
    }

    @Transactional
    public CareerResponse replaceCareers(Long applicantId, Long applicationId, CareerReplaceRequest request) {
        JobApplication application = sectionAccessService.findOwnedApplication(applicantId, applicationId);
        sectionAccessService.validateWritable(application);
        sectionAccessService.validateCareerEnabled(application);
        validateRequest(request);

        careerRepository.deleteByJobApplicationId(applicationId);
        List<ApplicationCareer> careers = request.careers().stream()
                .map(career -> toCareer(application, career))
                .toList();
        careerRepository.saveAll(careers);

        return getCareerResponse(applicationId);
    }

    private void validateRequest(CareerReplaceRequest request) {
        if (request == null || request.careers() == null) {
            throw new InvalidJobApplicationException("Career list is required.");
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
                request.promotionDate(),
                request.currentlyEmployed(),
                request.responsibilities(),
                request.resignationReason(),
                request.sortOrder()
        );
    }

    private CareerResponse getCareerResponse(Long applicationId) {
        List<CareerItemResponse> careers = careerRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(CareerItemResponse::from)
                .toList();
        return new CareerResponse(careers);
    }
}
