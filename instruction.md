남은 지적사항
1. Major — stage results export 컬럼 계약이 아직 충돌합니다

문서 7.5에서는 stage results export를 “기존 AdminStageResultResponse 그대로”라고 하며 컬럼을 stageResultId, stageId, applicationId, applicantName, ... decidedAt 정도로 적고 있습니다. 그런데 9.3에서는 stage results export/upload-template이 stageResultUpdatedAt을 포함하므로 upload template으로 적합하다고 합니다.

이건 실제 구현 시 바로 충돌합니다. stageResultUpdatedAt이 없으면 07d의 STALE_ROW 검증을 할 수 없습니다.

수정 권고:

둘 중 하나로 확정하세요.

안 1 — stage results export도 upload source로 쓴다.
→ stage results export 컬럼에 stageResultUpdatedAt을 반드시 추가한다.
→ 단, 기존 list-parity보다 upload-compatible export에 가깝다는 점을 명시한다.

안 2 — stage results export는 순수 목록용으로 둔다.
→ upload source는 GET /admin/stages/{stageId}/results/upload-template 하나만 허용한다.
→ 9.3의 “stage results export도 upload source” 문장을 제거한다.

개인적으로는 안 2가 더 깔끔합니다.
목록 export와 upload template은 목적이 다릅니다. 운영자가 보기 좋은 export와 시스템이 검증 가능한 upload sheet를 굳이 하나로 묶을 필요 없습니다.

2. Major — upload blank cell 의미가 정의되지 않았습니다

현재 upload 편집 가능 컬럼은 resultStatus, score, comment이고, read-only echo는 stageResultId/applicationId/applicantName/stageResultUpdatedAt입니다. 하지만 blank cell의 의미가 없습니다.

정해야 할 것:

resultStatus blank → 오류인가? 기존값 유지인가?
score blank → null로 clear인가? 기존값 유지인가?
comment blank → 빈 문자열/NULL로 clear인가? 기존값 유지인가?
변경 없는 row → bulkUpdateResults에 넘기는가? skip하는가?

권고 정책:

- resultStatus: 필수. blank면 row error.
- score: blank면 null clear.
- comment: blank면 null 또는 empty clear. 둘 중 하나로 고정.
- 변경 없는 row: diff에는 unchanged로 표시하고 commit 적용 대상에서는 제외.
- 단, stale check는 전체 row 또는 변경 대상 row 중 어느 범위에 적용할지 명시.

금융권 운영 엑셀에서는 “빈칸 = 기존값 유지”로 설계하면 오히려 실수와 오해가 늘어납니다. template에는 현재값을 채워주고, 사용자가 지우면 clear로 보는 방식이 더 명확합니다.

3. Major — stageResultUpdatedAt token의 포맷/정밀도/검증 방식이 부족합니다

stageResultUpdatedAt을 현재 DB StageResult.updatedAt과 비교한다고 되어 있습니다. 방향은 맞지만 Excel은 날짜 셀을 건드리면 포맷, timezone, millisecond/microsecond precision이 깨질 수 있습니다.

추가해야 할 규칙:

- stageResultUpdatedAt은 Excel date cell이 아니라 string cell로 export한다.
- ISO-8601 문자열로 고정한다. 예: 2026-05-29T16:30:12.123456+09:00
- DB timestamp precision에 맞춰 normalize 후 비교한다.
- 해당 셀이 date/numeric/formula이면 row error.
- 사용자가 수정하지 말아야 하는 read-only token임을 header/comment로 표시한다.

더 강하게 가려면 updatedAt 원문 대신 opaque token을 쓰는 방법도 있습니다.

stageResultVersionToken = HMAC(stageResultId | applicationId | stageId | updatedAt)

이러면 사용자가 token을 임의 수정해서 stale check를 우회하는 것도 막을 수 있습니다. 필수는 아니지만, 운영 리스크를 줄이려면 고려할 만합니다.

