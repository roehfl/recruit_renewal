여전히 남은 Major — PURGED + 파일 잔존 탐지가 row 매칭 파일을 놓친다

현재 AttachmentStorageHealthScanService.addOrphanIssues()는 orphan 후보만 먼저 만든다. 이 후보는 storedKeys, deletedKeys, deferredPurgeKeys, missingRowsByKey에 매칭되지 않는 파일만 포함한다.

그 다음에야 purgedApplicationIds(orphanCandidates)를 호출해서 PURGED_PHYSICAL_FILE_PRESENT를 만든다. 즉 치명탐지 대상이 orphanCandidates로 제한되어 있다.

그래서 이 케이스는 여전히 빠진다.

JobApplication.purgeResult = PURGED
ApplicationAttachment.physicalFileStatus = STORED
ApplicationAttachment.storagePath = applications/{applicationId}/...
실제 파일 존재

왜 빠지냐면, STORED row는 storedRows에 들어간다.
그리고 addOrphanIssues()는 storedKeys.contains(key)면 orphan 후보에서 제외한다.
따라서 해당 파일은 PURGED_PHYSICAL_FILE_PRESENT도 아니고, orphan도 아니고, stored missing도 아니다. 아무 이슈 없이 통과할 수 있다.

현재 테스트도 이 구멍을 막지 않는다. 테스트는 “첨부 row 없음 + PURGED 지원서 경로에 파일만 남음” 케이스만 검증한다.

하지만 health scan은 정상 코드 경로만 검증하는 게 아니라 오염된 운영 데이터와 깨진 불변식을 찾는 장치다. PURGED 지원서의 경로에 파일이 존재하면, row가 있든 없든 치명이다.

필요한 수정

PURGED_PHYSICAL_FILE_PRESENT 분류를 orphan 분기 뒤가 아니라 전체 physical file 기준의 선행 분기로 빼라.

private void addOrphanIssues(
        Map<String, PhysicalFileInfo> physicalFiles,
        RowScanResult rowScan,
        List<AttachmentStorageHealthIssueResponse> issues
) {
    Set<Long> purgedApplicationIds = purgedApplicationIds(
            new ArrayList<>(physicalFiles.values())
    );

    for (PhysicalFileInfo physicalFile : physicalFiles.values()) {
        Long applicationId = applicationIdFromKey(physicalFile.storageKey());
        if (applicationId != null && purgedApplicationIds.contains(applicationId)) {
            issues.add(AttachmentStorageHealthIssueResponse.of(
                    AttachmentStorageHealthIssueType.PURGED_PHYSICAL_FILE_PRESENT,
                    applicationId,
                    null,
                    PhysicalFileStatus.BINARY_DELETED,
                    physicalFile.fileKeyHash(),
                    physicalFile.size(),
                    "Purged application still has a physical file (critical)."
            ));
            continue;
        }

        // 이후 기존 stored/deleted/missing/deferred/orphan 분기
    }
}

그리고 테스트는 반드시 아래 케이스로 추가해라.

1. 지원서를 markPurged()
2. 같은 applicationId에 ApplicationAttachment row를 STORED 상태로 남김
3. storagePath에 실제 파일 생성
4. scanDryRun()
5. purgedPhysicalFilePresentCount == 1
6. ORPHAN이 아니라 PURGED_PHYSICAL_FILE_PRESENT

이 테스트가 들어가야 내가 지적한 치명탐지 구멍이 닫힌다.

추가 Low — 문서가 현재 구현보다 낙관적이다

07-implementation-history.md에는 Phase 09 종료라고 되어 있고, 9e 구현 리뷰 반영으로 Medium/Low만 적혀 있다.
하지만 위 Major가 남아 있으므로 문서의 “Phase 09 종료” 문구는 아직 이르다. 치명탐지 보정 후 종료 처리해라.