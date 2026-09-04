package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.InterviewEvaluationRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.InterviewCandidateParticipantRequest;
import com.shinyoung.recruit.dto.request.InterviewCreateRequest;
import com.shinyoung.recruit.dto.request.InterviewInterviewerParticipantRequest;
import com.shinyoung.recruit.dto.request.InterviewParticipantReplaceRequest;
import com.shinyoung.recruit.dto.request.InterviewUpdateRequest;
import com.shinyoung.recruit.dto.response.AdminInterviewDetailResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewSummaryResponse;
import com.shinyoung.recruit.enumeration.InterviewParticipantRole;
import com.shinyoung.recruit.enumeration.InterviewParticipantStatus;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InterviewNotFoundException;
import com.shinyoung.recruit.exception.InvalidInterviewException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewService {

    private static final Set<StageType> INTERVIEW_STAGE_TYPES = EnumSet.of(
            StageType.FIRST_INTERVIEW,
            StageType.SECOND_INTERVIEW,
            StageType.FINAL_INTERVIEW
    );
    private static final Set<StageStatus> MUTABLE_STAGE_STATUSES = EnumSet.of(
            StageStatus.READY,
            StageStatus.IN_PROGRESS
    );
    private static final Set<StageStatus> PREVIOUS_RESULT_VISIBLE_STATUSES = EnumSet.of(
            StageStatus.RESULT_ANNOUNCED,
            StageStatus.CLOSED
    );

    private final InterviewRepository interviewRepository;
    private final InterviewParticipantRepository interviewParticipantRepository;
    private final JobPostingRepository jobPostingRepository;
    private final StageRepository stageRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final EmployeeRepository employeeRepository;
    private final StageResultRepository stageResultRepository;
    private final InterviewEvaluationRepository interviewEvaluationRepository;

    public List<AdminInterviewSummaryResponse> getAdminInterviews(
            Long jobPostingId,
            Long stageId,
            InterviewStatus status,
            LocalDateTime from,
            LocalDateTime to
    ) {
        findJobPosting(jobPostingId);
        validateSearchRange(from, to);

        return interviewRepository.searchAdminInterviews(jobPostingId, stageId, status, from, to).stream()
                .map(interview -> {
                    List<InterviewParticipant> participants =
                            interviewParticipantRepository.findByInterviewIdForAdminDetail(interview.getId());
                    long candidateCount = countByRole(participants, InterviewParticipantRole.CANDIDATE);
                    long interviewerCount = countByRole(participants, InterviewParticipantRole.INTERVIEWER);
                    return AdminInterviewSummaryResponse.from(interview, candidateCount, interviewerCount);
                })
                .toList();
    }

    public AdminInterviewDetailResponse getAdminInterview(Long interviewId) {
        Interview interview = findInterview(interviewId);
        List<InterviewParticipant> participants =
                interviewParticipantRepository.findByInterviewIdForAdminDetail(interviewId);
        return AdminInterviewDetailResponse.from(interview, participants);
    }

    @Transactional
    public Long createDraft(Long jobPostingId, InterviewCreateRequest request) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        Stage stage = stageRepository.findByIdAndJobPostingId(request.stageId(), jobPostingId)
                .orElseThrow(() -> new StageNotFoundException("Stage not found. id=" + request.stageId()));
        validateInterviewStage(stage);
        validateStageMutable(stage);

        Interview interview = Interview.createDraft(
                jobPosting,
                stage,
                request.groupName(),
                request.startDateTime(),
                request.endDateTime(),
                request.method(),
                request.locationName(),
                request.roomName(),
                request.onlineMeetingUrl(),
                request.memo()
        );
        return interviewRepository.save(interview).getId();
    }

    @Transactional
    public Long updateDraft(Long interviewId, InterviewUpdateRequest request) {
        Interview interview = findInterview(interviewId);
        validateDraft(interview);
        validateInterviewStage(interview.getStage());
        validateStageMutable(interview.getStage());

        interview.updateDraft(
                request.groupName(),
                request.startDateTime(),
                request.endDateTime(),
                request.method(),
                request.locationName(),
                request.roomName(),
                request.onlineMeetingUrl(),
                request.memo()
        );
        return interview.getId();
    }

    @Transactional
    public Long replaceParticipants(Long interviewId, InterviewParticipantReplaceRequest request) {
        Interview interview = findInterview(interviewId);
        validateDraft(interview);
        validateInterviewStage(interview.getStage());
        validateStageMutable(interview.getStage());
        validateParticipantReplaceRequest(request);

        interview.clearParticipantsForDraft();
        interviewParticipantRepository.flush();

        List<InterviewParticipant> newParticipants = new ArrayList<>();
        for (InterviewCandidateParticipantRequest candidateRequest : request.candidates()) {
            JobApplication application = findApplication(candidateRequest.jobApplicationId());
            validateCandidateBelongsToJobPosting(interview, application);
            newParticipants.add(InterviewParticipant.candidate(
                    interview,
                    application,
                    candidateRequest.sortOrder()
            ));
        }
        for (InterviewInterviewerParticipantRequest interviewerRequest : request.interviewers()) {
            Employee employee = employeeRepository.findById(interviewerRequest.employeeId())
                    .orElseThrow(() -> new InvalidInterviewException(
                            "Employee not found. id=" + interviewerRequest.employeeId()
                    ));
            newParticipants.add(InterviewParticipant.interviewer(
                    interview,
                    employee,
                    interviewerRequest.sortOrder()
            ));
        }
        interviewParticipantRepository.saveAll(newParticipants);
        return interview.getId();
    }

    @Transactional
    public Long confirm(Long interviewId) {
        Interview interview = findInterview(interviewId);
        validateDraft(interview);
        validateInterviewStage(interview.getStage());
        validateStageMutable(interview.getStage());

        List<InterviewParticipant> participants =
                interviewParticipantRepository.findByInterviewIdForAdminDetail(interviewId);
        validateConfirmParticipants(interview, participants);
        interview.confirm();
        return interview.getId();
    }

    @Transactional
    public Long cancel(Long interviewId) {
        Interview interview = findInterview(interviewId);
        if (!interview.isConfirmed()) {
            throw new InvalidInterviewException("Only CONFIRMED interview can be cancelled.");
        }
        if (!MUTABLE_STAGE_STATUSES.contains(interview.getStage().getStatus())) {
            throw new InvalidInterviewException("Interview cannot be cancelled for current stage status.");
        }
        interview.cancel();
        return interview.getId();
    }

    /**
     * 면접 물리 삭제. 확정 면접이 붙은 READY 단계를 지울 수 없던 막다른 길을 푸는 경로다
     * (StageService.delete 는 DRAFT 가 아닌 면접이 있으면 차단한다).
     *
     * <p>DRAFT(외부 비노출) 와 CANCELLED(취소가 이미 지원자·면접관에게 노출됨) 만 허용한다. CONFIRMED 를 바로
     * 지우면 통보 없이 약속이 사라지므로 cancel 을 먼저 요구한다. 평가가 하나라도 있으면 판정 데이터이므로 막는다
     * (DRAFT 평가도 면접관이 임시 저장한 내용을 담을 수 있다). 참가자는 Interview 의 cascade + orphanRemoval 로
     * 함께 정리된다.
     */
    @Transactional
    public Long delete(Long interviewId) {
        Interview interview = findInterview(interviewId);
        if (interview.isConfirmed()) {
            throw new InvalidInterviewException("CONFIRMED interview must be cancelled before delete.");
        }
        validateStageMutable(interview.getStage());
        if (interviewEvaluationRepository.existsByInterviewId(interviewId)) {
            throw new InvalidInterviewException("Interview with evaluation cannot be deleted.");
        }
        interviewRepository.delete(interview);
        return interviewId;
    }

    private Interview findInterview(Long interviewId) {
        return interviewRepository.findAdminDetailById(interviewId)
                .orElseThrow(() -> new InterviewNotFoundException(interviewId));
    }

    private JobPosting findJobPosting(Long jobPostingId) {
        return jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("JobPosting not found. id=" + jobPostingId));
    }

    private JobApplication findApplication(Long jobApplicationId) {
        return jobApplicationRepository.findAdminDetailById(jobApplicationId)
                .orElseThrow(() -> new JobApplicationNotFoundException(
                        "JobApplication not found. id=" + jobApplicationId
                ));
    }

    private void validateSearchRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw new InvalidInterviewException("Search to must be after from.");
        }
    }

    private void validateDraft(Interview interview) {
        if (!interview.isDraft()) {
            throw new InvalidInterviewException("Only DRAFT interview can be changed.");
        }
    }

    private void validateInterviewStage(Stage stage) {
        if (!INTERVIEW_STAGE_TYPES.contains(stage.getStageType())) {
            throw new InvalidInterviewException("Interview can be scheduled only for interview stage types.");
        }
    }

    private void validateStageMutable(Stage stage) {
        if (!MUTABLE_STAGE_STATUSES.contains(stage.getStatus())) {
            throw new InvalidInterviewException("Interview cannot be changed for current stage status.");
        }
    }

    private void validateParticipantReplaceRequest(InterviewParticipantReplaceRequest request) {
        if (request == null || request.candidates() == null || request.interviewers() == null) {
            throw new InvalidInterviewException("Candidate and interviewer participant lists are required.");
        }
        if (request.candidates().stream().anyMatch(Objects::isNull)
                || request.interviewers().stream().anyMatch(Objects::isNull)) {
            throw new InvalidInterviewException("Participant item must not be null.");
        }
        validateDuplicateIds(
                request.candidates().stream()
                        .map(InterviewCandidateParticipantRequest::jobApplicationId)
                        .toList(),
                "Duplicate candidate participant is not allowed."
        );
        validateDuplicateIds(
                request.interviewers().stream()
                        .map(InterviewInterviewerParticipantRequest::employeeId)
                        .toList(),
                "Duplicate interviewer participant is not allowed."
        );
        validateDuplicateSortOrders(
                request.candidates().stream()
                        .map(InterviewCandidateParticipantRequest::sortOrder)
                        .toList(),
                "Duplicate candidate sort order is not allowed."
        );
        validateDuplicateSortOrders(
                request.interviewers().stream()
                        .map(InterviewInterviewerParticipantRequest::sortOrder)
                        .toList(),
                "Duplicate interviewer sort order is not allowed."
        );
    }

    private void validateDuplicateIds(List<Long> ids, String message) {
        Set<Long> uniqueIds = new HashSet<>();
        for (Long id : ids) {
            if (id == null || !uniqueIds.add(id)) {
                throw new InvalidInterviewException(message);
            }
        }
    }

    private void validateDuplicateSortOrders(List<Integer> sortOrders, String message) {
        Set<Integer> uniqueSortOrders = new HashSet<>();
        for (Integer sortOrder : sortOrders) {
            if (sortOrder != null && !uniqueSortOrders.add(sortOrder)) {
                throw new InvalidInterviewException(message);
            }
        }
    }

    private void validateCandidateBelongsToJobPosting(Interview interview, JobApplication application) {
        if (!Objects.equals(application.getJobPosting().getId(), interview.getJobPosting().getId())) {
            throw new InvalidInterviewException("Candidate application must belong to the interview JobPosting.");
        }
    }

    private void validateConfirmParticipants(Interview interview, List<InterviewParticipant> participants) {
        List<InterviewParticipant> candidates = participants.stream()
                .filter(participant -> participant.getRole() == InterviewParticipantRole.CANDIDATE)
                .filter(participant -> participant.getParticipantStatus() == InterviewParticipantStatus.ASSIGNED)
                .toList();
        List<InterviewParticipant> interviewers = participants.stream()
                .filter(participant -> participant.getRole() == InterviewParticipantRole.INTERVIEWER)
                .filter(participant -> participant.getParticipantStatus() == InterviewParticipantStatus.ASSIGNED)
                .toList();

        if (candidates.isEmpty()) {
            throw new InvalidInterviewException("At least one candidate is required to confirm interview.");
        }
        if (interviewers.isEmpty()) {
            throw new InvalidInterviewException("At least one interviewer is required to confirm interview.");
        }

        validateParticipantDuplicates(candidates, interviewers);
        for (InterviewParticipant candidate : candidates) {
            JobApplication application = candidate.getJobApplication();
            validateCandidateBelongsToJobPosting(interview, application);
            validateCandidateSubmitted(application);
            validatePreviousStagePassed(interview.getStage(), application);
            validateCandidateTimeCollision(interview, application);
        }
        for (InterviewParticipant interviewer : interviewers) {
            validateInterviewerTimeCollision(interview, interviewer.getEmployee());
        }
    }

    private void validateParticipantDuplicates(
            List<InterviewParticipant> candidates,
            List<InterviewParticipant> interviewers
    ) {
        validateDuplicateIds(
                candidates.stream()
                        .map(participant -> participant.getJobApplication().getId())
                        .toList(),
                "Duplicate candidate participant is not allowed."
        );
        validateDuplicateIds(
                interviewers.stream()
                        .map(participant -> participant.getEmployee().getId())
                        .toList(),
                "Duplicate interviewer participant is not allowed."
        );
        validateDuplicateSortOrders(
                candidates.stream()
                        .map(InterviewParticipant::getSortOrder)
                        .toList(),
                "Duplicate candidate sort order is not allowed."
        );
        validateDuplicateSortOrders(
                interviewers.stream()
                        .map(InterviewParticipant::getSortOrder)
                        .toList(),
                "Duplicate interviewer sort order is not allowed."
        );
    }

    private void validateCandidateSubmitted(JobApplication application) {
        if (application.getStatus() != JobApplicationStatus.SUBMITTED) {
            throw new InvalidInterviewException("Only SUBMITTED candidate application can be assigned.");
        }
    }

    private void validatePreviousStagePassed(Stage currentStage, JobApplication application) {
        Stage previousStage = stageRepository.findByJobPostingIdOrderByStageOrderAscIdAsc(
                        currentStage.getJobPosting().getId()
                ).stream()
                .filter(stage -> stage.getStageOrder() < currentStage.getStageOrder())
                .reduce((first, second) -> second)
                .orElseThrow(() -> new InvalidInterviewException(
                        "Previous stage result is required before confirming interview."
                ));

        if (!PREVIOUS_RESULT_VISIBLE_STATUSES.contains(previousStage.getStatus())) {
            throw new InvalidInterviewException("Previous stage result must be announced before confirming interview.");
        }

        StageResult result = stageResultRepository.findByStageIdAndJobApplicationId(
                        previousStage.getId(),
                        application.getId()
                )
                .orElseThrow(() -> new InvalidInterviewException(
                        "Previous stage passed result is required before confirming interview."
                ));
        if (result.getResultStatus() != StageResultStatus.PASSED) {
            throw new InvalidInterviewException("Only previous-stage PASSED candidate can be confirmed.");
        }
    }

    private void validateCandidateTimeCollision(Interview interview, JobApplication application) {
        boolean collision = interviewParticipantRepository.existsCandidateConfirmedTimeCollision(
                application.getId(),
                interview.getId(),
                interview.getStartDateTime(),
                interview.getEndDateTime()
        );
        if (collision) {
            throw new InvalidInterviewException("Candidate has another confirmed interview in the same time range.");
        }
    }

    private void validateInterviewerTimeCollision(Interview interview, Employee employee) {
        boolean collision = interviewParticipantRepository.existsInterviewerConfirmedTimeCollision(
                employee.getId(),
                interview.getId(),
                interview.getStartDateTime(),
                interview.getEndDateTime()
        );
        if (collision) {
            throw new InvalidInterviewException("Interviewer has another confirmed interview in the same time range.");
        }
    }

    private long countByRole(List<InterviewParticipant> participants, InterviewParticipantRole role) {
        return participants.stream()
                .filter(participant -> participant.getRole() == role)
                .count();
    }
}
