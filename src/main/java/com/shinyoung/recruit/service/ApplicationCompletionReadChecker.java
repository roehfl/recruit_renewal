package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.ApplicationCareerProfile;
import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.domain.repository.ApplicationAnswerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerProfileRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.dto.response.ApplicationCompletionSummaryResponse;
import com.shinyoung.recruit.dto.response.ApplicationSectionReadinessResponse;
import com.shinyoung.recruit.enumeration.CareerType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationCompletionReadChecker {

    private static final int SHORT_TEXT_MAX_LENGTH = 500;
    private static final int LONG_TEXT_MAX_LENGTH = 5000;

    private static final String EDUCATION = "EDUCATION";
    private static final String CAREER = "CAREER";
    private static final String MILITARY = "MILITARY";
    private static final String QUESTION = "QUESTION";
    private static final String FORM_CONFIG = "FORM_CONFIG";
    private static final String CERTIFICATE = "CERTIFICATE";
    private static final String LANGUAGE = "LANGUAGE";
    private static final String AWARD = "AWARD";
    private static final String GAP_PERIOD = "GAP_PERIOD";

    private final ApplicationEducationRepository educationRepository;
    private final ApplicationCareerProfileRepository careerProfileRepository;
    private final ApplicationCareerRepository careerRepository;
    private final ApplicationMilitaryRepository militaryRepository;
    private final JobPostingQuestionRepository jobPostingQuestionRepository;
    private final ApplicationAnswerRepository applicationAnswerRepository;
    private final ApplicationCertificateRepository certificateRepository;
    private final ApplicationLanguageRepository languageRepository;
    private final ApplicationAwardRepository awardRepository;
    private final ApplicationGapPeriodRepository gapPeriodRepository;

    public CompletionReadinessResult check(JobApplication application) {
        Long applicationId = application.getId();
        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();

        ReadinessAccumulator accumulator = new ReadinessAccumulator();
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
            checkCareer(config, applicationId, accumulator);
            checkMilitary(config, applicationId, accumulator);
            checkOptionalSections(config, applicationId, accumulator);
        }
        checkQuestions(application, accumulator);

        return accumulator.toResult();
    }

    private void checkEducation(ApplicationFormConfig config, Long applicationId, ReadinessAccumulator accumulator) {
        if (!config.isUseEducation()) {
            return;
        }
        accumulator.addRequiredGroup(EDUCATION);
        if (!educationRepository.existsByJobApplicationId(applicationId)) {
            accumulator.addRequiredIssue(item(
                    EDUCATION,
                    "Education",
                    true,
                    "MISSING_ROW",
                    "Education section is required before submit."
            ));
        }
    }

    private void checkCareer(ApplicationFormConfig config, Long applicationId, ReadinessAccumulator accumulator) {
        if (!config.isUseCareer()) {
            return;
        }
        accumulator.addRequiredGroup(CAREER);

        Optional<ApplicationCareerProfile> profile = careerProfileRepository.findByJobApplicationId(applicationId);
        if (profile.isEmpty()) {
            accumulator.addRequiredIssue(item(
                    CAREER,
                    "Career",
                    true,
                    "MISSING_PROFILE",
                    "Career profile is required before submit."
            ));
            return;
        }

        CareerType careerType = profile.get().getCareerType();
        if (careerType == null || careerType == CareerType.NOT_SELECTED) {
            accumulator.addRequiredIssue(item(
                    CAREER,
                    "Career",
                    true,
                    "TYPE_NOT_SELECTED",
                    "Career type must be selected before submit."
            ));
            return;
        }

        boolean hasCareerRows = careerRepository.existsByJobApplicationId(applicationId);
        if (careerType == CareerType.EXPERIENCED && !hasCareerRows) {
            accumulator.addRequiredIssue(item(
                    CAREER,
                    "Career",
                    true,
                    "MISSING_ROW",
                    "Career rows are required for experienced applicants before submit."
            ));
        }
        if ((careerType == CareerType.NEWCOMER || careerType == CareerType.NOT_APPLICABLE) && hasCareerRows) {
            accumulator.addRequiredIssue(item(
                    CAREER,
                    "Career",
                    true,
                    "INVALID_DISALLOWED_ROW",
                    "Career rows are not allowed for the selected career type before submit."
            ));
        }
    }

    private void checkMilitary(ApplicationFormConfig config, Long applicationId, ReadinessAccumulator accumulator) {
        if (!config.isUseMilitary()) {
            return;
        }
        accumulator.addRequiredGroup(MILITARY);

        Optional<ApplicationMilitary> military = militaryRepository.findByJobApplicationId(applicationId);
        if (military.isEmpty()) {
            accumulator.addRequiredIssue(item(
                    MILITARY,
                    "Military",
                    true,
                    "MISSING_ROW",
                    "Military section is required before submit."
            ));
            return;
        }

        MilitarySubjectType subjectType = military.get().getMilitarySubjectType();
        if (subjectType == null) {
            accumulator.addRequiredIssue(item(
                    MILITARY,
                    "Military",
                    true,
                    "TYPE_NOT_SELECTED",
                    "Military subject type is required before submit."
            ));
            return;
        }

        if (subjectType == MilitarySubjectType.COMPLETED
                && (military.get().getServiceStartDate() == null || military.get().getServiceEndDate() == null)) {
            accumulator.addRequiredIssue(item(
                    MILITARY,
                    "Military",
                    true,
                    "MISSING_PERIOD",
                    "Military service period is required for completed applicants before submit."
            ));
        }
        if (subjectType == MilitarySubjectType.EXEMPTED
                && (military.get().getExemptionReason() == null || military.get().getExemptionReason().isBlank())) {
            accumulator.addRequiredIssue(item(
                    MILITARY,
                    "Military",
                    true,
                    "MISSING_REASON",
                    "Military exemption reason is required before submit."
            ));
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

    private void checkOptionalSections(ApplicationFormConfig config, Long applicationId, ReadinessAccumulator accumulator) {
        if (config.isUseCertificate()) {
            checkOptionalSection(
                    certificateRepository.existsByJobApplicationId(applicationId),
                    CERTIFICATE,
                    "Certificate",
                    accumulator
            );
        }
        if (config.isUseLanguage()) {
            checkOptionalSection(
                    languageRepository.existsByJobApplicationId(applicationId),
                    LANGUAGE,
                    "Language",
                    accumulator
            );
        }
        if (config.isUseAward()) {
            checkOptionalSection(
                    awardRepository.existsByJobApplicationId(applicationId),
                    AWARD,
                    "Award",
                    accumulator
            );
        }
        if (config.isUseGapPeriod()) {
            checkOptionalSection(
                    gapPeriodRepository.existsByJobApplicationId(applicationId),
                    GAP_PERIOD,
                    "Gap period",
                    accumulator
            );
        }
    }

    private void checkOptionalSection(
            boolean complete,
            String sectionCode,
            String sectionName,
            ReadinessAccumulator accumulator
    ) {
        accumulator.addOptionalGroup(sectionCode);
        if (!complete) {
            accumulator.addOptionalIssue(item(
                    sectionCode,
                    sectionName,
                    false,
                    "OPTIONAL_EMPTY",
                    sectionName + " section is empty."
            ));
        }
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
}
