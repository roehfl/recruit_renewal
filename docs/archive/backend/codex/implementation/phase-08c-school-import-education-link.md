# Phase 08c - School xlsx import + ApplicationEducation.schoolId 링크

## 1. Phase 요약

- Date: 2026-06-02
- Work type: implementation (Phase 08 세 번째 슬라이스, Phase 08 종료). 설계 §6.3/§6.4, ADR 0004 기준.
- Goal: (A) `ApplicationEducation` 에 optional `schoolId`(application-level 참조) 추가, (B) School xlsx 일괄 import(upsert).

## 2. 구현 범위 (Implemented)

### A. ApplicationEducation.schoolId 링크
- `ApplicationEducation` 에 nullable `schoolId` 추가(강한 FK 없음). 자동완성 선택 시 채우고 직접입력이면 null.
- `EducationRequest`/`EducationResponse`/`AdminEducationResponse` 에 `schoolId` 추가. `schoolName`(free-text snapshot)은 그대로 유지.
- 기존 호출부 비파괴: `ApplicationEducation.create` 오버로드 + `EducationRequest` back-compat 생성자.

### B. School xlsx import(upsert)
- `POST /api/admin/schools/import` (multipart). 행 단위 적용(전체 거부 아님), 유효하지 않은 행만 skip.
- upsert 키: `schoolCode`(있으면) 우선, 없으면 `(schoolName, schoolType, region)` fallback. 기존=update(active 보존), 신규=insert.
- **행 검증(리뷰)**: 적용 전 필수(schoolName) + 컬럼 길이(엔티티 length 와 일치)를 검증해, 초과 행은 DB 예외 대신 **해당 행만 skip**(전체 rollback 방지).
- **natural key 모호성(리뷰)**: schoolCode 없는 fallback 매칭이 2건 이상이면 update 대상이 모호 → **해당 행 skip + 사유**(master 오염 방지).
- 파일 방어: `.xlsx`만, 크기/행수 한도(`UploadProperties` 재사용), header signature, formula 셀 skip, blank 행 skip.
- 결과: `inserted`/`updated`/`skipped` 카운트 + skip 사유(rowNumber/reason).

## 3. Out of scope

- `schoolId` 존재 검증(강한 FK/validation 미결합 — ADR 0004). 프론트가 유효 schoolId 전송 가정.
- SCHOOL funnel 통계(07c 보류, 별도 후속).
- import preview/commit 분리(단일 commit + 요약 채택). all-or-nothing 아님(행 단위 skip).
- 과거 free-text `schoolName` 의 소급 매칭(backfill).

## 4. 변경 파일

### Created (main)
- `dto/request/SchoolImportRowRequest.java`
- `dto/response/SchoolImportRowError.java`, `dto/response/SchoolImportResponse.java`
- `service/SchoolImportParser.java`, `service/SchoolImportService.java`

### Modified (main)
- `domain/entity/ApplicationEducation.java` — nullable `schoolId` + `create` 오버로드(14-arg) + `idx_application_education_school` 인덱스(리뷰, SCHOOL 통계 기반).
- `dto/request/EducationRequest.java` — `schoolId` 컴포넌트 + back-compat 생성자(13-arg).
- `service/ApplicationEducationService.java` — `toEducation` 에서 `schoolId` 전달.
- `dto/response/EducationResponse.java`, `dto/response/AdminEducationResponse.java` — `schoolId` 추가.
- `domain/repository/SchoolRepository.java` — `findBySchoolCode`, `findByNaturalKey`.
- `service/SchoolImportService.java` — 행 길이 검증 + natural key 모호성 skip(리뷰).
- `controller/AdminSchoolController.java` — `POST /admin/schools/import`.

### Created (test)
- `controller/SchoolImportControllerTest.java` (9)
- `service/SchoolImportParserTest.java` (5, 파일 방어 단위)
- `service/ApplicationEducationServiceTest.java` — schoolId 테스트 1건 추가(기존 파일).

## 5. 클래스별 설명

- `ApplicationEducation`(Entity, 수정): `school_id` nullable 컬럼 + `create(...)` 13/14-arg 오버로드(기존 호출부 비파괴).
- `EducationRequest`(Request DTO, 수정): canonical 에 `schoolId` 추가, 13-arg back-compat 생성자가 schoolId=null 로 위임(JSON 은 name 기반이라 schoolId optional).
- `SchoolImportParser`(Service): xlsx 파일 방어 + 행 파싱(07d 패턴). header=[schoolCode/schoolName/schoolType/educationMode/region/address/countryCode].
- `SchoolImportService`(Service): upsert(schoolCode 우선 + natural key fallback), insert/update/skip 카운트. **행 검증(필수+컬럼 길이)** 으로 초과 행 skip(DB 예외 회피), **natural key 2건 이상이면 모호 skip**. formula/blank 행도 skip+사유. (`ExistingMatch` 내부 record 로 매칭/없음/모호 구분.)
- `SchoolRepository`(수정): `findBySchoolCode`, `findByNaturalKey`(null 필드 IS NULL 매칭).
- `SchoolImportResponse`/`SchoolImportRowError`(Response DTO): 결과 요약/스킵 사유.

