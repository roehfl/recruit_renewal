package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import com.shinyoung.recruit.enumeration.EvaluationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "interview_evaluation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_interview_evaluation_candidate_interviewer",
                        columnNames = {"interview_id", "candidate_participant_id", "interviewer_participant_id"}
                )
        },
        indexes = {
                @Index(name = "idx_evaluation_interview", columnList = "interview_id"),
                @Index(name = "idx_evaluation_stage", columnList = "stage_id"),
                @Index(name = "idx_evaluation_job_application", columnList = "job_application_id"),
                @Index(name = "idx_evaluation_interviewer_participant", columnList = "interviewer_participant_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewEvaluation extends BaseEntity {

    public static final int COMMENT_MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_participant_id", nullable = false)
    private InterviewParticipant candidateParticipant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interviewer_participant_id", nullable = false)
    private InterviewParticipant interviewerParticipant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private EvaluationGrade grade;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private EvaluationRecommendation recommendation;

    @Column(length = COMMENT_MAX_LENGTH)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EvaluationStatus status;

    private LocalDateTime submittedAt;

    private InterviewEvaluation(
            Interview interview,
            InterviewParticipant candidateParticipant,
            InterviewParticipant interviewerParticipant
    ) {
        validate(interview, candidateParticipant, interviewerParticipant);
        this.interview = interview;
        this.candidateParticipant = candidateParticipant;
        this.interviewerParticipant = interviewerParticipant;
        this.jobApplication = candidateParticipant.getJobApplication();
        this.stage = interview.getStage();
        this.status = EvaluationStatus.DRAFT;
    }

    public static InterviewEvaluation initialize(
            Interview interview,
            InterviewParticipant candidateParticipant,
            InterviewParticipant interviewerParticipant
    ) {
        return new InterviewEvaluation(interview, candidateParticipant, interviewerParticipant);
    }

    public void updateContent(
            EvaluationGrade grade,
            EvaluationRecommendation recommendation,
            String comment
    ) {
        if (!isDraft()) {
            throw new IllegalStateException("Only DRAFT evaluation can be updated.");
        }
        validateComment(comment);
        this.grade = grade;
        this.recommendation = recommendation;
        this.comment = trimToNull(comment);
    }

    public void submit(LocalDateTime submittedAt) {
        if (!isDraft()) {
            throw new IllegalStateException("Only DRAFT evaluation can be submitted.");
        }
        if (submittedAt == null) {
            throw new IllegalArgumentException("submittedAt is required.");
        }
        if (grade == null) {
            throw new IllegalArgumentException("grade is required to submit.");
        }
        if (recommendation == null) {
            throw new IllegalArgumentException("recommendation is required to submit.");
        }
        this.status = EvaluationStatus.SUBMITTED;
        this.submittedAt = submittedAt;
    }

    public void reopen() {
        if (!isSubmitted()) {
            throw new IllegalStateException("Only SUBMITTED evaluation can be reopened.");
        }
        this.status = EvaluationStatus.DRAFT;
        this.submittedAt = null;
    }

    public boolean isDraft() {
        return status == EvaluationStatus.DRAFT;
    }

    public boolean isSubmitted() {
        return status == EvaluationStatus.SUBMITTED;
    }

    private static void validate(
            Interview interview,
            InterviewParticipant candidateParticipant,
            InterviewParticipant interviewerParticipant
    ) {
        if (interview == null) {
            throw new IllegalArgumentException("Interview is required.");
        }
        if (interview.getStage() == null) {
            throw new IllegalArgumentException("Interview stage is required.");
        }
        if (candidateParticipant == null) {
            throw new IllegalArgumentException("Candidate participant is required.");
        }
        if (interviewerParticipant == null) {
            throw new IllegalArgumentException("Interviewer participant is required.");
        }
        if (!candidateParticipant.isCandidate()) {
            throw new IllegalArgumentException("candidateParticipant must have role CANDIDATE.");
        }
        if (!interviewerParticipant.isInterviewer()) {
            throw new IllegalArgumentException("interviewerParticipant must have role INTERVIEWER.");
        }
        if (candidateParticipant.getJobApplication() == null) {
            throw new IllegalArgumentException("candidateParticipant must have a JobApplication.");
        }
        if (!belongsToInterview(interview, candidateParticipant)) {
            throw new IllegalArgumentException("candidateParticipant must belong to the same interview.");
        }
        if (!belongsToInterview(interview, interviewerParticipant)) {
            throw new IllegalArgumentException("interviewerParticipant must belong to the same interview.");
        }
    }

    private static void validateComment(String comment) {
        if (comment != null && comment.length() > COMMENT_MAX_LENGTH) {
            throw new IllegalArgumentException("comment must be at most " + COMMENT_MAX_LENGTH + " characters.");
        }
    }

    private static boolean belongsToInterview(Interview interview, InterviewParticipant participant) {
        Interview participantInterview = participant.getInterview();
        if (participantInterview == null) {
            return false;
        }
        Long interviewId = interview.getId();
        Long participantInterviewId = participantInterview.getId();
        if (interviewId != null && participantInterviewId != null) {
            return interviewId.equals(participantInterviewId);
        }
        return participantInterview == interview;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
