package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.ApplicationCareerProfile;
import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPostingAttachmentRequirement;
import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.domain.repository.ApplicationAnswerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerProfileRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.CareerType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationSubmitValidator {

    private static final int SHORT_TEXT_MAX_LENGTH = 500;
    private static final int LONG_TEXT_MAX_LENGTH = 5000;

    private final ApplicationEducationRepository educationRepository;
    private final ApplicationCareerProfileRepository careerProfileRepository;
    private final ApplicationCareerRepository careerRepository;
    private final ApplicationMilitaryRepository militaryRepository;
    private final ApplicationCertificateRepository certificateRepository;
    private final ApplicationLanguageRepository languageRepository;
    private final ApplicationAwardRepository awardRepository;
    private final ApplicationGapPeriodRepository gapPeriodRepository;
    private final JobPostingQuestionRepository jobPostingQuestionRepository;
    private final ApplicationAnswerRepository applicationAnswerRepository;
    private final JobPostingAttachmentRequirementRepository attachmentRequirementRepository;
    private final ApplicationAttachmentRepository attachmentRepository;

    public void validate(JobApplication application) {
        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null) {
            throw new InvalidJobApplicationException("Application form config is required before submit.");
        }

        Long applicationId = application.getId();
        validateEducation(config, applicationId);
        validateCareer(config, applicationId);
        validateMilitary(config, applicationId);
        validateSimpleRequiredSection(config.isUseCertificate(), config.isRequireCertificate(), () -> certificateRepository.existsByJobApplicationId(applicationId), "Certificate");
        validateSimpleRequiredSection(config.isUseLanguage(), config.isRequireLanguage(), () -> languageRepository.existsByJobApplicationId(applicationId), "Language");
        validateSimpleRequiredSection(config.isUseAward(), config.isRequireAward(), () -> awardRepository.existsByJobApplicationId(applicationId), "Award");
        validateSimpleRequiredSection(config.isUseGapPeriod(), config.isRequireGapPeriod(), () -> gapPeriodRepository.existsByJobApplicationId(applicationId), "Gap period");
        validateAnswers(application);
        validateAttachmentRequirements(application);
    }

    private void validateEducation(ApplicationFormConfig config, Long applicationId) {
        if (!isRequired(config.isUseEducation(), config.isRequireEducation())) {
            return;
        }
        if (!educationRepository.existsByJobApplicationId(applicationId)) {
            throw new InvalidJobApplicationException("Education section is required before submit.");
        }
    }

    private void validateCareer(ApplicationFormConfig config, Long applicationId) {
        if (!isRequired(config.isUseCareer(), config.isRequireCareer())) {
            return;
        }

        ApplicationCareerProfile profile = careerProfileRepository.findByJobApplicationId(applicationId)
                .orElseThrow(() -> new InvalidJobApplicationException("Career profile is required before submit."));
        CareerType careerType = profile.getCareerType();
        if (careerType == null || careerType == CareerType.NOT_SELECTED) {
            throw new InvalidJobApplicationException("Career type must be selected before submit.");
        }

        boolean hasCareerRows = careerRepository.existsByJobApplicationId(applicationId);
        if (careerType == CareerType.EXPERIENCED && !hasCareerRows) {
            throw new InvalidJobApplicationException("Career rows are required for experienced applicants before submit.");
        }
        if ((careerType == CareerType.NEWCOMER || careerType == CareerType.NOT_APPLICABLE) && hasCareerRows) {
            throw new InvalidJobApplicationException("Career rows are not allowed for the selected career type before submit.");
        }
    }

    private void validateMilitary(ApplicationFormConfig config, Long applicationId) {
        if (!isRequired(config.isUseMilitary(), config.isRequireMilitary())) {
            return;
        }

        ApplicationMilitary military = militaryRepository.findByJobApplicationId(applicationId)
                .orElseThrow(() -> new InvalidJobApplicationException("Military section is required before submit."));
        MilitarySubjectType subjectType = military.getMilitarySubjectType();
        if (subjectType == null) {
            throw new InvalidJobApplicationException("Military subject type is required before submit.");
        }

        if (subjectType == MilitarySubjectType.COMPLETED
                && (military.getServiceStartDate() == null || military.getServiceEndDate() == null)) {
            throw new InvalidJobApplicationException("Military service period is required for completed applicants before submit.");
        }
        if (subjectType == MilitarySubjectType.EXEMPTED
                && (military.getExemptionReason() == null || military.getExemptionReason().isBlank())) {
            throw new InvalidJobApplicationException("Military exemption reason is required before submit.");
        }
    }

    private void validateAnswers(JobApplication application) {
        List<JobPostingQuestion> questions = jobPostingQuestionRepository
                .findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(application.getJobPosting().getId());
        if (questions.isEmpty()) {
            return;
        }

        Map<Long, ApplicationAnswer> answerMap = applicationAnswerRepository.findByJobApplicationId(application.getId()).stream()
                .filter(answer -> answer.getJobPostingQuestion() != null)
                .collect(Collectors.toMap(
                        answer -> answer.getJobPostingQuestion().getId(),
                        answer -> answer,
                        (first, ignored) -> first
                ));

        for (JobPostingQuestion question : questions) {
            ApplicationAnswer answer = answerMap.get(question.getId());
            validateRequiredAnswer(question, answer);
            validateAnswerLength(question, answer);
        }
    }

    private void validateRequiredAnswer(JobPostingQuestion question, ApplicationAnswer answer) {
        if (!Boolean.TRUE.equals(question.getRequired())) {
            return;
        }
        if (answer == null) {
            throw new InvalidJobApplicationException("Required question answer is missing.");
        }
        String answerText = answer.getAnswerText();
        if (answerText == null) {
            throw new InvalidJobApplicationException("Required question answer is missing.");
        }
        if (answerText.isBlank()) {
            throw new InvalidJobApplicationException("Required question answer is blank.");
        }
    }

    private void validateAnswerLength(JobPostingQuestion question, ApplicationAnswer answer) {
        if (answer == null || answer.getAnswerText() == null) {
            return;
        }

        String answerText = answer.getAnswerText();
        int questionMaxLength = question.getMaxLength() != null
                ? question.getMaxLength()
                : defaultMaxLength(question.getAnswerType());
        if (answerText.length() > questionMaxLength) {
            throw new InvalidJobApplicationException("Answer exceeds max length.");
        }
        if (question.getAnswerType() == QuestionAnswerType.SHORT_TEXT && answerText.length() > SHORT_TEXT_MAX_LENGTH) {
            throw new InvalidJobApplicationException("Answer exceeds SHORT_TEXT max length.");
        }
        if (question.getAnswerType() == QuestionAnswerType.LONG_TEXT && answerText.length() > LONG_TEXT_MAX_LENGTH) {
            throw new InvalidJobApplicationException("Answer exceeds LONG_TEXT max length.");
        }
    }

    private int defaultMaxLength(QuestionAnswerType answerType) {
        if (answerType == QuestionAnswerType.SHORT_TEXT) {
            return SHORT_TEXT_MAX_LENGTH;
        }
        return LONG_TEXT_MAX_LENGTH;
    }

    private void validateSimpleRequiredSection(
            boolean useSection,
            boolean requireSection,
            BooleanSupplier exists,
            String sectionName
    ) {
        if (!isRequired(useSection, requireSection) || exists.getAsBoolean()) {
            return;
        }
        throw new InvalidJobApplicationException(sectionName + " section is required before submit.");
    }

    private boolean isRequired(boolean useSection, boolean requireSection) {
        return useSection && requireSection;
    }

    private void validateAttachmentRequirements(JobApplication application) {
        List<JobPostingAttachmentRequirement> requirements = attachmentRequirementRepository
                .findByJobPostingIdAndRequiredTrueOrderBySortOrderAscIdAsc(application.getJobPosting().getId());
        if (requirements.isEmpty()) {
            return;
        }

        Map<AttachmentRequirementKey, Long> storedCounts = attachmentRepository
                .findByJobApplicationIdAndPhysicalFileStatus(application.getId(), PhysicalFileStatus.STORED)
                .stream()
                .filter(attachment -> attachment.getDeletedAt() == null)
                .collect(Collectors.groupingBy(
                        attachment -> new AttachmentRequirementKey(
                                attachment.getAttachmentType(),
                                attachment.getSectionType()
                        ),
                        Collectors.counting()
                ));

        for (JobPostingAttachmentRequirement requirement : requirements) {
            long storedCount = storedCounts.getOrDefault(
                    new AttachmentRequirementKey(requirement.getAttachmentType(), requirement.getSectionType()),
                    0L
            );
            if (storedCount < requirement.getMinCount()) {
                throw new InvalidJobApplicationException(requirement.getDisplayName() + " attachment is required before submit.");
            }
        }
    }

    private record AttachmentRequirementKey(
            AttachmentType attachmentType,
            ApplicationSectionType sectionType
    ) {
    }
}
