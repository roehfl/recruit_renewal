package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPostingAttachmentRequirement;
import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.domain.repository.ApplicationAnswerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.dto.response.ApplicationCompletionSummaryResponse;
import com.shinyoung.recruit.dto.response.ApplicationSectionReadinessResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationCompletionReadChecker {

    private static final int SHORT_TEXT_MAX_LENGTH = 500;
    private static final int LONG_TEXT_MAX_LENGTH = 5000;

    private static final String BASIC_INFO = "BASIC_INFO";
    private static final String EDUCATION = "EDUCATION";
    private static final String MILITARY = "MILITARY";
    private static final String QUESTION = "QUESTION";
    private static final String FORM_CONFIG = "FORM_CONFIG";
    private static final String CERTIFICATE = "CERTIFICATE";
    private static final String LANGUAGE = "LANGUAGE";
    private static final String AWARD = "AWARD";
    private static final String GAP_PERIOD = "GAP_PERIOD";
    private static final String ATTACHMENT = "ATTACHMENT";

    private final ApplicationBasicInfoRepository basicInfoRepository;
    private final ApplicationEducationRepository educationRepository;
    private final ApplicationMilitaryRepository militaryRepository;
    private final JobPostingQuestionRepository jobPostingQuestionRepository;
    private final ApplicationAnswerRepository applicationAnswerRepository;
    private final ApplicationCertificateRepository certificateRepository;
    private final ApplicationLanguageRepository languageRepository;
    private final ApplicationAwardRepository awardRepository;
    private final ApplicationGapPeriodRepository gapPeriodRepository;
    private final JobPostingAttachmentRequirementRepository attachmentRequirementRepository;
    private final ApplicationAttachmentRepository attachmentRepository;

    public CompletionReadinessResult check(JobApplication application) {
        Long applicationId = application.getId();
        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();

        ReadinessAccumulator accumulator = new ReadinessAccumulator();
        checkBasicInfo(applicationId, accumulator);
        if (config == null) {
            accumulator.addRequiredGroup(FORM_CONFIG);
            accumulator.addRequiredIssue(item(
                    FORM_CONFIG,
                    "Application form config",
                    true,
                    "MISSING_CONFIG",
                    "Application form config is required before submit."
            ));
        } else {
            checkEducation(config, applicationId, accumulator);
            checkMilitary(config, applicationId, accumulator);
            checkSimpleSection(config.isUseCertificate(), config.isRequireCertificate(), () -> certificateRepository.existsByJobApplicationId(applicationId), CERTIFICATE, "Certificate", accumulator);
            checkSimpleSection(config.isUseLanguage(), config.isRequireLanguage(), () -> languageRepository.existsByJobApplicationId(applicationId), LANGUAGE, "Language", accumulator);
            checkSimpleSection(config.isUseAward(), config.isRequireAward(), () -> awardRepository.existsByJobApplicationId(applicationId), AWARD, "Award", accumulator);
            checkSimpleSection(config.isUseGapPeriod(), config.isRequireGapPeriod(), () -> gapPeriodRepository.existsByJobApplicationId(applicationId), GAP_PERIOD, "Gap period", accumulator);
        }
        checkQuestions(application, accumulator);
        checkAttachments(application, accumulator);

        return accumulator.toResult();
    }

    private void checkBasicInfo(Long applicationId, ReadinessAccumulator accumulator) {
        accumulator.addRequiredGroup(BASIC_INFO);
        ApplicationBasicInfo basicInfo = basicInfoRepository.findByJobApplicationId(applicationId).orElse(null);
        if (basicInfo == null) {
            accumulator.addRequiredIssue(item(BASIC_INFO, "Basic info", true, "MISSING_ROW",
                    "Basic info section is required before submit."));
            return;
        }
        boolean requiredMissing = isBlank(basicInfo.getNameKorean())
                || basicInfo.getBirthDate() == null
                || basicInfo.getNationalityType() == null
                || isBlank(basicInfo.getMobilePhone())
                || isBlank(basicInfo.getEmail())
                || basicInfo.getVeteranStatus() == null
                || basicInfo.getDisabilityStatus() == null
                || (basicInfo.getNationalityType() == NationalityType.FOREIGN && isBlank(basicInfo.getCountryCode()))
                || (basicInfo.getDisabilityStatus() == DisabilityStatus.SUBJECT
                        && (isBlank(basicInfo.getDisabilityGradeCode()) || isBlank(basicInfo.getDisabilityTypeCode())));
        if (requiredMissing) {
            accumulator.addRequiredIssue(item(BASIC_INFO, "Basic info", true, "MISSING_REQUIRED_FIELD",
                    "Basic info required fields are missing before submit."));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void checkEducation(ApplicationFormConfig config, Long applicationId, ReadinessAccumulator accumulator) {
        if (!config.isUseEducation()) {
            return;
        }
        boolean required = config.isRequireEducation();
        addGroup(EDUCATION, required, accumulator);
        if (!educationRepository.existsByJobApplicationId(applicationId)) {
            addIssue(item(
                    EDUCATION,
                    "Education",
                    required,
                    "MISSING_ROW",
                    sectionMissingMessage("Education", required)
            ), required, accumulator);
        }
    }

    private void checkMilitary(ApplicationFormConfig config, Long applicationId, ReadinessAccumulator accumulator) {
        if (!config.isUseMilitary()) {
            return;
        }
        boolean required = config.isRequireMilitary();
        addGroup(MILITARY, required, accumulator);

        Optional<ApplicationMilitary> military = militaryRepository.findByJobApplicationId(applicationId);
        if (military.isEmpty()) {
            addIssue(item(
                    MILITARY,
                    "Military",
                    required,
                    "MISSING_ROW",
                    sectionMissingMessage("Military", required)
            ), required, accumulator);
            return;
        }

        MilitarySubjectType subjectType = military.get().getMilitarySubjectType();
        if (subjectType == null) {
            addIssue(item(
                    MILITARY,
                    "Military",
                    required,
                    "TYPE_NOT_SELECTED",
                    required
                            ? "Military subject type is required before submit."
                            : "Military subject type has not been selected."
            ), required, accumulator);
            return;
        }

        if (subjectType == MilitarySubjectType.COMPLETED
                && (military.get().getServiceStartDate() == null || military.get().getServiceEndDate() == null)) {
            addIssue(item(
                    MILITARY,
                    "Military",
                    required,
                    "MISSING_PERIOD",
                    required
                            ? "Military service period is required for completed applicants before submit."
                            : "Military service period is incomplete."
            ), required, accumulator);
        }
        if ((subjectType == MilitarySubjectType.EXEMPTED || subjectType == MilitarySubjectType.SUBJECT)
                && (military.get().getNonServiceReason() == null || military.get().getNonServiceReason().isBlank())) {
            addIssue(item(
                    MILITARY,
                    "Military",
                    required,
                    "MISSING_REASON",
                    required
                            ? "Military non-service reason is required before submit."
                            : "Military non-service reason is empty."
            ), required, accumulator);
        }
    }

    private void checkQuestions(JobApplication application, ReadinessAccumulator accumulator) {
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

        boolean questionGroupAdded = false;
        for (JobPostingQuestion question : questions) {
            boolean required = Boolean.TRUE.equals(question.getRequired());
            if (required && !questionGroupAdded) {
                accumulator.addRequiredGroup(QUESTION);
                questionGroupAdded = true;
            }

            ApplicationAnswer answer = answerMap.get(question.getId());
            if (required && isMissingRequiredAnswer(answer)) {
                accumulator.addRequiredIssue(item(
                        QUESTION,
                        "Question",
                        true,
                        "MISSING_REQUIRED_ANSWER",
                        "Required question answer is missing."
                ));
            }
            if (answerLengthInvalid(question, answer)) {
                if (!questionGroupAdded) {
                    accumulator.addRequiredGroup(QUESTION);
                    questionGroupAdded = true;
                }
                accumulator.addRequiredIssue(item(
                        QUESTION,
                        "Question",
                        true,
                        "INVALID_LENGTH",
                        "Answer exceeds max length."
                ));
            }
        }
    }

    private boolean isMissingRequiredAnswer(ApplicationAnswer answer) {
        if (answer == null || answer.getAnswerText() == null) {
            return true;
        }
        return answer.getAnswerText().isBlank();
    }

    private boolean answerLengthInvalid(JobPostingQuestion question, ApplicationAnswer answer) {
        if (answer == null || answer.getAnswerText() == null) {
            return false;
        }

        String answerText = answer.getAnswerText();
        int questionMaxLength = question.getMaxLength() != null
                ? question.getMaxLength()
                : defaultMaxLength(question.getAnswerType());
        if (answerText.length() > questionMaxLength) {
            return true;
        }
        if (question.getAnswerType() == QuestionAnswerType.SHORT_TEXT && answerText.length() > SHORT_TEXT_MAX_LENGTH) {
            return true;
        }
        return question.getAnswerType() == QuestionAnswerType.LONG_TEXT && answerText.length() > LONG_TEXT_MAX_LENGTH;
    }

    private int defaultMaxLength(QuestionAnswerType answerType) {
        if (answerType == QuestionAnswerType.SHORT_TEXT) {
            return SHORT_TEXT_MAX_LENGTH;
        }
        return LONG_TEXT_MAX_LENGTH;
    }

    private void checkSimpleSection(
            boolean useSection,
            boolean requireSection,
            BooleanSupplier complete,
            String sectionCode,
            String sectionName,
            ReadinessAccumulator accumulator
    ) {
        if (!useSection) {
            return;
        }
        addGroup(sectionCode, requireSection, accumulator);
        if (!complete.getAsBoolean()) {
            addIssue(item(
                    sectionCode,
                    sectionName,
                    requireSection,
                    requireSection ? "MISSING_ROW" : "OPTIONAL_EMPTY",
                    sectionName + " section is empty."
            ), requireSection, accumulator);
        }
    }

    private void addGroup(String sectionCode, boolean required, ReadinessAccumulator accumulator) {
        if (required) {
            accumulator.addRequiredGroup(sectionCode);
            return;
        }
        accumulator.addOptionalGroup(sectionCode);
    }

    private void addIssue(
            ApplicationSectionReadinessResponse issue,
            boolean required,
            ReadinessAccumulator accumulator
    ) {
        if (required) {
            accumulator.addRequiredIssue(issue);
            return;
        }
        accumulator.addOptionalIssue(issue);
    }

    private String sectionMissingMessage(String sectionName, boolean required) {
        if (required) {
            return sectionName + " section is required before submit.";
        }
        return sectionName + " section is empty.";
    }

    private ApplicationSectionReadinessResponse item(
            String sectionCode,
            String sectionName,
            boolean required,
            String reasonCode,
            String message
    ) {
        return new ApplicationSectionReadinessResponse(
                sectionCode,
                sectionName,
                required,
                false,
                reasonCode,
                message
        );
    }

    private void checkAttachments(JobApplication application, ReadinessAccumulator accumulator) {
        List<JobPostingAttachmentRequirement> requirements = attachmentRequirementRepository
                .findByJobPostingIdOrderBySortOrderAscIdAsc(application.getJobPosting().getId());
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

        boolean hasRequired = requirements.stream().anyMatch(JobPostingAttachmentRequirement::isRequired);
        boolean hasOptionalRecommendation = requirements.stream()
                .anyMatch(requirement -> !requirement.isRequired() && requirement.getMinCount() > 0);
        if (hasRequired) {
            accumulator.addRequiredGroup(ATTACHMENT);
        } else if (hasOptionalRecommendation) {
            accumulator.addOptionalGroup(ATTACHMENT);
        }

        for (JobPostingAttachmentRequirement requirement : requirements) {
            long storedCount = storedCounts.getOrDefault(
                    new AttachmentRequirementKey(requirement.getAttachmentType(), requirement.getSectionType()),
                    0L
            );
            if (requirement.isRequired() && storedCount < requirement.getMinCount()) {
                accumulator.addRequiredIssue(item(
                        ATTACHMENT,
                        "Attachment",
                        true,
                        "REQUIRED_ATTACHMENT_MISSING",
                        requirement.getDisplayName() + " attachment is required before submit."
                ));
                continue;
            }
            if (!hasRequired
                    && !requirement.isRequired()
                    && requirement.getMinCount() > 0
                    && storedCount < requirement.getMinCount()) {
                accumulator.addOptionalIssue(item(
                        ATTACHMENT,
                        "Attachment",
                        false,
                        "OPTIONAL_ATTACHMENT_MISSING",
                        requirement.getDisplayName() + " attachment has not been uploaded."
                ));
            }
        }
    }

    public record CompletionReadinessResult(
            ApplicationCompletionSummaryResponse summary,
            List<ApplicationSectionReadinessResponse> requiredMissingSections,
            List<ApplicationSectionReadinessResponse> optionalIncompleteSections
    ) {
    }

    private static final class ReadinessAccumulator {

        private final Set<String> requiredGroups = new HashSet<>();
        private final Set<String> requiredGroupsWithIssues = new HashSet<>();
        private final Set<String> optionalGroups = new HashSet<>();
        private final Set<String> optionalGroupsWithIssues = new HashSet<>();
        private final List<ApplicationSectionReadinessResponse> requiredIssues = new java.util.ArrayList<>();
        private final List<ApplicationSectionReadinessResponse> optionalIssues = new java.util.ArrayList<>();

        private void addRequiredGroup(String sectionCode) {
            requiredGroups.add(sectionCode);
        }

        private void addRequiredIssue(ApplicationSectionReadinessResponse issue) {
            requiredGroupsWithIssues.add(issue.sectionCode());
            requiredIssues.add(issue);
        }

        private void addOptionalGroup(String sectionCode) {
            optionalGroups.add(sectionCode);
        }

        private void addOptionalIssue(ApplicationSectionReadinessResponse issue) {
            optionalGroupsWithIssues.add(issue.sectionCode());
            optionalIssues.add(issue);
        }

        private CompletionReadinessResult toResult() {
            int requiredSectionCount = requiredGroups.size();
            int completedRequiredSectionCount = requiredSectionCount - requiredGroupsWithIssues.size();
            int optionalSectionCount = optionalGroups.size();
            int completedOptionalSectionCount = optionalSectionCount - optionalGroupsWithIssues.size();
            int requiredCompletionRate = requiredSectionCount == 0
                    ? 100
                    : (completedRequiredSectionCount * 100) / requiredSectionCount;

            ApplicationCompletionSummaryResponse summary = new ApplicationCompletionSummaryResponse(
                    requiredSectionCount,
                    completedRequiredSectionCount,
                    requiredIssues.size(),
                    optionalSectionCount,
                    completedOptionalSectionCount,
                    optionalIssues.size(),
                    requiredCompletionRate,
                    requiredIssues.size()
            );
            return new CompletionReadinessResult(
                    summary,
                    List.copyOf(requiredIssues),
                    List.copyOf(optionalIssues)
            );
        }
    }

    private record AttachmentRequirementKey(
            AttachmentType attachmentType,
            ApplicationSectionType sectionType
    ) {
    }
}
