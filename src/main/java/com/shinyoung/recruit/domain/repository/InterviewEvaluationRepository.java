package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewEvaluationRepository extends JpaRepository<InterviewEvaluation, Long> {

    List<InterviewEvaluation> findByInterviewId(Long interviewId);

    boolean existsByInterviewIdAndCandidateParticipantIdAndInterviewerParticipantId(
            Long interviewId,
            Long candidateParticipantId,
            Long interviewerParticipantId
    );
}
