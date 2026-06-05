package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.StageResultBulkUpdateItemRequest;
import com.shinyoung.recruit.dto.request.StageResultBulkUpdateRequest;
import com.shinyoung.recruit.dto.request.StageResultUploadRowRequest;
import com.shinyoung.recruit.dto.response.StageResultUploadCommitResponse;
import com.shinyoung.recruit.dto.response.StageResultUploadDiff;
import com.shinyoung.recruit.dto.response.StageResultUploadPreviewResponse;
import com.shinyoung.recruit.dto.response.StageResultUploadRowResult;
import com.shinyoung.recruit.dto.response.StageResultUploadTemplateRow;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageResultUploadCommitOutcome;
import com.shinyoung.recruit.enumeration.StageResultUploadRowStatus;
import com.shinyoung.recruit.exception.StageNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Excel upload(StageResult) preview/commit. stateless(새 테이블 없음)이며 commit은 기존
 * {@link StageResultService#bulkUpdateResults}에 위임해 Stage {@code IN_PROGRESS} guard, PENDING 금지,
 * comment 길이, actor 필수, 정정 이력/audit 같은 기존 불변식을 그대로 상속한다.
 *
 * <p>행은 {@code stageResultId}(존재) + {@code applicationId} 일치 + path {@code stageId} 소속의
 * 3중 교차검증을 모두 만족해야 유효하다. 편집 컬럼은 {@code resultStatus}/{@code score}/{@code comment}뿐이고,
 * 빈칸은 resultStatus=오류, score/comment=null clear로 해석한다. 변경 없는 행은 commit에서 제외한다.
 * commit은 변경 대상 행에 대해 {@code stageResultUpdatedAt} 토큰을 현재 DB 값과 비교해 낙관적 동시성을
 * 검사하고, 오류/STALE이 하나라도 있으면 0건 적용(all-or-nothing)으로 거부한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StageResultUploadService {

    private static final DateTimeFormatter TOKEN_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int COMMENT_MAX_LENGTH = 2000;

    /** 편집 허용 resultStatus = StageResultStatus − PENDING. */
    private static final Set<StageResultStatus> ALLOWED_STATUSES = Set.of(
            StageResultStatus.PASSED,
            StageResultStatus.FAILED,
            StageResultStatus.ABSENT,
            StageResultStatus.HOLD,
            StageResultStatus.WITHDRAWN);

    private static final ExcelExportSpec<StageResultUploadTemplateRow> TEMPLATE_SPEC = new ExcelExportSpec<>(
            "stage-result-upload",
            List.of(
                    new ExportColumn<>("stageResultId", StageResultUploadTemplateRow::stageResultId),
                    new ExportColumn<>("applicationId", StageResultUploadTemplateRow::applicationId),
                    new ExportColumn<>("applicantName", StageResultUploadTemplateRow::applicantName),
                    new ExportColumn<>("stageResultUpdatedAt", StageResultUploadTemplateRow::stageResultUpdatedAt),
                    new ExportColumn<>("resultStatus", StageResultUploadTemplateRow::resultStatus),
                    new ExportColumn<>("score", StageResultUploadTemplateRow::score),
                    new ExportColumn<>("comment", StageResultUploadTemplateRow::comment)));

    private final StageRepository stageRepository;
    private final StageResultRepository stageResultRepository;
    private final StageResultUploadParser parser;
    private final StageResultService stageResultService;
    private final ExcelExportService excelExportService;
    private final UploadAuditLogger uploadAuditLogger;
    private final AuditRequestContextResolver auditRequestContextResolver;

    @PersistenceContext
    private EntityManager entityManager;

    public ExcelExportFile generateTemplate(Long stageId) {
        requireStage(stageId);
        // 동시성 토큰을 service의 formatToken과 동일 규칙으로 만들기 위해 여기서 직접 매핑한다(단일 소스).
        List<StageResultUploadTemplateRow> rows = stageResultRepository.findByStageIdForAdminList(stageId).stream()
                .map(result -> new StageResultUploadTemplateRow(
                        String.valueOf(result.getId()),
                        String.valueOf(result.getJobApplication().getId()),
                        result.getJobApplication().getApplicantNameSnapshot(),
                        formatToken(result.getUpdatedAt()),
                        result.getResultStatus() == null ? "" : result.getResultStatus().name(),
                        result.getScore() == null ? "" : result.getScore().toPlainString(),
                        result.getComment() == null ? "" : result.getComment()))
                .toList();
        // round-trip 소스이므로 formula-escape를 끈다(comment 등 값을 변형하지 않는다).
        return excelExportService.generate(
                TEMPLATE_SPEC, rows, "stage-result-upload-template-stage-" + stageId + ".xlsx", false);
    }

    public StageResultUploadPreviewResponse preview(Long stageId, MultipartFile file) {
        requireStage(stageId);
        List<StageResultUploadRowRequest> rows = parser.parse(file);
        Map<Long, StageResult> resultMap = loadResultMap(stageId);
        Set<Long> duplicateIds = duplicateStageResultIds(rows);

        List<StageResultUploadRowResult> results = rows.stream()
                .map(row -> validate(row, resultMap, duplicateIds, false).result())
                .toList();
        return StageResultUploadPreviewResponse.of(stageId, results);
    }

    @Transactional
    public StageResultUploadCommitResponse commit(Long stageId, MultipartFile file, String actor) {
        requireStage(stageId);
        // 변경 행이 0건이어도 actor 필수 + Stage IN_PROGRESS guard를 먼저 강제한다(bulkUpdateResults 우회 방지).
        stageResultService.validateBulkUpdatable(stageId, actor);

        List<StageResultUploadRowRequest> rows = parser.parse(file);
        Map<Long, StageResult> resultMap = loadResultMap(stageId);
        Set<Long> duplicateIds = duplicateStageResultIds(rows);

        // 1차 검증: 형식/허용값/교차검증 + 변경/미변경 분류(STALE은 미판정).
        List<ValidatedUploadRow> firstPass = rows.stream()
                .map(row -> validate(row, resultMap, duplicateIds, false))
                .toList();
        if (firstPass.stream().anyMatch(v -> v.result().status() == StageResultUploadRowStatus.ERROR)) {
            return auditedCommitResponse(stageId, file, actor, StageResultUploadCommitResponse.rejected(
                    stageId, StageResultUploadCommitOutcome.REJECTED_VALIDATION,
                    firstPass.stream().map(ValidatedUploadRow::result).toList()));
        }

        // 변경 대상 행만 비관적 쓰기 잠금 + DB 최신값으로 refresh한다. 잠금 후 최신 updatedAt 기준으로 토큰을
        // 재비교하므로, 다른 관리자가 동시에 commit해도 lost update(앞선 변경 덮어쓰기)를 막는다. 잠금 순서는
        // id 오름차순으로 고정해 deadlock을 피한다.
        firstPass.stream()
                .filter(v -> v.result().status() == StageResultUploadRowStatus.CHANGED)
                .map(v -> resultMap.get(v.result().stageResultId()))
                .sorted(Comparator.comparing(StageResult::getId))
                .forEach(entity -> entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE));

        // 2차 검증: 잠금/refresh 후 최신값 기준으로 STALE까지 판정한다.
        List<ValidatedUploadRow> validated = rows.stream()
                .map(row -> validate(row, resultMap, duplicateIds, true))
                .toList();
        List<StageResultUploadRowResult> results = validated.stream()
                .map(ValidatedUploadRow::result)
                .toList();
        if (results.stream().anyMatch(r -> r.status() == StageResultUploadRowStatus.ERROR)) {
            return auditedCommitResponse(stageId, file, actor, StageResultUploadCommitResponse.rejected(
                    stageId, StageResultUploadCommitOutcome.REJECTED_VALIDATION, results));
        }
        if (results.stream().anyMatch(r -> r.status() == StageResultUploadRowStatus.STALE)) {
            return auditedCommitResponse(stageId, file, actor, StageResultUploadCommitResponse.rejected(
                    stageId, StageResultUploadCommitOutcome.REJECTED_STALE, results));
        }

        List<StageResultBulkUpdateItemRequest> items = validated.stream()
                .filter(v -> v.result().status() == StageResultUploadRowStatus.CHANGED)
                .map(v -> new StageResultBulkUpdateItemRequest(
                        v.result().stageResultId(), v.newStatus(), v.newScore(), v.newComment()))
                .toList();
        if (!items.isEmpty()) {
            // upload 는 STAGE_RESULT_UPLOAD 한 건으로 감사하므로 bulkUpdateResults 의 STAGE_RESULT_CORRECT
            // 감사는 끈다(이중 기록 방지, Phase 09b).
            stageResultService.bulkUpdateResults(stageId, new StageResultBulkUpdateRequest(items), actor, false);
        }
        // APPLIED 는 커밋된 변경의 성공 증적 — 이 트랜잭션에 join(in-tx) 해 원자적으로 남는다(ADR-0006).
        return auditedCommitResponse(stageId, file, actor, StageResultUploadCommitResponse.applied(stageId, results));
    }

    /** commit outcome 감사(dual-write, Phase 09b). APPLIED=in-tx, REJECTED_*=REQUIRES_NEW(adapter 내부 분기). */
    private StageResultUploadCommitResponse auditedCommitResponse(
            Long stageId,
            MultipartFile file,
            String actor,
            StageResultUploadCommitResponse response
    ) {
        uploadAuditLogger.logUploadCommit(auditRequestContextResolver.resolve(actor), stageId, response, file);
        return response;
    }

    private ValidatedUploadRow validate(
            StageResultUploadRowRequest row,
            Map<Long, StageResult> resultMap,
            Set<Long> duplicateIds,
            boolean forCommit
    ) {
        List<String> errors = new ArrayList<>();

        if (row.formulaCellPresent()) {
            errors.add("수식(formula) 셀은 허용되지 않습니다.");
        }
        if (row.tokenCellNotString()) {
            errors.add("stageResultUpdatedAt은 문자열 셀이어야 합니다.");
        }

        Long stageResultId = parseLong(row.stageResultId());
        if (stageResultId == null) {
            errors.add("stageResultId는 필수이며 숫자여야 합니다.");
        }
        Long applicationId = parseLong(row.applicationId());
        if (applicationId == null) {
            errors.add("applicationId는 필수이며 숫자여야 합니다.");
        }

        StageResultStatus newStatus = parseResultStatus(blankToNull(row.resultStatus()), errors);
        BigDecimal newScore = parseScore(blankToNull(row.score()), errors);
        String newComment = blankToNull(row.comment());
        if (newComment != null && newComment.length() > COMMENT_MAX_LENGTH) {
            errors.add("comment는 2000자 이하여야 합니다.");
        }

        if (stageResultId != null && duplicateIds.contains(stageResultId)) {
            errors.add("stageResultId가 파일 내에서 중복되었습니다.");
        }

        StageResult current = stageResultId == null ? null : resultMap.get(stageResultId);
        if (stageResultId != null && !duplicateIds.contains(stageResultId)) {
            if (current == null) {
                errors.add("해당 단계의 StageResult가 아니거나 존재하지 않습니다.");
            } else if (applicationId != null
                    && !current.getJobApplication().getId().equals(applicationId)) {
                errors.add("applicationId가 StageResult와 일치하지 않습니다.");
            }
        }

        if (!errors.isEmpty()) {
            return new ValidatedUploadRow(
                    StageResultUploadRowResult.error(
                            row.rowNumber(), stageResultId, applicationId, row.applicantName(), errors),
                    null, null, null);
        }

        boolean changed = current.getResultStatus() != newStatus
                || !scoreEquals(current.getScore(), newScore)
                || !Objects.equals(blankToNull(current.getComment()), newComment);

        if (!changed) {
            return new ValidatedUploadRow(
                    StageResultUploadRowResult.unchanged(
                            row.rowNumber(), stageResultId, applicationId, row.applicantName()),
                    newStatus, newScore, newComment);
        }

        StageResultUploadDiff diff = StageResultUploadDiff.of(current, newStatus, newScore, newComment);

        if (forCommit) {
            String currentToken = formatToken(current.getUpdatedAt());
            String rowToken = row.stageResultUpdatedAt() == null ? "" : row.stageResultUpdatedAt().trim();
            if (!currentToken.equals(rowToken)) {
                return new ValidatedUploadRow(
                        StageResultUploadRowResult.stale(
                                row.rowNumber(), stageResultId, applicationId, row.applicantName(),
                                currentToken, diff),
                        newStatus, newScore, newComment);
            }
        }

        return new ValidatedUploadRow(
                StageResultUploadRowResult.changed(
                        row.rowNumber(), stageResultId, applicationId, row.applicantName(), diff),
                newStatus, newScore, newComment);
    }

    private StageResultStatus parseResultStatus(String raw, List<String> errors) {
        if (raw == null) {
            errors.add("resultStatus는 필수입니다.");
            return null;
        }
        try {
            StageResultStatus parsed = StageResultStatus.valueOf(raw);
            if (!ALLOWED_STATUSES.contains(parsed)) {
                errors.add("허용되지 않는 resultStatus입니다: " + raw);
                return null;
            }
            return parsed;
        } catch (IllegalArgumentException e) {
            errors.add("허용되지 않는 resultStatus입니다: " + raw);
            return null;
        }
    }

    private BigDecimal parseScore(String raw, List<String> errors) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            errors.add("score 형식이 올바르지 않습니다: " + raw);
            return null;
        }
    }

    private Map<Long, StageResult> loadResultMap(Long stageId) {
        return stageResultRepository.findByStageIdForAdminList(stageId).stream()
                .collect(Collectors.toMap(StageResult::getId, Function.identity()));
    }

    private Set<Long> duplicateStageResultIds(List<StageResultUploadRowRequest> rows) {
        Set<Long> seen = new HashSet<>();
        Set<Long> duplicates = new HashSet<>();
        for (StageResultUploadRowRequest row : rows) {
            Long id = parseLong(row.stageResultId());
            if (id == null) {
                continue;
            }
            if (!seen.add(id)) {
                duplicates.add(id);
            }
        }
        return duplicates;
    }

    private void requireStage(Long stageId) {
        if (!stageRepository.existsById(stageId)) {
            throw new StageNotFoundException("Stage not found.");
        }
    }

    private static boolean scoreEquals(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.compareTo(b) == 0;
    }

    private static String formatToken(LocalDateTime updatedAt) {
        // DB precision(보통 microseconds)과 in-memory(nanoseconds) 차이로 토큰이 어긋나지 않도록 micro로 normalize한다.
        return updatedAt == null ? "" : TOKEN_FORMAT.format(updatedAt.truncatedTo(ChronoUnit.MICROS));
    }

    private static Long parseLong(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 검증 결과(응답 DTO) + commit 매핑용 typed 값 묶음. */
    private record ValidatedUploadRow(
            StageResultUploadRowResult result,
            StageResultStatus newStatus,
            BigDecimal newScore,
            String newComment
    ) {
    }
}
