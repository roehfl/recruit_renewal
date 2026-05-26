package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(
        name = "interview",
        indexes = {
                @Index(name = "idx_interview_job_posting_stage_status", columnList = "job_posting_id,stage_id,status"),
                @Index(name = "idx_interview_time_range", columnList = "start_date_time,end_date_time")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Interview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    @Column(nullable = false, length = 100)
    private String groupName;

    @Column(nullable = false)
    private LocalDateTime startDateTime;

    @Column(nullable = false)
    private LocalDateTime endDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InterviewMethod method;

    @Column(length = 200)
    private String locationName;

    @Column(length = 100)
    private String roomName;

    @Column(length = 500)
    private String onlineMeetingUrl;

    @Column(length = 1000)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InterviewStatus status;

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<InterviewParticipant> participants = new ArrayList<>();

    private Interview(
            JobPosting jobPosting,
            Stage stage,
            String groupName,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            InterviewMethod method,
            String locationName,
            String roomName,
            String onlineMeetingUrl,
            String memo
    ) {
        validate(jobPosting, stage, groupName, startDateTime, endDateTime, method, locationName, onlineMeetingUrl);
        this.jobPosting = jobPosting;
        this.stage = stage;
        this.groupName = groupName.trim();
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.method = method;
        this.locationName = trimToNull(locationName);
        this.roomName = trimToNull(roomName);
        this.onlineMeetingUrl = trimToNull(onlineMeetingUrl);
        this.memo = trimToNull(memo);
        this.status = InterviewStatus.DRAFT;
    }

    public static Interview createDraft(
            JobPosting jobPosting,
            Stage stage,
            String groupName,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            InterviewMethod method,
            String locationName,
            String roomName,
            String onlineMeetingUrl,
            String memo
    ) {
        return new Interview(
                jobPosting,
                stage,
                groupName,
                startDateTime,
                endDateTime,
                method,
                locationName,
                roomName,
                onlineMeetingUrl,
                memo
        );
    }

    public void updateDraft(
            String groupName,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            InterviewMethod method,
            String locationName,
            String roomName,
            String onlineMeetingUrl,
            String memo
    ) {
        if (!isDraft()) {
            throw new IllegalStateException("Only DRAFT interview can be updated.");
        }
        validate(this.jobPosting, this.stage, groupName, startDateTime, endDateTime, method, locationName, onlineMeetingUrl);
        this.groupName = groupName.trim();
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.method = method;
        this.locationName = trimToNull(locationName);
        this.roomName = trimToNull(roomName);
        this.onlineMeetingUrl = trimToNull(onlineMeetingUrl);
        this.memo = trimToNull(memo);
    }

    public void confirm() {
        this.status = InterviewStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = InterviewStatus.CANCELLED;
    }

    public void clearParticipantsForDraft() {
        if (!isDraft()) {
            throw new IllegalStateException("Only DRAFT interview participants can be replaced.");
        }
        this.participants.clear();
    }

    public boolean isDraft() {
        return status == InterviewStatus.DRAFT;
    }

    public boolean isConfirmed() {
        return status == InterviewStatus.CONFIRMED;
    }

    public boolean isCancelled() {
        return status == InterviewStatus.CANCELLED;
    }

    void addParticipant(InterviewParticipant participant) {
        this.participants.add(participant);
    }

    private static void validate(
            JobPosting jobPosting,
            Stage stage,
            String groupName,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            InterviewMethod method,
            String locationName,
            String onlineMeetingUrl
    ) {
        if (jobPosting == null) {
            throw new IllegalArgumentException("JobPosting is required.");
        }
        if (stage == null) {
            throw new IllegalArgumentException("Stage is required.");
        }
        if (!isSameJobPosting(jobPosting, stage)) {
            throw new IllegalArgumentException("Stage must belong to the same JobPosting.");
        }
        if (isBlank(groupName)) {
            throw new IllegalArgumentException("Interview groupName is required.");
        }
        if (startDateTime == null) {
            throw new IllegalArgumentException("Interview startDateTime is required.");
        }
        if (endDateTime == null) {
            throw new IllegalArgumentException("Interview endDateTime is required.");
        }
        if (!endDateTime.isAfter(startDateTime)) {
            throw new IllegalArgumentException("Interview endDateTime must be after startDateTime.");
        }
        if (method == null) {
            throw new IllegalArgumentException("Interview method is required.");
        }
        if ((method == InterviewMethod.IN_PERSON || method == InterviewMethod.HYBRID) && isBlank(locationName)) {
            throw new IllegalArgumentException("Interview locationName is required.");
        }
        if ((method == InterviewMethod.ONLINE || method == InterviewMethod.HYBRID) && isBlank(onlineMeetingUrl)) {
            throw new IllegalArgumentException("Interview onlineMeetingUrl is required.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isSameJobPosting(JobPosting jobPosting, Stage stage) {
        JobPosting stageJobPosting = stage.getJobPosting();
        if (stageJobPosting == null) {
            return false;
        }
        Long jobPostingId = jobPosting.getId();
        Long stageJobPostingId = stageJobPosting.getId();
        if (jobPostingId != null && stageJobPostingId != null) {
            return jobPostingId.equals(stageJobPostingId);
        }
        return stageJobPosting == jobPosting;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
