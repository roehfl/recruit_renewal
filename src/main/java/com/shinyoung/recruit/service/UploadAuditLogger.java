package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.dto.response.StageResultUploadCommitResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Excel upload commit에 대한 SLF4J 구조적 audit 로그. upload는 Phase 07의 유일한 쓰기 경로이므로
 * 주체/요청 메타 + outcome + 행 카운트 + 소스 파일 메타(파일명/크기/contentHash)를 남긴다.
 *
 * <p>업로드 파일 내용/지원자 PII 값 자체는 audit에 직접 남기지 않고, 변경 추적용 {@code contentHash}만 남긴다.
 * 영속 {@code ActivityLog} 도입 시 이관한다.
 */
@Component
public class UploadAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("recruit.audit.upload");

    private final Clock clock;

    public UploadAuditLogger(Clock clock) {
        this.clock = clock;
    }

    public void logUploadCommit(
            ExportAuditContext context,
            Long stageId,
            StageResultUploadCommitResponse response,
            MultipartFile file
    ) {
        log.info(
                "upload audit eventType=STAGE_RESULT_UPLOAD_COMMIT stageId={} timestamp={} "
                        + "actorLoginId={} authority={} clientIp={} userAgent={} requestId={} "
                        + "outcome={} rowCount={} changedCount={} unchangedCount={} errorCount={} staleCount={} "
                        + "sourceFileName={} sourceFileSize={} contentHash={}",
                stageId,
                LocalDateTime.now(clock),
                context.actorLoginId(),
                context.authority(),
                context.clientIp(),
                context.userAgent(),
                context.requestId(),
                response.outcome(),
                response.totalRows(),
                response.changedCount(),
                response.unchangedCount(),
                response.errorCount(),
                response.staleCount(),
                sanitize(file.getOriginalFilename()),
                file.getSize(),
                contentHash(file));
    }

    /**
     * 낙관적 잠금(@Version) 충돌로 commit이 실패한 경우의 audit. 충돌은 service 트랜잭션 commit 시 예외로 터져
     * 정상 응답이 없으므로(=정상 logUploadCommit에 도달 못 함), controller가 이 메서드로 실패 attempt를 남긴다.
     */
    public void logUploadConflict(ExportAuditContext context, Long stageId, MultipartFile file) {
        log.info(
                "upload audit eventType=STAGE_RESULT_UPLOAD_COMMIT stageId={} timestamp={} "
                        + "actorLoginId={} authority={} clientIp={} userAgent={} requestId={} "
                        + "outcome=OPTIMISTIC_LOCK_CONFLICT sourceFileName={} sourceFileSize={} contentHash={}",
                stageId,
                LocalDateTime.now(clock),
                context.actorLoginId(),
                context.authority(),
                context.clientIp(),
                context.userAgent(),
                context.requestId(),
                sanitize(file.getOriginalFilename()),
                file.getSize(),
                contentHash(file));
    }

    private String contentHash(MultipartFile file) {
        try {
            return HashUtil.sha256Bytes(file.getBytes());
        } catch (IOException e) {
            return "UNAVAILABLE";
        }
    }

    private String sanitize(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.replaceAll("[\\r\\n]", " ");
    }
}
