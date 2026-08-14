package com.shinyoung.recruit.enumeration;

/** PurgeBatch 실행 모드(Phase 09c). dry-run 과 execute 는 별도 batch 다(설계 §5.4). */
public enum PurgeBatchMode {
    DRY_RUN,
    EXECUTE
}
