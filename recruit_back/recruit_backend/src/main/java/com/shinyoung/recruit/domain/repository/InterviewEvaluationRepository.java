package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InterviewEvaluationRepository extends JpaRepository<InterviewEvaluation, Long> {

    List<InterviewEvaluation> findByInterviewId(Long interviewId);

    /** 면접 삭제 가드 — 평가가 하나라도 붙어 있으면 삭제하지 않는다(InterviewService.delete). */
    boolean existsByInterviewId(Long interviewId);

    boolean existsByInterviewIdAndCandidateParticipantIdAndInterviewerParticipantId(
            Long interviewId,
            Long candidateParticipantId,
            Long interviewerParticipantId
    );

    @Query("""
            select evaluation
            from InterviewEvaluation evaluation
            join fetch evaluation.interview interview
            join fetch interview.stage
            join fetch evaluation.candidateParticipant candidateParticipant
            join fetch candidateParticipant.jobApplication application
            join fetch application.jobPosition
            join fetch evaluation.interviewerParticipant interviewerParticipant
            join fetch interviewerParticipant.employee
            where interview.id = :interviewId
              and interviewerParticipant.id = :interviewerParticipantId
            order by candidateParticipant.sortOrder asc nulls last, candidateParticipant.id asc, evaluation.id asc
            """)
    List<InterviewEvaluation> findByInterviewIdAndInterviewerParticipantId(
            @Param("interviewId") Long interviewId,
            @Param("interviewerParticipantId") Long interviewerParticipantId
    );

    @Query("""
            select evaluation
            from InterviewEvaluation evaluation
            join fetch evaluation.interview interview
            join fetch interview.stage
            join fetch evaluation.candidateParticipant candidateParticipant
            join fetch candidateParticipant.jobApplication application
            join fetch application.jobPosition
            join fetch evaluation.interviewerParticipant interviewerParticipant
            join fetch interviewerParticipant.employee
            where evaluation.id = :evaluationId
              and interview.id = :interviewId
              and interviewerParticipant.id = :interviewerParticipantId
            """)
    Optional<InterviewEvaluation> findDetailByIdAndInterviewIdAndInterviewerParticipantId(
            @Param("evaluationId") Long evaluationId,
            @Param("interviewId") Long interviewId,
            @Param("interviewerParticipantId") Long interviewerParticipantId
    );

    @Query("""
            select evaluation
            from InterviewEvaluation evaluation
            join fetch evaluation.interview interview
            join fetch interview.stage
            join fetch evaluation.candidateParticipant candidateParticipant
            join fetch evaluation.interviewerParticipant interviewerParticipant
            join fetch interviewerParticipant.employee
            where evaluation.id = :evaluationId
              and interview.id = :interviewId
            """)
    Optional<InterviewEvaluation> findAdminDetailByIdAndInterviewId(
            @Param("evaluationId") Long evaluationId,
            @Param("interviewId") Long interviewId
    );

    @Query("""
            select evaluation
            from InterviewEvaluation evaluation
            join fetch evaluation.interview interview
            join fetch interview.stage
            join fetch evaluation.candidateParticipant candidateParticipant
            join fetch candidateParticipant.jobApplication application
            join fetch application.jobPosition
            join fetch evaluation.interviewerParticipant interviewerParticipant
            join fetch interviewerParticipant.employee
            where interview.id = :interviewId
            order by candidateParticipant.sortOrder asc nulls last, candidateParticipant.id asc,
                     interviewerParticipant.sortOrder asc nulls last, interviewerParticipant.id asc, evaluation.id asc
            """)
    List<InterviewEvaluation> findByInterviewIdForAdmin(@Param("interviewId") Long interviewId);

    @Query("""
            select evaluation
            from InterviewEvaluation evaluation
            join fetch evaluation.interview interview
            join fetch interview.stage
            join fetch evaluation.candidateParticipant candidateParticipant
            join fetch candidateParticipant.jobApplication application
            join fetch application.jobPosition
            join fetch evaluation.interviewerParticipant interviewerParticipant
            join fetch interviewerParticipant.employee
            where evaluation.stage.id = :stageId
            order by interview.startDateTime asc, interview.id asc,
                     candidateParticipant.sortOrder asc nulls last, candidateParticipant.id asc,
                     interviewerParticipant.sortOrder asc nulls last, interviewerParticipant.id asc, evaluation.id asc
            """)
    List<InterviewEvaluation> findByStageIdForAdmin(@Param("stageId") Long stageId);

    @Query("""
            select evaluation
            from InterviewEvaluation evaluation
            join fetch evaluation.interview interview
            join fetch interview.stage
            join fetch evaluation.candidateParticipant candidateParticipant
            join fetch candidateParticipant.jobApplication application
            join fetch application.jobPosition
            join fetch evaluation.interviewerParticipant interviewerParticipant
            join fetch interviewerParticipant.employee
            where evaluation.jobApplication.id = :applicationId
            order by interview.startDateTime asc, interview.id asc,
                     interviewerParticipant.sortOrder asc nulls last, interviewerParticipant.id asc, evaluation.id asc
            """)
    List<InterviewEvaluation> findByJobApplicationIdForAdmin(@Param("applicationId") Long applicationId);
}
