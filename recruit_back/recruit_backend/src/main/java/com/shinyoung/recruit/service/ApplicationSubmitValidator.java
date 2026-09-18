package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.ApplicationFormConfig;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
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
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
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
    private final ApplicationMilitaryRepository militaryRepository;
    private final ApplicationCertificateRepository certificateRepository;
    private final ApplicationLanguageRepository languageRepository;
    private final ApplicationAwardRepository awardRepository;
    private final ApplicationGapPeriodRepository gapPeriodRepository;
    private final JobPostingQuestionRepository jobPostingQuestionRepository;
    private final ApplicationAnswerRepository applicationAnswerRepository;
    private final JobPostingAttachmentRequirementRepository attachmentRequirementRepository;
    private final ApplicationAttachmentRepository attachmentRepository;
    private final ApplicationBasicInfoRepository basicInfoRepository;

    public void validate(JobApplication application) {
        Long applicationId = application.getId();
        validateBasicInfo(applicationId);

        ApplicationFormConfig config = application.getJobPosting().getApplicationFormConfig();
        if (config == null) {
            throw new InvalidJobApplicationException("지원서 양식 설정이 없어 제출할 수 없습니다.");
        }

        validateEducation(config, applicationId);
        validateMilitary(config, applicationId);
        validateSimpleRequiredSection(config.isUseCertificate(), config.isRequireCertificate(), () -> certificateRepository.existsByJobApplicationId(applicationId), "자격증");
        validateSimpleRequiredSection(config.isUseLanguage(), config.isRequireLanguage(), () -> languageRepository.existsByJobApplicationId(applicationId), "어학");
        validateSimpleRequiredSection(config.isUseAward(), config.isRequireAward(), () -> awardRepository.existsByJobApplicationId(applicationId), "수상");
        validateSimpleRequiredSection(config.isUseGapPeriod(), config.isRequireGapPeriod(), () -> gapPeriodRepository.existsByJobApplicationId(applicationId), "공백기간");
        validateAnswers(application);
        validateAttachmentRequirements(application);
    }

    private void validateEducation(ApplicationFormConfig config, Long applicationId) {
        if (!isRequired(config.isUseEducation(), config.isRequireEducation())) {
            return;
        }
        if (!educationRepository.existsByJobApplicationId(applicationId)) {
            throw new InvalidJobApplicationException("학력을 입력해야 제출할 수 있습니다.");
        }
    }

    private void validateMilitary(ApplicationFormConfig config, Long applicationId) {
        if (!isRequired(config.isUseMilitary(), config.isRequireMilitary())) {
            return;
        }

        ApplicationMilitary military = militaryRepository.findByJobApplicationId(applicationId)
                .orElseThrow(() -> new InvalidJobApplicationException("병역 사항을 입력해야 제출할 수 있습니다."));
        MilitarySubjectType subjectType = military.getMilitarySubjectType();
        if (subjectType == null) {
            throw new InvalidJobApplicationException("병역 구분을 선택해야 제출할 수 있습니다.");
        }

        if (subjectType == MilitarySubjectType.COMPLETED
                && (military.getServiceStartDate() == null || military.getServiceEndDate() == null)) {
            throw new InvalidJobApplicationException("군필자는 복무기간을 입력해야 제출할 수 있습니다.");
        }
        if ((subjectType == MilitarySubjectType.EXEMPTED || subjectType == MilitarySubjectType.SUBJECT)
                && (military.getNonServiceReason() == null || military.getNonServiceReason().isBlank())) {
            throw new InvalidJobApplicationException("미필·면제 사유를 입력해야 제출할 수 있습니다.");
        }
    }

    private void validateAnswers(JobApplication application) {
        List<JobPostingQuestion> questions = jobPostingQuestionRepository
                .findByJobPostingIdOrderBySortOrderAscIdAsc(application.getJobPosting().getId());
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
        String answerText = answer == null ? null : answer.getAnswerText();
        if (answerText == null || answerText.isBlank()) {
            throw new InvalidJobApplicationException("필수 질문 '" + question.getQuestionText() + "'에 답변해야 제출할 수 있습니다.");
        }
    }

    private void validateAnswerLength(JobPostingQuestion question, ApplicationAnswer answer) {
        if (answer == null || answer.getAnswerText() == null) {
            return;
        }

        String answerText = answer.getAnswerText();
        String questionText = question.getQuestionText();
        // 공백만 있는 선택 답변은 미입력으로 보고 최소 글자수를 적용하지 않는다(프론트 섹션 검증과 동일).
        int trimmedLength = answerText.strip().length();
        if (question.getMinLength() != null && trimmedLength > 0 && trimmedLength < question.getMinLength()) {
            throw new InvalidJobApplicationException(
                    "'" + questionText + "' 답변은 최소 " + question.getMinLength() + "자 이상이어야 제출할 수 있습니다.");
        }
        int questionMaxLength = question.getMaxLength() != null
                ? question.getMaxLength()
                : defaultMaxLength(question.getAnswerType());
        if (answerText.length() > questionMaxLength) {
            throw new InvalidJobApplicationException(
                    "'" + questionText + "' 답변이 최대 " + questionMaxLength + "자를 넘었습니다.");
        }
        if (question.getAnswerType() == QuestionAnswerType.SHORT_TEXT && answerText.length() > SHORT_TEXT_MAX_LENGTH) {
            throw new InvalidJobApplicationException(
                    "'" + questionText + "' 답변이 단답형 최대 " + SHORT_TEXT_MAX_LENGTH + "자를 넘었습니다.");
        }
        if (question.getAnswerType() == QuestionAnswerType.LONG_TEXT && answerText.length() > LONG_TEXT_MAX_LENGTH) {
            throw new InvalidJobApplicationException(
                    "'" + questionText + "' 답변이 서술형 최대 " + LONG_TEXT_MAX_LENGTH + "자를 넘었습니다.");
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
        throw new InvalidJobApplicationException(sectionName + "을(를) 입력해야 제출할 수 있습니다.");
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
                throw new InvalidJobApplicationException(requirement.getDisplayName() + " 첨부파일을 등록해야 제출할 수 있습니다.");
            }
        }
    }

    private void validateBasicInfo(Long applicationId) {
        ApplicationBasicInfo basicInfo = basicInfoRepository.findByJobApplicationId(applicationId)
                .orElseThrow(() -> new InvalidJobApplicationException("기본정보를 입력해야 제출할 수 있습니다."));

        if (isBlank(basicInfo.getNameKorean())
                || basicInfo.getBirthDate() == null
                || basicInfo.getNationalityType() == null
                || isBlank(basicInfo.getMobilePhone())
                || isBlank(basicInfo.getEmail())
                || basicInfo.getVeteranStatus() == null
                || basicInfo.getDisabilityStatus() == null) {
            throw new InvalidJobApplicationException("기본정보 필수 항목(성명, 생년월일, 국적, 휴대전화, 이메일, 보훈·장애 여부)을 입력해야 제출할 수 있습니다.");
        }
        if (basicInfo.getNationalityType() == NationalityType.FOREIGN && isBlank(basicInfo.getCountryCode())) {
            throw new InvalidJobApplicationException("외국 국적은 국가를 선택해야 제출할 수 있습니다.");
        }
        if (basicInfo.getDisabilityStatus() == DisabilityStatus.SUBJECT
                && (isBlank(basicInfo.getDisabilityGradeCode()) || isBlank(basicInfo.getDisabilityTypeCode()))) {
            throw new InvalidJobApplicationException("장애 대상자는 장애 등급과 유형을 선택해야 제출할 수 있습니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record AttachmentRequirementKey(
            AttachmentType attachmentType,
            ApplicationSectionType sectionType
    ) {
    }
}
