package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.dto.response.ApplicantInterviewDetailResponse;
import com.shinyoung.recruit.dto.response.ApplicantInterviewSummaryResponse;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.exception.InterviewNotFoundException;
import com.shinyoung.recruit.exception.InvalidInterviewException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicantInterviewService {

    private static final List<InterviewStatus> VISIBLE_STATUSES = List.of(
            InterviewStatus.CONFIRMED,
            InterviewStatus.CANCELLED
    );

    private final InterviewParticipantRepository interviewParticipantRepository;
    private final JobApplicationRepository jobApplicationRepository;

    public List<ApplicantInterviewSummaryResponse> getMyInterviews(
            Long applicantId,
            InterviewStatus status,
            LocalDateTime from,
            LocalDateTime to
    ) {
        validateReadCondition(status, from, to);
        return interviewParticipantRepository.findVisibleApplicantInterviewParticipants(
                        applicantId,
                        VISIBLE_STATUSES,
                        status,
                        from,
                        to
                )
                .stream()
                .map(ApplicantInterviewSummaryResponse::from)
                .toList();
    }

    public List<ApplicantInterviewSummaryResponse> getMyApplicationInterviews(
            Long applicantId,
            Long applicationId,
            InterviewStatus status,
            LocalDateTime from,
            LocalDateTime to
    ) {
        validateReadCondition(status, from, to);
        JobApplication application = jobApplicationRepository.findByIdAndApplicantId(applicationId, applicantId)
                .orElseThrow(() -> new JobApplicationNotFoundException("JobApplication not found. id=" + applicationId));
        if (application.getStatus() == JobApplicationStatus.WITHDRAWN) {
            throw new InvalidInterviewException("Withdrawn application interview schedules cannot be viewed.");
        }

        return interviewParticipantRepository.findVisibleApplicationInterviewParticipants(
                        applicantId,
                        applicationId,
                        VISIBLE_STATUSES,
                        status,
                        from,
                        to
                )
                .stream()
                .map(ApplicantInterviewSummaryResponse::from)
                .toList();
    }

    public ApplicantInterviewDetailResponse getMyInterviewDetail(Long applicantId, Long interviewId) {
        return interviewParticipantRepository.findVisibleApplicantInterviewParticipant(
                        applicantId,
                        interviewId,
                        VISIBLE_STATUSES
                )
                .map(ApplicantInterviewDetailResponse::from)
                .orElseThrow(() -> new InterviewNotFoundException(interviewId));
    }

    private void validateReadCondition(InterviewStatus status, LocalDateTime from, LocalDateTime to) {
        if (status == InterviewStatus.DRAFT) {
            throw new InvalidInterviewException("Applicant interview status filter cannot be DRAFT.");
        }
        if (from != null && to != null && !to.isAfter(from)) {
            throw new InvalidInterviewException("Search to must be after from.");
        }
    }
}
