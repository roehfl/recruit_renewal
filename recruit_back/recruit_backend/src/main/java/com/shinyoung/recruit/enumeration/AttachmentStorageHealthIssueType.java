package com.shinyoung.recruit.enumeration;

public enum AttachmentStorageHealthIssueType {
    STORED_MISSING_PHYSICAL_FILE,
    DELETED_PHYSICAL_FILE_REMAINING,
    ORPHAN_PHYSICAL_FILE,
    INVALID_STORAGE_PATH,
    MISSING_ROW_PHYSICAL_FILE_PRESENT,
    /** PURGED 지원서의 경로에 파일이 잔존 — "DB PURGED + 파일 잔존" 치명적 불일치(09e, 설계 §6.1). */
    PURGED_PHYSICAL_FILE_PRESENT,
    IGNORED_UNMANAGED_FILE
}
