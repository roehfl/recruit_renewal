package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.repository.InterviewEvaluationRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.dto.response.AdminApplicationEvaluationResponse;
import com.shinyoung.recruit.dto.response.AdminEvaluationItemResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewEvaluationResponse;
import com.shinyoung.recruit.dto.response.InterviewEvaluationInitializeResponse;
import com.shinyoung.recruit.enumeration.InterviewParticipantRole;
import com.shinyoung.recruit.enumeration.InterviewParticipantStatus;
import com.shinyoung.recruit.exception.InterviewEvaluationNotFoundException;
import com.shinyoung.recruit.exception.InterviewNotFoundException;
import com.shinyoung.recruit.exception.InvalidInterviewEvaluationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewEvaluationAdminService {

    private static final Logger log = LoggerFactory.getLogger(InterviewEvaluationAdminService.class);

    private final InterviewRepository interviewRepository;
    private final InterviewParticipantRepository interviewParticipantRepository;
    private final InterviewEvaluationRepository interviewEvaluationRepository;
    private final StageRepository stageRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final Clock clock;

    @Transactional
    public InterviewEvaluationInitializeResponse initialize(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new InterviewNotFoundException(interviewId));
        if (!interview.isConfirmed()) {
            throw new InvalidInterviewEvaluationException(
                    "Evaluation can be initialized only when interview is CONFIRMED."
            );
        }

        List<InterviewParticipant> candidates = interviewParticipantRepository
                .findByInterviewIdAndRoleAndParticipantStatusOrderBySortOrderAscIdAsc(
                        interviewId,
                        InterviewParticipantRole.CANDIDATE,
                        InterviewParticipantStatus.ASSIGNED
                );
        List<InterviewParticipant> interviewers = interviewParticipantRepository
                .findByInterviewIdAndRoleAndParticipantStatusOrderBySortOrderAscIdAsc(
                        interviewId,
                        InterviewParticipantRole.INTERVIEWER,
                        InterviewParticipantStatus.ASSIGNED
                );

        int createdCount = 0;
        int alreadyExistedCount = 0;
        List<InterviewEvaluation> newEvaluations = new ArrayList<>();
        for (InterviewParticipant candidate : candidates) {
            for (InterviewParticipant interviewer : interviewers) {
                boolean exists = interviewEvaluationRepository
                        .existsByInterviewIdAndCandidateParticipantIdAndInterviewerParticipantId(
                                interviewId,
                                candidate.getId(),
                                interviewer.getId()
                        );
                if (exists) {
                    alreadyExistedCount++;
                    continue;
                }
                newEvaluations.add(InterviewEvaluation.initialize(interview, candidate, interviewer));
                createdCount++;
            }
        }
        interviewEvaluationRepository.saveAll(newEvaluations);

        return new InterviewEvaluationInitializeResponse(
                interviewId,
                createdCount,
                alreadyExistedCount,
                createdCount + alreadyExistedCount
        );
    }

    public AdminInterviewEvaluationResponse getInterviewEvaluations(Long interviewId) {
        Interview interview = interviewRepository.findAdminDetailById(interviewId)
                .orElseThrow(() -> new InterviewNotFoundException(interviewId));
        List<InterviewEvaluation> evaluations = interviewEvaluationRepository.findByInterviewIdForAdmin(interviewId);
        return AdminInterviewEvaluationResponse.of(interview, evaluations);
    }

    public List<AdminInterviewEvaluationResponse> getStageEvaluations(Long stageId) {
        if (!stageRepository.existsById(stageId)) {
            throw new StageNotFoundException("Stage not found.");
        }
        List<InterviewEvaluation> evaluations = interviewEvaluationRepository.findByStageIdForAdmin(stageId);
        return groupByInterview(evaluations).values().stream()
                .map(group -> AdminInterviewEvaluationResponse.of(group.get(0).getInterview(), group))
                .toList();
    }

    public List<AdminApplicationEvaluationResponse> getApplicationEvaluations(Long applicationId) {
        if (!jobApplicationRepository.existsById(applicationId)) {
            throw new JobApplicationNotFoundException("JobApplication not found.");
        }
        List<InterviewEvaluation> evaluations = interviewEvaluationRepository.findByJobApplicationIdForAdmin(applicationId);
        return groupByInterview(evaluations).values().stream()
                .map(AdminApplicationEvaluationResponse::from)
                .toList();
    }

    @Transactional
    public AdminEvaluationItemResponse reopen(Long interviewId, Long evaluationId, String actor) {
        validateActor(actor);
        InterviewEvaluation evaluation = interviewEvaluationRepository
                .findAdminDetailByIdAndInterviewId(evaluationId, interviewId)
                .orElseThrow(() -> new InterviewEvaluationNotFoundException(evaluationId));
        if (!evaluation.isSubmitted()) {
            throw new InvalidInterviewEvaluationException("Only SUBMITTED evaluation can be reopened.");
        }
        // Reopen returns the evaluation to DRAFT so the interviewer can edit/re-submit via the 06b APIs.
        // Those APIs require CONFIRMED interview + ASSIGNED participants, so reopening into a cancelled
        // state would produce a DRAFT that can never be re-submitted. Block it here.
        if (!evaluation.getInterview().isConfirmed()) {
            throw new InvalidInterviewEvaluationException(
                    "Evaluation can be reopened only when interview is CONFIRMED."
            );
        }
        if (!evaluation.getCandidateParticipant().isAssigned()) {
            throw new InvalidInterviewEvaluationException("Candidate participant is not assigned.");
        }
        if (!evaluation.getInterviewerParticipant().isAssigned()) {
            throw new InvalidInterviewEvaluationException("Interviewer participant is not assigned.");
        }

        LocalDateTime previousSubmittedAt = evaluation.getSubmittedAt();
        evaluation.reopen();

        // Persistent audit (ActivityLog) is deferred until the ActivityLog domain root exists.
        // Until then, the reopen action is recorded in the application audit log.
        log.info(
                "InterviewEvaluation reopened: evaluationId={}, interviewId={}, actor={}, previousSubmittedAt={}, reopenedAt={}",
                evaluationId,
                interviewId,
                actor,
                previousSubmittedAt,
                LocalDateTime.now(clock)
        );

        return AdminEvaluationItemResponse.from(evaluation);
    }

    private void validateActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidInterviewEvaluationException("Evaluation reopen actor is required.");
        }
    }

    private Map<Long, List<InterviewEvaluation>> groupByInterview(List<InterviewEvaluation> evaluations) {
        Map<Long, List<InterviewEvaluation>> byInterview = new LinkedHashMap<>();
        for (InterviewEvaluation evaluation : evaluations) {
            byInterview
                    .computeIfAbsent(evaluation.getInterview().getId(), key -> new ArrayList<>())
                    .add(evaluation);
        }
        return byInterview;
    }
}
