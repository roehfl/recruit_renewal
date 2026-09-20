package com.shinyoung.recruit.enumeration;

/** 자동 파기 스케줄 1회 기동의 결과(Phase 10). 화면이 마지막 실행 상태를 보여 주는 데 쓴다. */
public enum RetentionScheduleRunResult {
    /** 자동 파기가 꺼져 있어 스캔하지 않음. */
    SKIPPED_DISABLED,
    /** 다음 파기 예정일 전이라 스캔하지 않음. */
    SKIPPED_NOT_DUE,
    /** 스캔했으나 적격 건이 0건. */
    NO_TARGET,
    /** 파기를 실행함. */
    EXECUTED,
    /** 실행 중 예외 발생. */
    ERROR
}
