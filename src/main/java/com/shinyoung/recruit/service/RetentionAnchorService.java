package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.RetentionAnchorRequest;
import com.shinyoung.recruit.dto.response.RetentionAnchorResponse;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.exception.InvalidRetentionRequestException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code JobPosting.hiringEndedAt}(retention anchor) 수동 확정(Phase 09c, 설계 §5.3 / 리뷰 2차 #5).
 * "공고 마감" ≠ "채용 프로세스 종료" — 자동 세팅 없이 관리자 명령으로만 확정한다. 정정 재확정 허용.
 */
@Service
@RequiredArgsConstructor
public class RetentionAnchorService {

    private final JobPostingRepository jobPostingRepository;
    private final ActivityLogService activityLogService;
    private final AuditRequestContextResolver auditRequestContextResolver;

    @Transactional
    public RetentionAnchorResponse fixAnchor(Long jobPostingId, RetentionAnchorRequest request, String actor) {
        actor = requireActor(actor);
        // 서비스 직접 호출(배치/스케줄러 확장) 대비 최소 방어(9c 리뷰 Low 3).
        if (request == null || request.hiringEndedAt() == null) {
            throw new InvalidRetentionRequestException("hiringEndedAt은 필수입니다.");
        }
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + jobPostingId));

        jobPosting.fixHiringEndedAt(request.hiringEndedAt());

        // anchor 확정 = 커밋된 변경의 성공 증적(in-tx, ADR-0006). 확정값은 sanitize 대상 reasonMessage 로 남긴다.
        AuditActorContext context = auditRequestContextResolver.resolve(actor);
        activityLogService.recordInCurrentTx(AuditEvent.builder()
                .actorType(context.actorType())
                .actorId(context.actorId())
                .actorRoleSnapshot(context.actorRoleSnapshot())
                .actionType(AuditActionType.RETENTION_ANCHOR_SET)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.JOB_POSTING)
                .targetId(String.valueOf(jobPostingId))
                .jobPostingId(jobPostingId)
                .reasonMessage("hiringEndedAt=" + request.hiringEndedAt())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return RetentionAnchorResponse.from(jobPosting);
    }

    /** 관리자 행위 — actor 부재 시 ANONYMOUS 감사 차단(9c 리뷰 Medium 2). 스케줄러는 별도 SYSTEM 정책(후속). */
    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidRetentionRequestException("Retention actor is required.");
        }
        return actor.trim();
    }
}
