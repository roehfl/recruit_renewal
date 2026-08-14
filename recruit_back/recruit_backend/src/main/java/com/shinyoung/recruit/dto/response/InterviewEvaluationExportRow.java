package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import com.shinyoung.recruit.enumeration.EvaluationStatus;

import java.time.LocalDateTime;

/**
 * Interview evaluations export 전용 평탄 row(평가 1건 = 1행, stage 레벨). 읽기 전용 export로 Phase 06
 * 경계(평가 작성/변경은 배정 면접관 본인만)를 유지한다. admin 뷰이므로 면접관 식별을 노출한다.
 *
 * <p>candidate/application/employee는 {@code findByStageIdForAdmin}에서 fetch join되므로 매핑 시점에
 * 추가 조회가 발생하지 않는다.
 */
public record InterviewEvaluationExportRow(
        Long interviewId,
        String groupName,
        String applicantName,
        String positionName,
        String interviewerName,
        EvaluationStatus status,
        EvaluationGrade grade,
        EvaluationRecommendation recommendation,
        String comment,
        LocalDateTime submittedAt
) {

    public static InterviewEvaluationExportRow from(InterviewEvaluation evaluation) {
        JobApplication application = evaluation.getCandidateParticipant().getJobApplication();
        Employee interviewer = evaluation.getInterviewerParticipant().getEmployee();
        return new InterviewEvaluationExportRow(
                evaluation.getInterview().getId(),
                evaluation.getInterview().getGroupName(),
                application.getApplicantNameSnapshot(),
                application.getJobPositionNameSnapshot(),
                interviewer != null ? interviewer.getName() : null,
                evaluation.getStatus(),
                evaluation.getGrade(),
                evaluation.getRecommendation(),
                evaluation.getComment(),
                evaluation.getSubmittedAt()
        );
    }
}
