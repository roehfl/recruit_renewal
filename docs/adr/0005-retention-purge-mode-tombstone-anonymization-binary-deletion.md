# Retention 파기 방식은 tombstone 익명화 + 첨부 바이너리 물리삭제로 한다 (crypto-shred·전면 hard delete 기각)

Phase 09 지원자 개인정보 파기의 기본 방식은 **tombstone anonymization + binary physical delete** 다. 관계형 row 는 지원자 **원문 PII 를 파기**하고, 통계/감사 연결에 필요한 **비식별 tombstone**(ids·코드·날짜 차원·파기 메타)만 남긴다. 첨부파일은 익명화가 아니라 **스토리지에서 바이너리를 물리 삭제**하고 DB 에는 attachment tombstone(`originalFilename` 제거, 필요 시 `filenameHash`)만 둔다. Applicant 공통 PII 는 ref-count 로 처리한다(해당 Applicant 의 **모든** JobApplication 이 파기 대상이 됐을 때만 익명화). ActivityLog 는 이 파기 job 이 수정/삭제/마스킹하지 않는다(별도 lifecycle policy 대상).

## Status

accepted-with-implementation-gate (2026-06-04, Phase 09 design / grill-with-docs). 본 ADR 의 실질 안전장치인 entity 별 field-level 익명화 allowlist 는 `docs/codex/implementation/phase-09-pii-field-inventory.md` 로 산출되었다(instruction.md 리뷰 #1). 그 인벤토리의 §9 DDL 목록·§10 확인 항목(날짜 보존 trade-off 등)이 **확정**되면 `accepted` 로 전환하고 9d 구현을 시작한다.

## Considered Options

- **crypto-shred(암호화키 파기)** — 기각. 현재 암호화 필드는 `Applicant.ci` 정도뿐이고(email/phone/name/answers/섹션 전부 평문) AES 키도 **글로벌 단일 키**(`AES_SECRET_KEY`)라 지원자별/지원서별/코호트별 파기가 불가능하다. per-subject envelope key 구조는 별도 대규모 보안 설계이므로 Phase 09 범위가 아니다(후속 도입 시 재고).
- **전면 hard delete** — 기각(기본 전략으로는). cascade 삭제 위험(`CLAUDE.md §7.2` 금지 기조), ActivityLog/PurgeRecord 의 `applicationId` 감사 연결 단절, funnel 모집단 P(`submittedAt != null` 코호트) 축소로 과거 통계 재현 불가. 단 **첨부 바이너리와 지원서 원문성 데이터**는 hard delete/null 대상이다.
- **tombstone anonymization + binary delete** — 채택. 원문 PII 는 비가역 소거하되 비식별 골격을 남겨 통계 재현·감사 연결·cascade 안전성을 동시에 확보.

## Consequences

- **보존(비식별 tombstone) 후보**: `applicationId`, `jobPostingId`, `jobPositionId`, stage/result/status code, submitted date bucket(또는 필요한 최소 날짜 차원), `purgedAt`, `purgeBatchId`, `purgeResult`.
- **소거/익명화 대상**: `name`, `email`, `phone`, `ci`, `address`, 자기소개서/answers 원문, 학력/경력/자격/어학/수상 등 재식별 가능 상세 섹션 원문, 첨부 바이너리, `originalFilename`, PII 가능성 있는 자유입력 reason/comment.
- 인원 distinct 카운트는 PII 가 불필요하므로 **funnel/statistics 재현이 유지**된다(school/certificate 같은 free-text 차원은 `schoolId` 등 master 매칭분만 유지, 미매칭 free-text 는 소거되어 '기타'로 흡수 — 이미 ADR-0004 의 한계와 정렬).
- 감사는 `applicationId`(tombstone 생존) 및 hard-delete/연결 대비 `applicantRefHash`(HMAC-SHA256 + server pepper)로 연결한다.
- **익명화의 완전성·비가역성이 "파기" 인정 요건**이다. quasi-identifier 잔존(생년월일+학교+지역 조합 등) 이 재식별로 이어지지 않도록 entity 별 field-level allowlist 를 명시한다 — `phase-09-pii-field-inventory.md` 가 그 산출물이며 본 ADR 의 실질 안전장치다. NOT NULL String PII 는 PLACEHOLDER(`"__PURGED__"`), NOT NULL date PII(+학력·경력 정확 날짜)는 ALTER nullable / 일반화 후 NULLIFY. **`ci`→null, `ciHash`→`"PURGED:"+UUID` overwrite**(리뷰 2차 #1) — `HashUtil.sha256` 이 plain SHA-256 이고 회원가입이 `existsByCiHash` 로 중복가입을 막으므로 ciHash 보존 = CI 연결자 잔존 = 비가역 파기 위반. 파기 후 동일 CI 재가입은 허용(파기 우선).
- 첨부 바이너리 물리삭제는 비가역이며 스토리지 삭제 실패 시 파기를 실패/스킵으로 감사한다(`actionResult` = FAILURE/SKIPPED).
