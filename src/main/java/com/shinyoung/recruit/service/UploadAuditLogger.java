package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.dto.response.StageResultUploadCommitResponse;
import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Excel upload commit 감사 adapter(Phase 09b dual-write). 영속 ActivityLog 가 source of truth,
 * SLF4J 는 보조.
 *
 * <p>트랜잭션 경로(ADR-0006): {@code APPLIED}(커밋된 변경 성공)는 <b>비즈니스 tx 안</b>에서
 * {@code recordInCurrentTx} — 따라서 {@code logUploadCommit} 은 {@code StageResultUploadService.commit}
 * 트랜잭션 내부에서 호출돼야 한다. {@code REJECTED_*}/낙관적 잠금 충돌은 {@code recordRequiresNew} 로
 * 비즈니스 rollback 과 무관하게 보존한다.
 *
 * <p><b>업로드 원본 파일명은 어디에도 남기지 않는다</b>(파일명에 실명/명단 가능 — 리뷰 2차 #2).
 * {@code sourceFileNameHash}(SHA-256) + 확장자만 기록한다.
 */
@Component
public class UploadAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("recruit.audit.upload");

    private final Clock clock;
    private final ActivityLogService activityLogService;

    public UploadAuditLogger(Clock clock, ActivityLogService activityLogService) {
        this.clock = clock;
        this.activityLogService = activityLogService;
    }

    /** commit outcome 별 감사. APPLIED=in-tx(호출 트랜잭션 join), REJECTED_*=REQUIRES_NEW. */
    public void logUploadCommit(
            AuditActorContext actorContext,
            Long stageId,
            StageResultUploadCommitResponse response,
            MultipartFile file
    ) {
        String fileNameHash = fileNameHash(file);
        String extension = fileExtension(file);
        String contentHash = contentHash(file);

        UploadMetadata metadata = new UploadMetadata(
                stageId,
                response.outcome().name(),
                response.totalRows(),
                response.changedCount(),
                response.unchangedCount(),
                response.errorCount(),
                response.staleCount(),
                fileNameHash,
                extension,
                file.getSize(),
                contentHash
        );

        AuditEvent.AuditEventBuilder event = AuditEvent.builder()
                .actorType(actorContext.actorType())
                .actorId(actorContext.actorId())
                .actorRoleSnapshot(actorContext.actorRoleSnapshot())
                .actionType(AuditActionType.STAGE_RESULT_UPLOAD)
                .targetType(AuditTargetType.STAGE_RESULT)
                .targetId(String.valueOf(stageId))
                .ipAddress(actorContext.ipAddress())
                .userAgent(actorContext.userAgent())
                .metadata(metadata);

        switch (response.outcome()) {
            case APPLIED -> activityLogService.recordInCurrentTx(
                    event.actionResult(AuditActionResult.SUCCESS).build());
            case REJECTED_VALIDATION -> activityLogService.recordRequiresNew(
                    event.actionResult(AuditActionResult.FAILURE)
                            .reasonCode(AuditReasonCode.VALIDATION_FAILED)
                            .build());
            case REJECTED_STALE -> activityLogService.recordRequiresNew(
                    event.actionResult(AuditActionResult.CONFLICT)
                            .reasonCode(AuditReasonCode.VERSION_MISMATCH)
                            .build());
        }

        log.info(
                "upload audit eventType=STAGE_RESULT_UPLOAD_COMMIT stageId={} timestamp={} "
                        + "actorLoginId={} authority={} clientIp={} userAgent={} "
                        + "outcome={} rowCount={} changedCount={} unchangedCount={} errorCount={} staleCount={} "
                        + "sourceFileNameHash={} sourceFileExtension={} sourceFileSize={} contentHash={}",
                stageId,
                LocalDateTime.now(clock),
                actorContext.actorId(),
                actorContext.actorRoleSnapshot(),
                actorContext.ipAddress(),
                actorContext.userAgent(),
                response.outcome(),
                response.totalRows(),
                response.changedCount(),
                response.unchangedCount(),
                response.errorCount(),
                response.staleCount(),
                fileNameHash,
                extension,
                file.getSize(),
                contentHash);
    }

    /**
     * 낙관적 잠금(@Version) 충돌로 commit이 실패한 경우의 audit. 충돌은 service 트랜잭션 commit 시 예외로 터져
     * 정상 응답이 없으므로(=정상 logUploadCommit에 도달 못 함), controller가 이 메서드로 실패 attempt를 남긴다.
     * REQUIRES_NEW — 비즈니스 rollback 과 무관하게 보존.
     */
    public void logUploadConflict(ExportAuditContext context, Long stageId, MultipartFile file) {
        String fileNameHash = fileNameHash(file);
        String extension = fileExtension(file);
        String contentHash = contentHash(file);

        activityLogService.recordRequiresNew(AuditEvent.builder()
                .actorType(ActorType.EMPLOYEE)
                .actorId(context.actorLoginId())
                .actorRoleSnapshot(context.authority())
                .actionType(AuditActionType.STAGE_RESULT_UPLOAD)
                .actionResult(AuditActionResult.CONFLICT)
                .targetType(AuditTargetType.STAGE_RESULT)
                .targetId(String.valueOf(stageId))
                .reasonCode(AuditReasonCode.VERSION_MISMATCH)
                .ipAddress(context.clientIp())
                .userAgent(context.userAgent())
                .metadata(new UploadConflictMetadata(stageId, fileNameHash, extension, file.getSize(), contentHash))
                .build());

        log.info(
                "upload audit eventType=STAGE_RESULT_UPLOAD_COMMIT stageId={} timestamp={} "
                        + "actorLoginId={} authority={} clientIp={} userAgent={} requestId={} "
                        + "outcome=OPTIMISTIC_LOCK_CONFLICT sourceFileNameHash={} sourceFileExtension={} sourceFileSize={} contentHash={}",
                stageId,
                LocalDateTime.now(clock),
                context.actorLoginId(),
                context.authority(),
                context.clientIp(),
                context.userAgent(),
                context.requestId(),
                fileNameHash,
                extension,
                file.getSize(),
                contentHash);
    }

    private String contentHash(MultipartFile file) {
        try {
            return HashUtil.sha256Bytes(file.getBytes());
        } catch (IOException e) {
            return "UNAVAILABLE";
        }
    }

    private String fileNameHash(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        return HashUtil.sha256(fileName == null ? "" : fileName);
    }

    /** 파일명 원문 금지 — 확장자만(소문자, 영숫자 외 제거, 최대 10자). */
    private String fileExtension(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dot + 1).toLowerCase().replaceAll("[^a-z0-9]", "");
        return extension.length() <= 10 ? extension : extension.substring(0, 10);
    }
}
