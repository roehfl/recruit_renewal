package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.AttachmentProperties;
import com.shinyoung.recruit.domain.entity.ApplicationAttachment;
import com.shinyoung.recruit.domain.repository.ApplicationAttachmentRepository;
import com.shinyoung.recruit.dto.response.AttachmentStorageHealthIssueResponse;
import com.shinyoung.recruit.dto.response.AttachmentStorageHealthScanResponse;
import com.shinyoung.recruit.enumeration.AttachmentStorageHealthIssueType;
import com.shinyoung.recruit.enumeration.PhysicalFileStatus;
import com.shinyoung.recruit.exception.StorageHealthScanException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AttachmentStorageHealthScanService {

    private static final String SAFE_SCAN_FAILURE_MESSAGE = "Attachment storage health scan failed.";

    private final ApplicationAttachmentRepository attachmentRepository;
    private final com.shinyoung.recruit.domain.repository.JobApplicationRepository jobApplicationRepository;
    private final AttachmentProperties attachmentProperties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AttachmentStorageHealthScanResponse scanDryRun() {
        Path storageRoot = attachmentProperties.getStorageRoot().toAbsolutePath().normalize();
        List<AttachmentStorageHealthIssueResponse> issues = new ArrayList<>();
        PhysicalScanResult physicalScan = scanPhysicalFiles(storageRoot, issues);
        RowScanResult rowScan = scanRows(storageRoot, issues);

        addStoredMissingIssues(rowScan.storedRows(), physicalScan.managedFilesByKey(), issues);
        addDeletedRemainingIssues(rowScan.deletedRows(), physicalScan.managedFilesByKey(), issues);
        addOrphanIssues(physicalScan.managedFilesByKey(), rowScan, issues);

        Map<AttachmentStorageHealthIssueType, Long> issueCounts = countIssues(issues);
        return new AttachmentStorageHealthScanResponse(
                true,
                LocalDateTime.now(clock),
                physicalScan.scannedPhysicalFileCount(),
                physicalScan.managedFilesByKey().size(),
                physicalScan.ignoredPhysicalFileCount(),
                rowScan.storedRowCount(),
                rowScan.deletedRowCount(),
                rowScan.missingRowCount(),
                rowScan.pendingBinaryDeleteRowCount(),
                issueCounts.getOrDefault(AttachmentStorageHealthIssueType.STORED_MISSING_PHYSICAL_FILE, 0L),
                issueCounts.getOrDefault(AttachmentStorageHealthIssueType.DELETED_PHYSICAL_FILE_REMAINING, 0L),
                issueCounts.getOrDefault(AttachmentStorageHealthIssueType.ORPHAN_PHYSICAL_FILE, 0L),
                issueCounts.getOrDefault(AttachmentStorageHealthIssueType.PURGED_PHYSICAL_FILE_PRESENT, 0L),
                issueCounts.getOrDefault(AttachmentStorageHealthIssueType.INVALID_STORAGE_PATH, 0L),
                List.copyOf(issues)
        );
    }

    private PhysicalScanResult scanPhysicalFiles(
            Path storageRoot,
            List<AttachmentStorageHealthIssueResponse> issues
    ) {
        if (!Files.exists(storageRoot, LinkOption.NOFOLLOW_LINKS)) {
            return new PhysicalScanResult(Map.of(), 0, 0);
        }
        if (!Files.isDirectory(storageRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new StorageHealthScanException(SAFE_SCAN_FAILURE_MESSAGE);
        }

        Map<String, PhysicalFileInfo> managedFiles = new HashMap<>();
        long scannedCount = 0;
        long ignoredCount = 0;
        try (Stream<Path> paths = Files.walk(storageRoot)) {
            List<Path> candidates = paths
                    .filter(path -> !storageRoot.equals(path))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            || Files.isSymbolicLink(path))
                    .toList();

            for (Path candidate : candidates) {
                Optional<String> relativeKey = toRelativeKey(storageRoot, candidate);
                if (relativeKey.isEmpty()) {
                    ignoredCount++;
                    continue;
                }
                if (Files.isSymbolicLink(candidate)) {
                    ignoredCount++;
                    addPhysicalIssue(
                            issues,
                            AttachmentStorageHealthIssueType.IGNORED_UNMANAGED_FILE,
                            relativeKey.get(),
                            null,
                            "Unmanaged storage entry was ignored."
                    );
                    continue;
                }

                scannedCount++;
                String key = relativeKey.get();
                if (!isManagedStorageKey(key)) {
                    ignoredCount++;
                    addPhysicalIssue(
                            issues,
                            AttachmentStorageHealthIssueType.IGNORED_UNMANAGED_FILE,
                            key,
                            Files.size(candidate),
                            "Unmanaged storage file was ignored."
                    );
                    continue;
                }
                managedFiles.put(key, new PhysicalFileInfo(key, HashUtil.sha256(key), Files.size(candidate)));
            }
        } catch (IOException | RuntimeException e) {
            throw new StorageHealthScanException(SAFE_SCAN_FAILURE_MESSAGE, e);
        }
        return new PhysicalScanResult(Map.copyOf(managedFiles), scannedCount, ignoredCount);
    }

    private RowScanResult scanRows(
            Path storageRoot,
            List<AttachmentStorageHealthIssueResponse> issues
    ) {
        // 1단계 마이그레이션(9d-2): legacy DELETED 와 SOFT_DELETED 를 동일(soft-deleted)하게 스캔한다.
        // BINARY_DELETE_PENDING/FAILED 는 9e 재처리 대상이라 row 이슈 판정에선 제외하되, 파일이 잔존할 수 있으므로
        // storageKey 를 "known but deferred" 로 등록해 ORPHAN 오탐을 막는다(9d-2 리뷰 Medium 1).
        // BINARY_DELETED(storagePath null)는 스캔 대상 외 — 본격 상태별 정책은 9e 에서 확장.
        List<ApplicationAttachment> attachments = attachmentRepository.findByPhysicalFileStatusIn(List.of(
                PhysicalFileStatus.STORED,
                PhysicalFileStatus.DELETED,
                PhysicalFileStatus.SOFT_DELETED,
                PhysicalFileStatus.MISSING,
                PhysicalFileStatus.BINARY_DELETE_PENDING,
                PhysicalFileStatus.BINARY_DELETE_FAILED
        ));
        List<AttachmentRowInfo> storedRows = new ArrayList<>();
        List<AttachmentRowInfo> deletedRows = new ArrayList<>();
        List<AttachmentRowInfo> missingRows = new ArrayList<>();
        Set<String> deferredPurgeKeys = new HashSet<>();
        long storedRowCount = 0;
        long deletedRowCount = 0;
        long missingRowCount = 0;
        long pendingBinaryDeleteRowCount = 0;

        for (ApplicationAttachment attachment : attachments) {
            PhysicalFileStatus status = attachment.getPhysicalFileStatus();
            if (status == PhysicalFileStatus.BINARY_DELETE_PENDING
                    || status == PhysicalFileStatus.BINARY_DELETE_FAILED) {
                // purge saga 진행/실패 행(§6.1 retry 대상) — issue 아님. 키만 known 처리(orphan 오탐 방지) +
                // reconciliation 가시성을 위해 카운트만 집계.
                pendingBinaryDeleteRowCount++;
                toStorageKey(storageRoot, attachment.getStoragePath())
                        .filter(this::isManagedStorageKey)
                        .ifPresent(deferredPurgeKeys::add);
                continue;
            }
            if (status == PhysicalFileStatus.STORED) {
                storedRowCount++;
            } else if (PhysicalFileStatus.SOFT_DELETED_FAMILY.contains(status)) {
                deletedRowCount++;
            } else if (status == PhysicalFileStatus.MISSING) {
                missingRowCount++;
            }

            Optional<String> storageKey = toStorageKey(storageRoot, attachment.getStoragePath());
            if (storageKey.isEmpty() || !isManagedStorageKey(storageKey.get())) {
                issues.add(AttachmentStorageHealthIssueResponse.of(
                        AttachmentStorageHealthIssueType.INVALID_STORAGE_PATH,
                        applicationIdOf(attachment),
                        attachment.getId(),
                        status,
                        null,
                        null,
                        "Attachment row has an invalid storage key."
                ));
                continue;
            }

            AttachmentRowInfo row = new AttachmentRowInfo(
                    applicationIdOf(attachment),
                    attachment.getId(),
                    status,
                    storageKey.get(),
                    HashUtil.sha256(storageKey.get())
            );
            if (status == PhysicalFileStatus.STORED) {
                storedRows.add(row);
            } else if (PhysicalFileStatus.SOFT_DELETED_FAMILY.contains(status)) {
                deletedRows.add(row);
            } else {
                missingRows.add(row);
            }
        }

        return new RowScanResult(
                List.copyOf(storedRows),
                List.copyOf(deletedRows),
                List.copyOf(missingRows),
                Set.copyOf(deferredPurgeKeys),
                storedRowCount,
                deletedRowCount,
                missingRowCount,
                pendingBinaryDeleteRowCount
        );
    }

    private void addStoredMissingIssues(
            List<AttachmentRowInfo> storedRows,
            Map<String, PhysicalFileInfo> physicalFiles,
            List<AttachmentStorageHealthIssueResponse> issues
    ) {
        for (AttachmentRowInfo row : storedRows) {
            if (!physicalFiles.containsKey(row.storageKey())) {
                issues.add(AttachmentStorageHealthIssueResponse.of(
                        AttachmentStorageHealthIssueType.STORED_MISSING_PHYSICAL_FILE,
                        row.applicationId(),
                        row.attachmentId(),
                        row.rowStatus(),
                        row.fileKeyHash(),
                        null,
                        "STORED attachment row has no matching physical file."
                ));
            }
        }
    }

    private void addDeletedRemainingIssues(
            List<AttachmentRowInfo> deletedRows,
            Map<String, PhysicalFileInfo> physicalFiles,
            List<AttachmentStorageHealthIssueResponse> issues
    ) {
        for (AttachmentRowInfo row : deletedRows) {
            PhysicalFileInfo physicalFile = physicalFiles.get(row.storageKey());
            if (physicalFile != null) {
                issues.add(AttachmentStorageHealthIssueResponse.of(
                        AttachmentStorageHealthIssueType.DELETED_PHYSICAL_FILE_REMAINING,
                        row.applicationId(),
                        row.attachmentId(),
                        row.rowStatus(),
                        row.fileKeyHash(),
                        physicalFile.size(),
                        "DELETED attachment row still has a physical file."
                ));
            }
        }
    }

    private void addOrphanIssues(
            Map<String, PhysicalFileInfo> physicalFiles,
            RowScanResult rowScan,
            List<AttachmentStorageHealthIssueResponse> issues
    ) {
        Set<String> storedKeys = rowScan.storedRows().stream()
                .map(AttachmentRowInfo::storageKey)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> deletedKeys = rowScan.deletedRows().stream()
                .map(AttachmentRowInfo::storageKey)
                .collect(Collectors.toUnmodifiableSet());
        Map<String, AttachmentRowInfo> missingRowsByKey = rowScan.missingRows().stream()
                .collect(Collectors.toMap(AttachmentRowInfo::storageKey, Function.identity(), (left, right) -> left));

        // orphan 후보(어떤 row 키에도 매칭 안 됨)를 먼저 모아 applicationId 추출 → PURGED 지원서 집합을 일괄 조회.
        // PURGED 인데 파일이 남아 있으면 "DB PURGED + 파일 잔존" 치명적 불일치(§6.1)로 분류한다.
        List<PhysicalFileInfo> orphanCandidates = new ArrayList<>();
        for (PhysicalFileInfo physicalFile : physicalFiles.values()) {
            String key = physicalFile.storageKey();
            if (storedKeys.contains(key) || deletedKeys.contains(key) || rowScan.deferredPurgeKeys().contains(key)
                    || missingRowsByKey.containsKey(key)) {
                continue;
            }
            orphanCandidates.add(physicalFile);
        }

        for (PhysicalFileInfo physicalFile : physicalFiles.values()) {
            AttachmentRowInfo missingRow = missingRowsByKey.get(physicalFile.storageKey());
            if (missingRow != null) {
                issues.add(AttachmentStorageHealthIssueResponse.of(
                        AttachmentStorageHealthIssueType.MISSING_ROW_PHYSICAL_FILE_PRESENT,
                        missingRow.applicationId(),
                        missingRow.attachmentId(),
                        missingRow.rowStatus(),
                        physicalFile.fileKeyHash(),
                        physicalFile.size(),
                        "MISSING attachment row still has a physical file."
                ));
            }
        }

        Set<Long> purgedApplicationIds = purgedApplicationIds(orphanCandidates);
        for (PhysicalFileInfo physicalFile : orphanCandidates) {
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
            addPhysicalIssue(
                    issues,
                    AttachmentStorageHealthIssueType.ORPHAN_PHYSICAL_FILE,
                    physicalFile.storageKey(),
                    physicalFile.size(),
                    "Orphan physical file has no active DB reference."
            );
        }
    }

    /** orphan 후보 파일의 applicationId 중 최종 PURGED 인 집합(치명탐지용, §6.1). */
    private Set<Long> purgedApplicationIds(List<PhysicalFileInfo> orphanCandidates) {
        List<Long> candidateIds = orphanCandidates.stream()
                .map(file -> applicationIdFromKey(file.storageKey()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (candidateIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jobApplicationRepository.findIdsByIdInAndPurgeResult(
                candidateIds, com.shinyoung.recruit.enumeration.PurgeResult.PURGED));
    }

    /** storage key {@code applications/{applicationId}/yyyy/mm/dd/{file}} 의 applicationId 파싱. */
    private Long applicationIdFromKey(String storageKey) {
        String[] parts = storageKey.split("/");
        if (parts.length < 2) {
            return null;
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void addPhysicalIssue(
            List<AttachmentStorageHealthIssueResponse> issues,
            AttachmentStorageHealthIssueType issueType,
            String storageKey,
            Long physicalFileSize,
            String message
    ) {
        issues.add(AttachmentStorageHealthIssueResponse.of(
                issueType,
                null,
                null,
                null,
                HashUtil.sha256(storageKey),
                physicalFileSize,
                message
        ));
    }

    private Map<AttachmentStorageHealthIssueType, Long> countIssues(List<AttachmentStorageHealthIssueResponse> issues) {
        Map<AttachmentStorageHealthIssueType, Long> counts = new EnumMap<>(AttachmentStorageHealthIssueType.class);
        for (AttachmentStorageHealthIssueResponse issue : issues) {
            AttachmentStorageHealthIssueType issueType = AttachmentStorageHealthIssueType.valueOf(issue.category());
            counts.merge(issueType, 1L, Long::sum);
        }
        return counts;
    }

    private Optional<String> toStorageKey(Path storageRoot, String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return Optional.empty();
        }
        try {
            Path rawPath = Path.of(storagePath);
            if (rawPath.isAbsolute()) {
                return Optional.empty();
            }
            Path resolved = storageRoot.resolve(rawPath).normalize();
            if (!resolved.startsWith(storageRoot)) {
                return Optional.empty();
            }
            return toRelativeKey(storageRoot, resolved);
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private Optional<String> toRelativeKey(Path storageRoot, Path path) {
        try {
            Path normalized = path.toAbsolutePath().normalize();
            if (!normalized.startsWith(storageRoot)) {
                return Optional.empty();
            }
            String relativeKey = storageRoot.relativize(normalized)
                    .toString()
                    .replace('\\', '/');
            if (relativeKey.isBlank() || relativeKey.startsWith("../") || relativeKey.equals("..")) {
                return Optional.empty();
            }
            return Optional.of(relativeKey);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean isManagedStorageKey(String storageKey) {
        String[] parts = storageKey.split("/");
        return parts.length == 6
                && "applications".equals(parts[0])
                && isPositiveLong(parts[1])
                && isYear(parts[2])
                && isMonth(parts[3])
                && isDay(parts[4])
                && !parts[5].isBlank();
    }

    private boolean isPositiveLong(String value) {
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isYear(String value) {
        return value.matches("\\d{4}");
    }

    private boolean isMonth(String value) {
        if (!value.matches("\\d{2}")) {
            return false;
        }
        int month = Integer.parseInt(value);
        return month >= 1 && month <= 12;
    }

    private boolean isDay(String value) {
        if (!value.matches("\\d{2}")) {
            return false;
        }
        int day = Integer.parseInt(value);
        return day >= 1 && day <= 31;
    }

    private Long applicationIdOf(ApplicationAttachment attachment) {
        return attachment.getJobApplication().getId();
    }

    private record PhysicalFileInfo(String storageKey, String fileKeyHash, long size) {
    }

    private record PhysicalScanResult(
            Map<String, PhysicalFileInfo> managedFilesByKey,
            long scannedPhysicalFileCount,
            long ignoredPhysicalFileCount
    ) {
    }

    private record AttachmentRowInfo(
            Long applicationId,
            Long attachmentId,
            PhysicalFileStatus rowStatus,
            String storageKey,
            String fileKeyHash
    ) {
    }

    private record RowScanResult(
            List<AttachmentRowInfo> storedRows,
            List<AttachmentRowInfo> deletedRows,
            List<AttachmentRowInfo> missingRows,
            Set<String> deferredPurgeKeys,
            long storedRowCount,
            long deletedRowCount,
            long missingRowCount,
            long pendingBinaryDeleteRowCount
    ) {
    }
}
