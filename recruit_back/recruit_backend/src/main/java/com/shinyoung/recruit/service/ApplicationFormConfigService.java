package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.response.ApplicationFormConfigResponse;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 지원서 양식(섹션 사용/필수) 설정의 단일 소유자.
 * 공고 등록/수정 API에서 분리해, 공고 수정 화면이 낡은 값으로 설정을 덮어쓰는 것을 구조적으로 막는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationFormConfigService {

    private final JobPostingRepository jobPostingRepository;
    private final Clock clock;

    /**
     * 공고 등록 시 사용할 설정을 만든다.
     * 요청이 없으면(등록 화면에서 양식을 다루지 않는 경우) 기본 설정을 만든다.
     * 설정을 null 로 두면 레이아웃 조회·게시·지원서 작성 흐름이 모두 막히므로 항상 값을 만든다.
     */
    public ApplicationFormConfig createFrom(ApplicationFormConfigRequest request) {
        if (request == null) {
            return defaultConfig();
        }
        boolean requireEducation = defaultRequired(request.requireEducation(), request.useEducation());
        boolean requireCareer = defaultRequired(request.requireCareer(), request.useCareer());
        boolean requireCertificate = defaultRequired(request.requireCertificate(), false);
        boolean requireLanguage = defaultRequired(request.requireLanguage(), false);
        boolean requireMilitary = defaultRequired(request.requireMilitary(), request.useMilitary());
        boolean requireAward = defaultRequired(request.requireAward(), false);
        boolean requireGapPeriod = defaultRequired(request.requireGapPeriod(), false);
        validateRequirement(request, requireEducation, requireCareer, requireCertificate, requireLanguage, requireMilitary, requireAward, requireGapPeriod);
        return ApplicationFormConfig.create(
                request.useEducation(),
                requireEducation,
                request.useCareer(),
                requireCareer,
                request.useCertificate(),
                requireCertificate,
                request.useLanguage(),
                requireLanguage,
                request.useMilitary(),
                requireMilitary,
                request.useAward(),
                requireAward,
                request.useGapPeriod(),
                requireGapPeriod,
                request.useAttachment()
        );
    }

    @Transactional
    public ApplicationFormConfigResponse save(Long jobPostingId, ApplicationFormConfigRequest request) {
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + jobPostingId));

        validateEditable(jobPosting);
        jobPosting.updateApplicationFormConfig(merge(request, jobPosting.getApplicationFormConfig()));

        return ApplicationFormConfigResponse.from(jobPosting.getApplicationFormConfig());
    }

    /** 지원서 양식은 레이아웃과 같은 시점 규칙을 따른다(ApplicationFormEditWindow). */
    private void validateEditable(JobPosting jobPosting) {
        if (ApplicationFormEditWindow.isEditable(jobPosting, LocalDateTime.now(clock))) {
            return;
        }
        if (jobPosting.getStatus() == JobPostingStatus.CLOSED) {
            throw new InvalidJobPostingException("마감된 채용공고의 지원서 양식은 수정할 수 없습니다.");
        }
        throw new InvalidJobPostingException("접수가 시작된 채용공고의 지원서 양식은 수정할 수 없습니다.");
    }

    private ApplicationFormConfig merge(ApplicationFormConfigRequest request, ApplicationFormConfig currentConfig) {
        if (currentConfig == null) {
            return createFrom(request);
        }

        boolean requireEducation = resolveUpdatedRequired(request.useEducation(), request.requireEducation(), currentConfig.isRequireEducation());
        boolean requireCareer = resolveUpdatedRequired(request.useCareer(), request.requireCareer(), currentConfig.isRequireCareer());
        boolean requireCertificate = resolveUpdatedRequired(request.useCertificate(), request.requireCertificate(), currentConfig.isRequireCertificate());
        boolean requireLanguage = resolveUpdatedRequired(request.useLanguage(), request.requireLanguage(), currentConfig.isRequireLanguage());
        boolean requireMilitary = resolveUpdatedRequired(request.useMilitary(), request.requireMilitary(), currentConfig.isRequireMilitary());
        boolean requireAward = resolveUpdatedRequired(request.useAward(), request.requireAward(), currentConfig.isRequireAward());
        boolean requireGapPeriod = resolveUpdatedRequired(request.useGapPeriod(), request.requireGapPeriod(), currentConfig.isRequireGapPeriod());
        validateRequirement(request, requireEducation, requireCareer, requireCertificate, requireLanguage, requireMilitary, requireAward, requireGapPeriod);

        return ApplicationFormConfig.create(
                request.useEducation(),
                requireEducation,
                request.useCareer(),
                requireCareer,
                request.useCertificate(),
                requireCertificate,
                request.useLanguage(),
                requireLanguage,
                request.useMilitary(),
                requireMilitary,
                request.useAward(),
                requireAward,
                request.useGapPeriod(),
                requireGapPeriod,
                request.useAttachment()
        );
    }

    /**
     * 등록 화면이 양식을 다루지 않을 때 쓰는 기본값.
     * 분리 이전 등록 화면의 기본값(전 섹션 사용, 학력·경력·병역만 필수)과 동일하게 두어 동작을 보존한다.
     */
    private ApplicationFormConfig defaultConfig() {
        return ApplicationFormConfig.create(true, true, true, true, true, true, true);
    }

    private boolean defaultRequired(Boolean requestedRequired, boolean defaultValue) {
        return requestedRequired == null ? defaultValue : requestedRequired;
    }

    private boolean resolveUpdatedRequired(boolean useSection, Boolean requestedRequired, boolean currentRequired) {
        if (requestedRequired != null) {
            return requestedRequired;
        }
        return useSection && currentRequired;
    }

    private void validateRequirement(
            ApplicationFormConfigRequest request,
            boolean requireEducation,
            boolean requireCareer,
            boolean requireCertificate,
            boolean requireLanguage,
            boolean requireMilitary,
            boolean requireAward,
            boolean requireGapPeriod
    ) {
        validateRequirement(request.useEducation(), requireEducation, "education");
        validateRequirement(request.useCareer(), requireCareer, "career");
        validateRequirement(request.useCertificate(), requireCertificate, "certificate");
        validateRequirement(request.useLanguage(), requireLanguage, "language");
        validateRequirement(request.useMilitary(), requireMilitary, "military");
        validateRequirement(request.useAward(), requireAward, "award");
        validateRequirement(request.useGapPeriod(), requireGapPeriod, "gap period");
    }

    private void validateRequirement(boolean useSection, boolean requireSection, String sectionName) {
        if (!useSection && requireSection) {
            throw new InvalidJobPostingException(sectionName + " section cannot be required when disabled.");
        }
    }
}
