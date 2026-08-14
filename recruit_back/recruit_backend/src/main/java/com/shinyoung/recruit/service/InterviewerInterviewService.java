package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.dto.response.InterviewerInterviewCandidateResponse;
import com.shinyoung.recruit.dto.response.InterviewerInterviewDetailResponse;
import com.shinyoung.recruit.dto.response.InterviewerInterviewSummaryResponse;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.exception.InterviewNotFoundException;
import com.shinyoung.recruit.exception.InvalidInterviewException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewerInterviewService {

    private static final List<InterviewStatus> VISIBLE_STATUSES = List.of(
            InterviewStatus.CONFIRMED,
            InterviewStatus.CANCELLED
    );

    private final InterviewParticipantRepository interviewParticipantRepository;

    public List<InterviewerInterviewSummaryResponse> getMyInterviews(
            Long employeeId,
            InterviewStatus status,
            LocalDateTime from,
            LocalDateTime to
    ) {
        validateReadCondition(status, from, to);
        return interviewParticipantRepository.findVisibleInterviewerInterviewParticipants(
                        employeeId,
                        VISIBLE_STATUSES,
                        status,
                        from,
                        to
                )
                .stream()
                .map(participant -> InterviewerInterviewSummaryResponse.from(
                        participant,
                        interviewParticipantRepository.countAssignedCandidatesByInterviewId(participant.getInterview().getId())
                ))
                .toList();
    }

    public InterviewerInterviewDetailResponse getMyInterviewDetail(Long employeeId, Long interviewId) {
        var interviewer = interviewParticipantRepository.findVisibleInterviewerInterviewParticipant(
                        employeeId,
                        interviewId,
                        VISIBLE_STATUSES
                )
                .orElseThrow(() -> new InterviewNotFoundException(interviewId));
        List<InterviewerInterviewCandidateResponse> candidates = interviewParticipantRepository
                .findAssignedCandidatesByInterviewId(interviewId)
                .stream()
                .map(InterviewerInterviewCandidateResponse::from)
                .toList();

        return InterviewerInterviewDetailResponse.from(interviewer, candidates);
    }

    private void validateReadCondition(InterviewStatus status, LocalDateTime from, LocalDateTime to) {
        if (status == InterviewStatus.DRAFT) {
            throw new InvalidInterviewException("Interviewer interview status filter cannot be DRAFT.");
        }
        if (from != null && to != null && !to.isAfter(from)) {
            throw new InvalidInterviewException("Search to must be after from.");
        }
    }
}
