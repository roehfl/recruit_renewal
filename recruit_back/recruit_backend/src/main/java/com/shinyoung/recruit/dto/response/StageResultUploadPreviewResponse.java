package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.StageResultUploadRowStatus;

import java.util.List;

/**
 * upload preview 결과: 영속 변경 없이 행별 검증/diff와 집계 카운트를 반환한다.
 * preview는 낙관적 동시성 스냅샷이 없으므로 STALE을 판정하지 않는다(commit 시점에 판정).
 *
 * @param committable errorCount == 0 (commit이 검증을 통과할 가능성; 동시성 위반은 commit에서 별도 판정)
 */
public record StageResultUploadPreviewResponse(
        Long stageId,
        int totalRows,
        int changedCount,
        int unchangedCount,
        int errorCount,
        boolean committable,
        List<StageResultUploadRowResult> rows
) {

    public static StageResultUploadPreviewResponse of(Long stageId, List<StageResultUploadRowResult> rows) {
        int changed = (int) rows.stream().filter(r -> r.status() == StageResultUploadRowStatus.CHANGED).count();
        int unchanged = (int) rows.stream().filter(r -> r.status() == StageResultUploadRowStatus.UNCHANGED).count();
        int error = (int) rows.stream().filter(r -> r.status() == StageResultUploadRowStatus.ERROR).count();
        return new StageResultUploadPreviewResponse(
                stageId, rows.size(), changed, unchanged, error, error == 0, List.copyOf(rows));
    }
}
