package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.RetentionHold;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.RetentionHoldRepository;
import com.shinyoung.recruit.dto.request.RetentionHoldCreateRequest;
import com.shinyoung.recruit.dto.response.RetentionHoldResponse;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.exception.InvalidRetentionHoldException;
import com.shinyoung.recruit.exception.InvalidRetentionRequestException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import com.shinyoung.recruit.exception.RetentionHoldNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RetentionHold(보존 예외) 관리(Phase 09c). <b>관리자 수동 hold 만</b>(설계 §5.3, 리뷰 2차 #6).
 * release 는 행 삭제가 아니라 releasedAt 마킹. hold 사유 원문은 도메인만 보유 — ActivityLog 미기록.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetentionHoldService {

    private final RetentionHoldRepository retentionHoldRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ActivityLogService activityLogService;
    private final AuditRequestContextResolver auditRequestContextResolver;
    private final Clock clock;

    public List<RetentionHoldResponse> getHolds() {
        return retentionHoldRepository.findAllByOrderByIdDesc().stream()
                .map(RetentionHoldResponse::from)
                .toList();
    }

    @Transactional
    public RetentionHoldResponse set(RetentionHoldCreateRequest request, String actor) {
        actor = requireActor(actor);
        // 서비스 직접 호출(배치/스케줄러 확장) 대비 최소 방어(9c 리뷰 Low 3).
        if (request == null || request.applicationId() == null) {
            throw new InvalidRetentionHoldException("applicationId는 필수입니다.");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new InvalidRetentionHoldException("reason은 필수입니다.");
        }
        if (!jobApplicationRepository.existsById(request.applicationId())) {
            throw new JobApplicationNotFoundException("지원서를 찾을 수 없습니다.");
        }
        if (retentionHoldRepository.existsByApplicationIdAndReleasedAtIsNull(request.applicationId())) {
            throw new InvalidRetentionHoldException("이미 active hold가 존재합니다.");
        }

        RetentionHold hold = retentionHoldRepository.save(
                RetentionHold.create(request.applicationId(), request.reason().trim(), actor));
        recordHoldAudit(AuditActionType.RETENTION_HOLD_SET, hold, actor);
        return RetentionHoldResponse.from(hold);
    }

    @Transactional
    public RetentionHoldResponse release(Long holdId, String actor) {
        actor = requireActor(actor);
        RetentionHold hold = retentionHoldRepository.findById(holdId)
                .orElseThrow(() -> new RetentionHoldNotFoundException("RetentionHold를 찾을 수 없습니다."));
        if (!hold.isActive()) {
            throw new InvalidRetentionHoldException("이미 해제된 hold입니다.");
        }

        hold.release(actor, LocalDateTime.now(clock));
        recordHoldAudit(AuditActionType.RETENTION_HOLD_RELEASE, hold, actor);
        return RetentionHoldResponse.from(hold);
    }

    /** 관리자 행위 — actor 부재 시 ANONYMOUS 감사 차단(9c 리뷰 Medium 2). 스케줄러는 별도 SYSTEM 정책(후속). */
    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidRetentionRequestException("Retention actor is required.");
        }
        return actor.trim();
    }

    /** hold set/release = 커밋된 변경의 성공 증적(in-tx, ADR-0006). 사유 원문 미기록. */
    private void recordHoldAudit(AuditActionType actionType, RetentionHold hold, String actor) {
        AuditActorContext context = auditRequestContextResolver.resolve(actor);
        activityLogService.recordInCurrentTx(AuditEvent.builder()
                .actorType(context.actorType())
                .actorId(context.actorId())
                .actorRoleSnapshot(context.actorRoleSnapshot())
                .actionType(actionType)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.RETENTION_HOLD)
                .targetId(String.valueOf(hold.getId()))
                .applicationId(hold.getApplicationId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }
}
