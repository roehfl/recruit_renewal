package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.InterviewParticipantRole;
import com.shinyoung.recruit.enumeration.InterviewParticipantStatus;
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

@Entity
@Getter
@Table(
        name = "interview_participant",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_interview_participant_candidate",
                        columnNames = {"interview_id", "role", "job_application_id"}
                ),
                @UniqueConstraint(
                        name = "uk_interview_participant_interviewer",
                        columnNames = {"interview_id", "role", "employee_id"}
                )
        },
        indexes = {
                @Index(name = "idx_interview_participant_interview", columnList = "interview_id"),
                @Index(name = "idx_interview_participant_job_application", columnList = "job_application_id"),
                @Index(name = "idx_interview_participant_employee", columnList = "employee_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewParticipant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InterviewParticipantRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id")
    private JobApplication jobApplication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InterviewParticipantStatus participantStatus;

    private Integer sortOrder;

    private InterviewParticipant(
            Interview interview,
            InterviewParticipantRole role,
            JobApplication jobApplication,
            Employee employee,
            Integer sortOrder
    ) {
        validate(interview, role, jobApplication, employee);
        this.interview = interview;
        this.role = role;
        this.jobApplication = jobApplication;
        this.employee = employee;
        this.participantStatus = InterviewParticipantStatus.ASSIGNED;
        this.sortOrder = sortOrder;
        interview.addParticipant(this);
    }

    public static InterviewParticipant candidate(
            Interview interview,
            JobApplication jobApplication,
            Integer sortOrder
    ) {
        return new InterviewParticipant(
                interview,
                InterviewParticipantRole.CANDIDATE,
                jobApplication,
                null,
                sortOrder
        );
    }

    public static InterviewParticipant interviewer(
            Interview interview,
            Employee employee,
            Integer sortOrder
    ) {
        return new InterviewParticipant(
                interview,
                InterviewParticipantRole.INTERVIEWER,
                null,
                employee,
                sortOrder
        );
    }

    public void cancel() {
        this.participantStatus = InterviewParticipantStatus.CANCELLED;
    }

    public boolean isAssigned() {
        return participantStatus == InterviewParticipantStatus.ASSIGNED;
    }

    public boolean isCandidate() {
        return role == InterviewParticipantRole.CANDIDATE;
    }

    public boolean isInterviewer() {
        return role == InterviewParticipantRole.INTERVIEWER;
    }

    private static void validate(
            Interview interview,
            InterviewParticipantRole role,
            JobApplication jobApplication,
            Employee employee
    ) {
        if (interview == null) {
            throw new IllegalArgumentException("Interview is required.");
        }
        if (role == null) {
            throw new IllegalArgumentException("Interview participant role is required.");
        }
        if (role == InterviewParticipantRole.CANDIDATE) {
            if (jobApplication == null) {
                throw new IllegalArgumentException("Candidate participant requires JobApplication.");
            }
            if (employee != null) {
                throw new IllegalArgumentException("Candidate participant must not have Employee.");
            }
        }
        if (role == InterviewParticipantRole.INTERVIEWER) {
            if (employee == null) {
                throw new IllegalArgumentException("Interviewer participant requires Employee.");
            }
            if (jobApplication != null) {
                throw new IllegalArgumentException("Interviewer participant must not have JobApplication.");
            }
        }
    }
}
