package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * typed AuditMetadata taxonomy 계약 테스트(Phase 09b).
 *
 * <p>metadataJson 은 actionType 별 allowlist + PII-free 여야 한다(설계 §5.1). sealed permits 와
 * 각 record 의 component 이름을 명시 allowlist 로 고정해, 누군가 metadata 에 지원자 PII 필드
 * (applicantName/email/phone/sourceFileName 원문 등)를 추가하면 이 테스트가 깨진다.
 */
class AuditMetadataContractTest {

    /** 지원자 PII 또는 사용자 제공 원문이 의심되는 component 이름 조각(소문자 비교). */
    private static final Set<String> FORBIDDEN_NAME_FRAGMENTS = Set.of(
            "applicantname", "username", "email", "phone", "address", "birth",
            "ci", "cihash", "password", "sourcefilename" // hash/extension 은 별도 허용 목록에서 명시
    );

    private static final Map<Class<?>, Set<String>> EXPECTED_COMPONENTS = Map.of(
            ExportMetadata.class, Set.of("datasetType", "filtersHash", "filtersSafeJson", "rowCount", "fileName"),
            PdfMetadata.class, Set.of("applicationId", "jobPostingId", "jobPositionId"),
            UploadMetadata.class, Set.of("stageId", "outcome", "rowCount", "changedCount", "unchangedCount",
                    "errorCount", "staleCount", "sourceFileNameHash", "sourceFileExtension", "sourceFileSize",
                    "contentHash"),
            UploadConflictMetadata.class, Set.of("stageId", "sourceFileNameHash", "sourceFileExtension",
                    "sourceFileSize", "contentHash"),
            StageResultChangeMetadata.class, Set.of("stageId", "changedCount"),
            EvaluationReopenMetadata.class, Set.of("interviewId", "previousSubmittedAt"),
            AttachmentAdminMetadata.class, Set.of("physicalDeleteRequested")
    );

    @Test
    void AuditMetadata는_sealed이고_permits가_고정되어_있다() {
        assertThat(AuditMetadata.class.isSealed()).isTrue();
        Set<Class<?>> permitted = Arrays.stream(AuditMetadata.class.getPermittedSubclasses())
                .collect(Collectors.toSet());
        assertThat(permitted).containsExactlyInAnyOrderElementsOf(EXPECTED_COMPONENTS.keySet());
    }

    @Test
    void 모든_metadata_record의_component는_allowlist와_일치한다() {
        for (Map.Entry<Class<?>, Set<String>> entry : EXPECTED_COMPONENTS.entrySet()) {
            Class<?> recordClass = entry.getKey();
            assertThat(recordClass.isRecord()).as("%s must be a record", recordClass).isTrue();
            Set<String> actual = Arrays.stream(recordClass.getRecordComponents())
                    .map(RecordComponent::getName)
                    .collect(Collectors.toSet());
            assertThat(actual)
                    .as("%s component allowlist", recordClass.getSimpleName())
                    .containsExactlyInAnyOrderElementsOf(entry.getValue());
        }
    }

    @Test
    void metadata_component에_PII_의심_이름이_없다() {
        // sourceFileNameHash 는 해시라 허용 — 원문(sourceFileName 단독)은 금지(리뷰 2차 #2).
        Set<String> allowedDespiteFragment = Set.of("sourcefilenamehash");

        List<String> violations = EXPECTED_COMPONENTS.keySet().stream()
                .flatMap(recordClass -> Arrays.stream(recordClass.getRecordComponents())
                        .map(component -> recordClass.getSimpleName() + "." + component.getName()))
                .filter(qualified -> {
                    String name = qualified.substring(qualified.indexOf('.') + 1).toLowerCase(Locale.ROOT);
                    if (allowedDespiteFragment.contains(name)) {
                        return false;
                    }
                    return FORBIDDEN_NAME_FRAGMENTS.stream().anyMatch(name::contains);
                })
                .toList();

        assertThat(violations).isEmpty();
    }
}
