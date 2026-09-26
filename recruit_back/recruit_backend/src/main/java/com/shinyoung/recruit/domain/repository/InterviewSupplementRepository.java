package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.InterviewSupplement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewSupplementRepository extends JpaRepository<InterviewSupplement, Long> {

    Optional<InterviewSupplement> findByStageId(Long stageId);

    List<InterviewSupplement> findByStageIdIn(Collection<Long> stageIds);

    /** 추가사항 대상 지원자: 단계의 CONFIRMED 면접에 배정된(ASSIGNED) 철회하지 않은 지원서. 조 시각 → 면접순서 순. */
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
            order by interview.startDateTime asc, interview.id asc, participant.sortOrder asc, participant.id asc
            """)
    List<InterviewParticipant> findCandidateParticipantsByStageId(@Param("stageId") Long stageId);
}
