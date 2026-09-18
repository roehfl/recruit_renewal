package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.enumeration.InterviewParticipantRole;
import com.shinyoung.recruit.enumeration.InterviewParticipantStatus;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewParticipantRepository extends JpaRepository<InterviewParticipant, Long> {

    List<InterviewParticipant> findByInterviewIdOrderByRoleAscSortOrderAscIdAsc(Long interviewId);

    boolean existsByInterviewIdAndRoleAndJobApplicationId(
            Long interviewId,
            InterviewParticipantRole role,
            Long jobApplicationId
    );

    boolean existsByInterviewIdAndRoleAndEmployeeId(
            Long interviewId,
            InterviewParticipantRole role,
            Long employeeId
    );

    List<InterviewParticipant> findByJobApplicationIdAndRoleAndParticipantStatus(
            Long jobApplicationId,
            InterviewParticipantRole role,
            InterviewParticipantStatus status
    );

    List<InterviewParticipant> findByInterviewIdAndRoleAndParticipantStatusOrderBySortOrderAscIdAsc(
            Long interviewId,
            InterviewParticipantRole role,
            InterviewParticipantStatus status
    );

    List<InterviewParticipant> findByEmployeeIdAndRoleAndParticipantStatus(
            Long employeeId,
            InterviewParticipantRole role,
            InterviewParticipantStatus status
    );

    @Query("""
            select participant
            from InterviewParticipant participant
            left join fetch participant.jobApplication application
            left join fetch application.applicant
            left join fetch application.jobPosition
            left join fetch participant.employee
            where participant.interview.id = :interviewId
            order by participant.role asc, participant.sortOrder asc, participant.id asc
            """)
    List<InterviewParticipant> findByInterviewIdForAdminDetail(@Param("interviewId") Long interviewId);

    @Query("""
            select count(participant) > 0
            from InterviewParticipant participant
            join participant.interview interview
            where participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.CANDIDATE
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
              and participant.jobApplication.id = :jobApplicationId
              and interview.status = com.shinyoung.recruit.enumeration.InterviewStatus.CONFIRMED
              and interview.id <> :excludeInterviewId
              and interview.startDateTime = :startDateTime
            """)
    boolean existsCandidateConfirmedTimeCollision(
            @Param("jobApplicationId") Long jobApplicationId,
            @Param("excludeInterviewId") Long excludeInterviewId,
            @Param("startDateTime") LocalDateTime startDateTime
    );

    @Query("""
            select count(participant) > 0
            from InterviewParticipant participant
            join participant.interview interview
            where participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.INTERVIEWER
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
              and participant.employee.id = :employeeId
              and interview.status = com.shinyoung.recruit.enumeration.InterviewStatus.CONFIRMED
              and interview.id <> :excludeInterviewId
              and interview.startDateTime = :startDateTime
            """)
    boolean existsInterviewerConfirmedTimeCollision(
            @Param("employeeId") Long employeeId,
            @Param("excludeInterviewId") Long excludeInterviewId,
            @Param("startDateTime") LocalDateTime startDateTime
    );

    @Query("""
            select participant
            from InterviewParticipant participant
            join fetch participant.interview interview
            join fetch interview.jobPosting
            join fetch interview.stage
            join fetch participant.jobApplication application
            join fetch application.jobPosting
            join fetch application.jobPosition
            where participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.CANDIDATE
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
              and application.applicant.id = :applicantId
              and application.status <> com.shinyoung.recruit.enumeration.JobApplicationStatus.WITHDRAWN
              and interview.status in :visibleStatuses
              and (:status is null or interview.status = :status)
              and (:from is null or interview.startDateTime >= :from)
              and (:to is null or interview.startDateTime < :to)
            order by interview.startDateTime asc, interview.id asc, participant.id asc
            """)
    List<InterviewParticipant> findVisibleApplicantInterviewParticipants(
            @Param("applicantId") Long applicantId,
            @Param("visibleStatuses") Collection<InterviewStatus> visibleStatuses,
            @Param("status") InterviewStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select participant
            from InterviewParticipant participant
            join fetch participant.interview interview
            join fetch interview.jobPosting
            join fetch interview.stage
            join fetch participant.jobApplication application
            join fetch application.jobPosting
            join fetch application.jobPosition
            where participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.CANDIDATE
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
              and application.id = :applicationId
              and application.applicant.id = :applicantId
              and application.status <> com.shinyoung.recruit.enumeration.JobApplicationStatus.WITHDRAWN
              and interview.status in :visibleStatuses
              and (:status is null or interview.status = :status)
              and (:from is null or interview.startDateTime >= :from)
              and (:to is null or interview.startDateTime < :to)
            order by interview.startDateTime asc, interview.id asc, participant.id asc
            """)
    List<InterviewParticipant> findVisibleApplicationInterviewParticipants(
            @Param("applicantId") Long applicantId,
            @Param("applicationId") Long applicationId,
            @Param("visibleStatuses") Collection<InterviewStatus> visibleStatuses,
            @Param("status") InterviewStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select participant
            from InterviewParticipant participant
            join fetch participant.interview interview
            join fetch interview.jobPosting
            join fetch interview.stage
            join fetch participant.jobApplication application
            join fetch application.jobPosting
            join fetch application.jobPosition
            where participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.CANDIDATE
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
              and application.applicant.id = :applicantId
              and application.status <> com.shinyoung.recruit.enumeration.JobApplicationStatus.WITHDRAWN
              and interview.id = :interviewId
              and interview.status in :visibleStatuses
            """)
    Optional<InterviewParticipant> findVisibleApplicantInterviewParticipant(
            @Param("applicantId") Long applicantId,
            @Param("interviewId") Long interviewId,
            @Param("visibleStatuses") Collection<InterviewStatus> visibleStatuses
    );

    @Query("""
            select participant
            from InterviewParticipant participant
            join fetch participant.interview interview
            join fetch interview.jobPosting
            join fetch interview.stage
            where participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.INTERVIEWER
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
              and participant.employee.id = :employeeId
              and interview.status in :visibleStatuses
              and (:status is null or interview.status = :status)
              and (:from is null or interview.startDateTime >= :from)
              and (:to is null or interview.startDateTime < :to)
            order by interview.startDateTime asc, interview.id asc, participant.id asc
            """)
    List<InterviewParticipant> findVisibleInterviewerInterviewParticipants(
            @Param("employeeId") Long employeeId,
            @Param("visibleStatuses") Collection<InterviewStatus> visibleStatuses,
            @Param("status") InterviewStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select participant
            from InterviewParticipant participant
            join fetch participant.interview interview
            join fetch interview.jobPosting
            join fetch interview.stage
            where participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.INTERVIEWER
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
              and participant.employee.id = :employeeId
              and interview.id = :interviewId
              and interview.status in :visibleStatuses
            """)
    Optional<InterviewParticipant> findVisibleInterviewerInterviewParticipant(
            @Param("employeeId") Long employeeId,
            @Param("interviewId") Long interviewId,
            @Param("visibleStatuses") Collection<InterviewStatus> visibleStatuses
    );

    @Query("""
            select participant
            from InterviewParticipant participant
            join fetch participant.jobApplication application
            join fetch application.applicant
            join fetch application.jobPosition
            where participant.interview.id = :interviewId
              and participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.CANDIDATE
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
            order by participant.sortOrder asc nulls last, participant.id asc
            """)
    List<InterviewParticipant> findAssignedCandidatesByInterviewId(@Param("interviewId") Long interviewId);

    @Query("""
            select count(participant)
            from InterviewParticipant participant
            where participant.interview.id = :interviewId
              and participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.CANDIDATE
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
            """)
    long countAssignedCandidatesByInterviewId(@Param("interviewId") Long interviewId);

    /** 면접 스케줄 조회 — 단계의 취소되지 않은 면접에 배정된 지원자(지원서·모집분야 함께). */
    @Query("""
            select participant
            from InterviewParticipant participant
            join fetch participant.interview interview
            join fetch participant.jobApplication application
            join fetch application.jobPosition jobPosition
            where interview.stage.id = :stageId
              and interview.status <> com.shinyoung.recruit.enumeration.InterviewStatus.CANCELLED
              and participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.CANDIDATE
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
            """)
    List<InterviewParticipant> findScheduleCandidatesByStageId(@Param("stageId") Long stageId);

    /** 면접 스케줄 조회 — 단계의 취소되지 않은 면접에 배정된 면접관(칸 안 순서대로). */
    @Query("""
            select participant
            from InterviewParticipant participant
            join fetch participant.interview interview
            join fetch participant.employee employee
            where interview.stage.id = :stageId
              and interview.status <> com.shinyoung.recruit.enumeration.InterviewStatus.CANCELLED
              and participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.INTERVIEWER
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
            order by participant.sortOrder asc, participant.id asc
            """)
    List<InterviewParticipant> findScheduleInterviewersByStageId(@Param("stageId") Long stageId);

    /** 면접 스케줄 업로드 — 교체 대상 단계 밖의 확정 면접에 같은 면접 시각으로 배정된 지원자인지. */
    @Query("""
            select count(participant) > 0
            from InterviewParticipant participant
            join participant.interview interview
            where participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.CANDIDATE
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
              and participant.jobApplication.id = :jobApplicationId
              and interview.status = com.shinyoung.recruit.enumeration.InterviewStatus.CONFIRMED
              and interview.stage.id <> :stageId
              and interview.startDateTime = :startDateTime
            """)
    boolean existsCandidateConfirmedAtStartOutsideStage(
            @Param("jobApplicationId") Long jobApplicationId,
            @Param("stageId") Long stageId,
            @Param("startDateTime") LocalDateTime startDateTime
    );

    /** 면접 스케줄 업로드 — 교체 대상 단계 밖의 확정 면접에 같은 면접 시각으로 배정된 면접관인지. */
    @Query("""
            select count(participant) > 0
            from InterviewParticipant participant
            join participant.interview interview
            where participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.INTERVIEWER
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
              and participant.employee.id = :employeeId
              and interview.status = com.shinyoung.recruit.enumeration.InterviewStatus.CONFIRMED
              and interview.stage.id <> :stageId
              and interview.startDateTime = :startDateTime
            """)
    boolean existsInterviewerConfirmedAtStartOutsideStage(
            @Param("employeeId") Long employeeId,
            @Param("stageId") Long stageId,
            @Param("startDateTime") LocalDateTime startDateTime
    );
}
