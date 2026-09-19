package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 메시지 발송 대상 조회 전용(message 카드 소유). 다른 도메인 리포지토리를 건드리지 않으려고 따로 둔다.
 * 모든 쿼리는 철회(WITHDRAWN)·파기(purgeResult not null) 지원서를 제외하고, 지원자(applicant)를 함께 읽는다.
 */
public interface MessageTargetRepository extends Repository<JobApplication, Long> {

    @Query("""
            select result
            from StageResult result
            join fetch result.jobApplication application
            join fetch application.applicant
            where result.stage.id = :stageId
              and result.resultStatus in :resultStatuses
              and application.status <> com.shinyoung.recruit.enumeration.JobApplicationStatus.WITHDRAWN
              and application.purgeResult is null
            order by application.id asc
            """)
    List<StageResult> findResultTargets(
            @Param("stageId") Long stageId,
            @Param("resultStatuses") Collection<StageResultStatus> resultStatuses
    );

    @Query("""
            select application
            from JobApplication application
            join fetch application.applicant
            where application.jobPosting.id = :jobPostingId
              and application.status in :statuses
              and application.purgeResult is null
            order by application.id asc
            """)
    List<JobApplication> findApplicationTargets(
            @Param("jobPostingId") Long jobPostingId,
            @Param("statuses") Collection<JobApplicationStatus> statuses
    );

    @Query("""
            select application
            from JobApplication application
            join fetch application.applicant
            where application.jobPosting.id = :jobPostingId
              and application.status in :statuses
              and application.purgeResult is null
              and exists (
                    select 1
                    from StageResult result
                    where result.jobApplication = application
                      and result.stage.id = :stageId
                      and result.resultStatus in :resultStatuses)
            order by application.id asc
            """)
    List<JobApplication> findApplicationTargetsWithStageResult(
            @Param("jobPostingId") Long jobPostingId,
            @Param("statuses") Collection<JobApplicationStatus> statuses,
            @Param("stageId") Long stageId,
            @Param("resultStatuses") Collection<StageResultStatus> resultStatuses
    );

    @Query("""
            select participant
            from InterviewParticipant participant
            join fetch participant.interview interview
            join fetch participant.jobApplication application
            join fetch application.applicant
            where interview.stage.id = :stageId
              and interview.status = com.shinyoung.recruit.enumeration.InterviewStatus.CONFIRMED
              and participant.role = com.shinyoung.recruit.enumeration.InterviewParticipantRole.CANDIDATE
              and participant.participantStatus = com.shinyoung.recruit.enumeration.InterviewParticipantStatus.ASSIGNED
              and application.status <> com.shinyoung.recruit.enumeration.JobApplicationStatus.WITHDRAWN
              and application.purgeResult is null
              and (:groupName is null or interview.groupName = :groupName)
            order by interview.startDateTime asc, interview.id asc, participant.sortOrder asc, participant.id asc
            """)
    List<InterviewParticipant> findInterviewTargets(
            @Param("stageId") Long stageId,
            @Param("groupName") String groupName
    );

    @Query("""
            select distinct interview.groupName
            from Interview interview
            where interview.stage.id = :stageId
              and interview.status = com.shinyoung.recruit.enumeration.InterviewStatus.CONFIRMED
            """)
    List<String> findConfirmedInterviewGroups(@Param("stageId") Long stageId);
}
