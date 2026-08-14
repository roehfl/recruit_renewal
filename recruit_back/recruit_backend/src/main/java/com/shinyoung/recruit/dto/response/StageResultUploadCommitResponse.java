package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.StageResultUploadCommitOutcome;
import com.shinyoung.recruit.enumeration.StageResultUploadRowStatus;

import java.util.List;

/**
 * upload commit 결과. all-or-nothing이므로 APPLIED는 전 행 통과, REJECTED_*는 0건 적용이다.
 *
 * @param changedCount   변경 대상(적용되었거나 거부 시 적용 예정이던) 행 수
 * @param failedRows     거부 시 ERROR/STALE 행(성공 시 빈 리스트)
 */
public record StageResultUploadCommitResponse(
        Long stageId,
        StageResultUploadCommitOutcome outcome,
        int totalRows,
        int changedCount,
        int unchangedCount,
        int errorCount,
        int staleCount,
        List<StageResultUploadRowResult> failedRows
) {

    public static StageResultUploadCommitResponse applied(Long stageId, List<StageResultUploadRowResult> rows) {
        return build(stageId, StageResultUploadCommitOutcome.APPLIED, rows);
    }

    public static StageResultUploadCommitResponse rejected(
            Long stageId,
            StageResultUploadCommitOutcome outcome,
            List<StageResultUploadRowResult> rows
    ) {
        return build(stageId, outcome, rows);
    }

    private static StageResultUploadCommitResponse build(
            Long stageId,
            StageResultUploadCommitOutcome outcome,
            List<StageResultUploadRowResult> rows
    ) {
        int changed = (int) rows.stream().filter(r -> r.status() == StageResultUploadRowStatus.CHANGED).count();
        int unchanged = (int) rows.stream().filter(r -> r.status() == StageResultUploadRowStatus.UNCHANGED).count();
        int error = (int) rows.stream().filter(r -> r.status() == StageResultUploadRowStatus.ERROR).count();
        int stale = (int) rows.stream().filter(r -> r.status() == StageResultUploadRowStatus.STALE).count();
        List<StageResultUploadRowResult> failed = outcome == StageResultUploadCommitOutcome.APPLIED
                ? List.of()
                : rows.stream()
                        .filter(r -> r.status() == StageResultUploadRowStatus.ERROR
                                || r.status() == StageResultUploadRowStatus.STALE)
                        .toList();
        return new StageResultUploadCommitResponse(
                stageId, outcome, rows.size(), changed, unchanged, error, stale, failed);
    }
}
