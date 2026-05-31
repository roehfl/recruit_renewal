# Recruit Backend — Context

신영증권 채용 Renewal 백엔드의 도메인 용어 glossary. 구현 세부는 `docs/codex` 문서를 따르고, 이 파일은 용어 정의만 유지한다.

## Language

### Export / Reporting (Phase 07)

**Export**:
관리자가 조회 화면의 데이터를 Excel(xlsx) 또는 PDF 파일로 내려받는 행위. read-only 이며, 도메인 상태를 변경하지 않는다.
_Avoid_: 다운로드(파일 첨부 download와 혼동), 출력

**Excel download**:
admin 조회 결과(applications/stage results/interviews/evaluations)를 xlsx로 export 하는 기능. 기존 list 필터를 재사용하고 page는 무시해 필터된 전체 행을 내보낸다.

**Excel upload**:
관리자가 작성한 xlsx를 올려 `StageResult`를 bulk로 **변경**하는 기능. Phase 07에서 유일하게 쓰기가 발생하는 지점이며, preview(검증·미적용)와 commit(적용)을 분리한다. `InterviewEvaluation`은 Phase 06 경계(평가는 배정 면접관 본인만 작성, 평가 독립성)상 upload 대상에서 **제외**한다 — admin은 엑셀로 평가 등급을 입력/수정하지 않는다.
_Avoid_: Excel import (이 프로젝트에서는 upload로 통일)

**Statistics**:
지원 데이터를 집계한 read-only 수치. 개별 개인정보가 아니라 집계값만 노출한다. `jobPosting` 단위의 **전형 funnel** 형태로 제공한다.

**전형 funnel**:
한 공고에서 "접수 → 그 공고의 stageOrder 순서대로 각 stage PASSED" 로 이어지는 단계별 인원 추이. 동적 stage 구조에 자동 적응한다.

**모집단 P (접수)**:
funnel의 분모가 되는 고정 지원서 코호트. `submittedAt != null`(한 번이라도 제출) 인 지원서를 포함하며 현재 status 와 무관하다(DRAFT·미제출 제외). 각 단계는 동일 P 위에서 계산한다. P 중 `status == SUBMITTED` 는 `currentlySubmittedCount`, `status == WITHDRAWN`(제출 후 철회) 는 `withdrawnCount` 로 별도 표기한다. 코호트를 제출이력으로 고정하므로 조회 시점이 달라도 funnel 이 재현 가능하다.
_Avoid_: "현재 SUBMITTED 집합"(철회 시 줄어들어 재현 불가)

**Dimension (집계 축)**:
funnel을 쪼개는 기준. 전체 / 분야별(`jobPosition`) / 학교별(`schoolName`) / 자격별(`certificateName`). 모든 dimension은 **지원자(application) 단위 distinct** 로 센다. 학교별은 **최종학력(가장 높은 `EducationLevel`) 1교만**, 자격별은 **자격명별 보유 지원자 distinct**. free-text 축(학교·자격)은 topN + '기타' 버킷으로 cardinality를 제한한다.

**funnel 단계 분포**:
각 stage 에서 P 멤버를 `StageResultStatus` 6종(PASSED/FAILED/ABSENT/HOLD/PENDING/WITHDRAWN) + `NO_RESULT` 의 7개 버킷으로 분류한 값. 합은 항상 `|P|`. PASSED 기준으로 누적 비율(P 대비)과 직전 단계 대비 전환율 두 가지를 함께 제공한다.

**NO_RESULT**:
funnel 응답 전용 synthetic 버킷. 해당 stage 에 `StageResult` row 자체가 없는(미초기화·미도달) P 멤버를 가리킨다. DB `StageResultStatus` enum 값이 **아니며**, Excel upload 의 허용 입력값도 **아니다**.
_Avoid_: PENDING(= StageResult row 존재, 결정 전 — NO_RESULT 와 구분)

**Application PDF**:
지원자 한 명의 지원서를 PDF로 렌더링한 출력물. 본질적으로 개인정보를 포함한다.

**Audit log (export)**:
누가/언제/어떤 dataset을/어떤 필터로/몇 행을 export 했는지 기록. 현재는 SLF4J 구조적 로그로만 남기고, 영속 `ActivityLog` 도메인 생성 시 그쪽으로 이관한다.

## Flagged ambiguities

- **CI (`ci`/`ciHash`)**: NICE 본인확인의 연계정보. 민감 식별자이므로 **어떤 export(Excel/PDF)에도 절대 포함하지 않는다**. `password`·암호화키도 동일하게 절대 노출하지 않는다. `name`/`phoneNumber`/`email`은 admin 운영(연락·발송) 목적상 평문으로 export 하되 audit 로그를 남긴다.
- **"학교별 통계"의 학교**: 현재 `ApplicationEducation.schoolName` 은 free-text 다. `School` master(Phase 08)가 없어 학교 grouping은 본질적으로 부정확하다 — 통계 설계 시 이 한계를 전제로 한다.