4. Medium — stageId 컬럼 처리도 정리해야 합니다

9.3 설명에는 stage results export/upload-template이 stageId를 포함한다고 되어 있습니다. 그런데 upload row DTO에는 stageId가 없습니다. 검증은 path {stageId}와 실제 StageResult.stage.id를 비교하는 3중 검증입니다.

이 자체는 동작합니다. 하지만 엑셀에 stageId 컬럼이 있는데 파싱/검증하지 않으면 문서와 구현이 어긋납니다.

정리 방향:

안 1 — row에 stageId 컬럼을 둔다.
→ row DTO에 stageId를 추가하고 stageResult.stage.id == row.stageId == path stageId까지 검증한다.

안 2 — row에는 stageId 컬럼을 두지 않는다.
→ stageId는 파일 header metadata 또는 endpoint path로만 판단한다.

권고는 안 2입니다. stageResultId + applicationId + path stageId면 충분합니다. row마다 stageId를 반복할 필요가 없습니다.

5. Medium — export 응답 방식 문서가 일부 불일치합니다

본문 7.4는 temp file 선생성 방식을 채택했다고 했습니다. 그런데 API List와 HTML report 쪽에는 /admin/applications/export 응답이 xlsx (StreamingResponseBody)로 남아 있습니다.

수정 권고:

Response: xlsx Resource 또는 ResponseEntity<Resource>

그리고 temp file 삭제 책임을 명확히 해야 합니다.

- service: temp xlsx 생성까지만 담당
- controller/response layer: 파일 전송 완료 후 finally에서 삭제
- 실패/예외 발생 시에도 삭제
- service 내부 finally에서 먼저 삭제하지 않음

Resource로 내려주는데 service finally에서 삭제하면, 실제 response body write 전에 파일이 사라질 수 있습니다.

6. Medium — Test Strategy가 새 보강 항목을 다 커버하지 않습니다

Test Strategy에는 row cap, NO_RESULT, upload all-or-nothing, PDF ci 부재 등은 있습니다. 하지만 새로 추가된 핵심 보강 항목 일부가 빠져 있습니다.

추가해야 할 테스트:

Export
- formula injection 위험 prefix escaping 검증
- xlsx read-back 시 ci/ciHash/password 컬럼 부재
- no-store/nosniff/content-disposition header 검증
- count > maxRows 시 실제 workbook 생성 안 함

Upload
- stageResultUpdatedAt 불일치 → STALE_ROW, update 0건
- duplicate stageResultId row 거부
- formula cell 거부
- .xls/.csv/.xlsm 거부
- header signature/version 불일치 거부
- maxUploadRows/maxUploadFileSize 초과 거부
- blank resultStatus/score/comment 정책 검증
- stage mismatch/applicationId mismatch 검증

PDF
- th:utext 미사용 정적 검사 또는 template convention test
- 외부 URL resource load 차단
- no-store/nosniff header 검증

특히 STALE_ROW 테스트는 07d의 핵심입니다. 빠지면 lost update 방어가 문서에만 있고 실제 보장되지 않을 수 있습니다.

7. Low — audit의 filtersSafeJson은 allowlist 기반으로 못 박는 게 좋습니다

audit schema에 filtersHash, filtersSafeJson을 둔 것은 좋습니다. 다만 나중에 검색 필터에 이름/전화번호/email 같은 값이 들어오면 filtersSafeJson이 PII 로그가 될 수 있습니다. 현재 문서에는 PII 직접 기록 금지가 있으므로 방향은 맞지만, 구현자가 raw request map을 그대로 넣지 않도록 더 명확히 하면 좋습니다.

추가 문장:

filtersSafeJson은 allowlist 기반으로 구성하며, applicantName/email/phoneNumber/comment 등 PII성 필터가 생기면 마스킹하거나 제외한다. raw request parameter map을 그대로 audit에 기록하지 않는다.