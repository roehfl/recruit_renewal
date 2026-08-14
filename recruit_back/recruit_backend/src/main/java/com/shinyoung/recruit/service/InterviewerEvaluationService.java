package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.repository.InterviewEvaluationRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.dto.request.InterviewEvaluationSaveRequest;
import com.shinyoung.recruit.dto.response.InterviewerEvaluationDetailResponse;
import com.shinyoung.recruit.dto.response.InterviewerEvaluationListResponse;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.exception.InterviewEvaluationNotFoundException;
import com.shinyoung.recruit.exception.InterviewNotFoundException;
import com.shinyoung.recruit.exception.InvalidInterviewEvaluationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewerEvaluationService {

    private static final List<InterviewStatus> VISIBLE_STATUSES = List.of(
            InterviewStatus.CONFIRMED,
            InterviewStatus.CANCELLED
    );

    private final InterviewParticipantRepository interviewParticipantRepository;
    private final InterviewEvaluationRepository interviewEvaluationRepository;
    private final Clock clock;

    public InterviewerEvaluationListResponse getMyEvaluations(Long employeeId, Long interviewId) {
        InterviewParticipant interviewer = findVisibleInterviewer(employeeId, interviewId);
        Interview interview = interviewer.getInterview();
        List<InterviewEvaluation> evaluations = interviewEvaluationRepository
                .findByInterviewIdAndInterviewerParticipantId(interviewId, interviewer.getId());
        return InterviewerEvaluationListResponse.from(interview, evaluations);
    }

    public InterviewerEvaluationDetailResponse getMyEvaluationDetail(
            Long employeeId,
            Long interviewId,
            Long evaluationId
    ) {
        InterviewEvaluation evaluation = findOwnedEvaluation(employeeId, interviewId, evaluationId);
        return InterviewerEvaluationDetailResponse.from(evaluation);
    }

    @Transactional
    public InterviewerEvaluationDetailResponse save(
            Long employeeId,
            Long interviewId,
            Long evaluationId,
            InterviewEvaluationSaveRequest request
    ) {
        InterviewEvaluation evaluation = findOwnedEvaluation(employeeId, interviewId, evaluationId);
        validateWritable(evaluation);
        applyContent(evaluation, request);
        return InterviewerEvaluationDetailResponse.from(evaluation);
    }

    @Transactional
    public InterviewerEvaluationDetailResponse submit(
            Long employeeId,
            Long interviewId,
            Long evaluationId,
            InterviewEvaluationSaveRequest request
    ) {
        InterviewEvaluation evaluation = findOwnedEvaluation(employeeId, interviewId, evaluationId);
        validateWritable(evaluation);
        if (request != null) {
            applyContent(evaluation, request);
        }
        if (evaluation.getGrade() == null) {
            throw new InvalidInterviewEvaluationException("grade is required to submit.");
        }
        if (evaluation.getRecommendation() == null) {
            throw new InvalidInterviewEvaluationException("recommendation is required to submit.");
        }
        evaluation.submit(LocalDateTime.now(clock));
        return InterviewerEvaluationDetailResponse.from(evaluation);
    }

    private void applyContent(InterviewEvaluation evaluation, InterviewEvaluationSaveRequest request) {
        if (request == null) {
            throw new InvalidInterviewEvaluationException("Evaluation save request is required.");
        }
        String comment = request.comment();
        if (comment != null && comment.length() > InterviewEvaluation.COMMENT_MAX_LENGTH) {
            throw new InvalidInterviewEvaluationException(
                    "comment must be at most " + InterviewEvaluation.COMMENT_MAX_LENGTH + " characters."
            );
        }
        evaluation.updateContent(request.grade(), request.recommendation(), comment);
    }

    private InterviewEvaluation findOwnedEvaluation(Long employeeId, Long interviewId, Long evaluationId) {
        // Resolve the caller's currently visible (CONFIRMED/CANCELLED) ASSIGNED interviewer participant first,
        // then scope the evaluation lookup to that participant id. This keeps detail/save/submit consistent with
        // the list endpoint and prevents a cancelled interviewer from reading an evaluation by its id.
        InterviewParticipant interviewer = findVisibleInterviewer(employeeId, interviewId);
        return interviewEvaluationRepository
                .findDetailByIdAndInterviewIdAndInterviewerParticipantId(evaluationId, interviewId, interviewer.getId())
                .orElseThrow(() -> new InterviewEvaluationNotFoundException(evaluationId));
    }

    private InterviewParticipant findVisibleInterviewer(Long employeeId, Long interviewId) {
        return interviewParticipantRepository
                .findVisibleInterviewerInterviewParticipant(employeeId, interviewId, VISIBLE_STATUSES)
                .orElseThrow(() -> new InterviewNotFoundException(interviewId));
    }

    private void validateWritable(InterviewEvaluation evaluation) {
        if (!evaluation.getInterview().isConfirmed()) {
            throw new InvalidInterviewEvaluationException(
                    "Evaluation can be modified only when interview is CONFIRMED."
            );
        }
        if (!evaluation.getInterviewerParticipant().isAssigned()) {
            throw new InvalidInterviewEvaluationException("Interviewer participant is not assigned.");
        }
        if (!evaluation.getCandidateParticipant().isAssigned()) {
            throw new InvalidInterviewEvaluationException("Candidate participant is not assigned.");
        }
        if (!evaluation.isDraft()) {
            throw new InvalidInterviewEvaluationException("Only DRAFT evaluation can be modified.");
        }
    }
}