## 6. API 목록

| Method | Path | 접근 | Purpose | Request | Response |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/admin/schools/import` | admin | xlsx 일괄 upsert | multipart `file` | `ApiResponse<SchoolImportResponse>` |

- 라우팅: `/admin/schools/import`(literal)이 `/admin/schools/{id}`(template)보다 우선 → 충돌 없음.
- 응답 헤더/보안은 기존과 동일(`/api/admin/**` admin).

## 7. Entity 관계 요약

- `ApplicationEducation.schoolId`(nullable) → `School.id` 를 application-level 로 참조(강한 FK 없음, ADR 0004). 매칭된 학력만 학교별 통계 가능, 미매칭은 null.
- 신규 테이블 없음(School 은 08b). 스키마 변경은 `application_education.school_id` 컬럼 추가 → `ddl-auto`(update) 자동.

## 8. 비즈니스 규칙

- `schoolId` optional, 미선택 null. 존재 검증 안 함(app-level 참조).
- import upsert: schoolCode 우선, 없으면 (name,type,region) fallback. update 는 active 보존(비활성화하지 않음). fallback 매칭 2건 이상이면 모호 skip.
- import 행 검증: schoolName 필수 + 컬럼 길이(엔티티 length 와 동일) 초과 시 해당 행 skip(전체 rollback 방지). blank schoolName/formula 셀도 skip + 사유. 파일 레벨 오류(.xlsx/크기/행수/header)는 400.
- `schoolName` free-text snapshot 유지(master 정규명과 별개).

## 9. 테스트 커버리지

- `SchoolImportControllerTest` (9): schoolCode 기준 insert+update, natural key fallback upsert(중복 insert 안 함), blank schoolName skip+사유, **컬럼 길이 초과 skip**, **formula 셀 행 skip**, **natural key 모호(2건) skip**, 비-xlsx 400, wrong header 400, 인가(403/401).
- `SchoolImportParserTest` (5, unit): maxRows 초과/maxFileSize 초과/.xls/wrong header → 거부, 정상 파싱.
- `ApplicationEducationServiceTest` (+1): 자동완성 선택 학력은 schoolId 저장/응답, 직접입력 학력은 schoolId null.
- 회귀: `AdminApplicationSection*`(education 응답에 schoolId 추가) 비파괴.

## 10. 테스트 결과

- 명령: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*School*" --tests "*ApplicationEducation*" --tests "*AdminApplicationSection*" --no-daemon`
- 결과: BUILD SUCCESSFUL — School import 9 + School import parser 5 + School 12 + ApplicationEducation(신규 schoolId) + AdminApplicationSection 회귀 전부 통과.
- 비고: 부분 실행(School/Education 영역).

## 11. 리뷰 반영 (instruction.md, 5건)

- **(Blocking) import 행 길이 미검증** — 엔티티 컬럼 길이 초과 행이 DB flush 시 `DataIntegrityViolationException` → import 전체 rollback 가능. `validateRow`(필수 + 길이) 로 해당 행만 skip + 사유.
- **(Blocking) natural key 중복 시 임의 첫 행 update** — schoolCode 없는 fallback 매칭 2건 이상이면 모호하므로 `ExistingMatch.ambiguous` 로 해당 행 skip(조용한 master 오염 방지).
- **(Medium) import 방어 테스트 부족** — formula 셀 skip / 길이 초과 skip / natural key 모호 skip(controller) + maxRows·maxFileSize·확장자·header(parser unit) 회귀 추가.
- **(Medium) `ApplicationEducation.schoolId` 인덱스 없음** — `idx_application_education_school` 추가(향후 SCHOOL funnel dimension 기반).

## 12. Known limitations

- `schoolId` 존재/active 검증 미수행(app-level 참조). dangling schoolId 가능(프론트 책임). 필요 시 후속 검증.
- import 는 단일 commit + 행 단위 skip(preview/STALE 토큰 없음). master 데이터라 보수성 낮음.
- import max-rows 는 `recruit.upload.max-rows`(기본 10,000) 재사용 — 대형 전국 학교 데이터셋은 env 로 상향 필요.
- 과거 free-text 학력 소급 매칭 없음.

## 13. Next phase considerations

- Phase 08 종료. 후속: SCHOOL/CERTIFICATE funnel dimension(07c 보류, schoolId 기반), 메시지 발송, 개인정보 파기/감사(영속 ActivityLog).
