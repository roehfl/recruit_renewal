package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationEducationSemesterGrade;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicationAnswerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationSemesterGradeRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.AdminApplicationAnswerResponse;
import com.shinyoung.recruit.dto.response.AdminApplicationStageResultResponse;
import com.shinyoung.recruit.dto.response.AdminAttachmentResponse;
import com.shinyoung.recruit.dto.response.AdminAwardResponse;
import com.shinyoung.recruit.dto.response.AdminBasicInfoResponse;
import com.shinyoung.recruit.dto.response.AdminCareerItemResponse;
import com.shinyoung.recruit.dto.response.AdminCareerResponse;
import com.shinyoung.recruit.dto.response.AdminCertificateResponse;
import com.shinyoung.recruit.dto.response.AdminEducationResponse;
import com.shinyoung.recruit.dto.response.AdminGapPeriodResponse;
import com.shinyoung.recruit.dto.response.AdminLanguageResponse;
import com.shinyoung.recruit.dto.response.AdminMilitaryResponse;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
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
    private final ApplicationBasicInfoRepository basicInfoRepository;
    private final ApplicationEducationRepository educationRepository;
    private final ApplicationEducationSemesterGradeRepository semesterGradeRepository;
    private final ApplicationCareerRepository careerRepository;
    private final ApplicationCertificateRepository certificateRepository;
    private final ApplicationLanguageRepository languageRepository;
    private final ApplicationMilitaryRepository militaryRepository;
    private final ApplicationAwardRepository awardRepository;
    private final ApplicationGapPeriodRepository gapPeriodRepository;
    private final ApplicationAttachmentRepository attachmentRepository;
    private final JobPostingQuestionRepository jobPostingQuestionRepository;
    private final ApplicationAnswerRepository applicationAnswerRepository;
    private final StageRepository stageRepository;
    private final StageResultRepository stageResultRepository;

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
        List<AdminCareerItemResponse> careers = careerRepository.findByJobApplicationIdOrderBySortOrderAscIdAsc(applicationId)
                .stream()
                .map(AdminCareerItemResponse::from)
                .toList();
        return new AdminCareerResponse(careers);
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

    public AdminBasicInfoResponse getBasicInfo(Long applicationId) {
        validateApplicationExists(applicationId);
        return basicInfoRepository.findByJobApplicationId(applicationId)
                .map(AdminBasicInfoResponse::from)
                .orElse(null);
    }

    public AdminMilitaryResponse getMilitary(Long applicationId) {
        validateApplicationExists(applicationId);
        return militaryRepository.findByJobApplicationId(applicationId)
                .map(military -> AdminMilitaryResponse.from(
                        military,
                        maskSensitiveText(military.getNonServiceReason())
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
        // soft-deleted(legacy DELETED 포함) + purge lifecycle 상태 전부 비표시(9d-2 1단계 호환).
        return attachmentRepository.findByJobApplicationIdAndPhysicalFileStatusNotInOrderBySortOrderAscIdAsc(
                        applicationId,
                        PhysicalFileStatus.HIDDEN_FROM_LISTING
                )
                .stream()
                .map(AdminAttachmentResponse::from)
                .toList();
    }

    public List<AdminApplicationAnswerResponse> getAnswers(Long applicationId) {
        JobApplication application = findApplication(applicationId);
        List<JobPostingQuestion> questions = jobPostingQuestionRepository
                .findByJobPostingIdAndActiveTrueOrderBySortOrderAscIdAsc(application.getJobPosting().getId());
        if (questions.isEmpty()) {
            return List.of();
        }

        Map<Long, ApplicationAnswer> answersByQuestionId = applicationAnswerRepository.findByJobApplicationId(applicationId)
                .stream()
                .filter(answer -> answer.getJobPostingQuestion() != null)
                .collect(Collectors.toMap(
                        answer -> answer.getJobPostingQuestion().getId(),
                        answer -> answer,
                        (first, ignored) -> first
                ));

        return questions.stream()
                .map(question -> AdminApplicationAnswerResponse.of(
                        question,
                        answersByQuestionId.get(question.getId())
                ))
                .toList();
    }

    public List<AdminApplicationStageResultResponse> getStageResults(Long applicationId) {
        JobApplication application = findApplication(applicationId);
        List<Stage> stages = stageRepository.findByJobPostingIdOrderByStageOrderAscIdAsc(application.getJobPosting().getId());
        if (stages.isEmpty()) {
            return List.of();
        }

        List<Long> stageIds = stages.stream()
                .map(Stage::getId)
                .toList();
        Map<Long, StageResult> stageResultsByStageId = stageResultRepository
                .findByJobApplicationIdAndStageIdInForTimeline(applicationId, stageIds)
                .stream()
                .collect(Collectors.toMap(
                        result -> result.getStage().getId(),
                        result -> result,
                        (first, ignored) -> first
                ));

        return stages.stream()
                .map(stage -> AdminApplicationStageResultResponse.of(
                        stage,
                        stageResultsByStageId.get(stage.getId())
                ))
                .toList();
    }

    private void validateApplicationExists(Long applicationId) {
        if (!jobApplicationRepository.existsById(applicationId)) {
            throw new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. id=" + applicationId);
        }
    }

    private JobApplication findApplication(Long applicationId) {
        return jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. id=" + applicationId));
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
