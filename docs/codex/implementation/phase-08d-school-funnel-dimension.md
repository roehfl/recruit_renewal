# Phase 08d - SCHOOL Funnel Dimension

## 1. Phase 요약

- Date: 2026-06-02
- Work type: implementation (Phase 08 네 번째 슬라이스 — 07c에서 보류한 학교별 funnel dimension 해소).
- Goal: 07c funnel 통계에 **SCHOOL dimension**을 추가한다. 08c의 `ApplicationEducation.schoolId`(최종학력 매칭)를 기반으로 학교별 funnel을 산출하고, 미매칭/topN 초과는 '기타'로 합산한다.
- 설계 근거: `CONTEXT.md`(Dimension/학교별=최종학력 1교, topN+기타), 07c 설계, ADR 0004.

## 2. 구현 범위 (Implemented)

- `GET /api/admin/job-postings/{jobPostingId}/statistics/funnel?dimension=SCHOOL&topN=` 활성화(기존 엔드포인트 확장).
- 지원자별 **최종학력(가장 높은 `EducationLevel`) 1교**의 `schoolId`로 코호트 P를 분할(application 단위 distinct).
- 미매칭(최종학력에 `schoolId` 없음 또는 학력 없음) + topN 초과 학교 → **'기타'** 한 그룹으로 합산.
- 학교 그룹 정렬: 인원 desc, `schoolId` asc. topN 기본 10(최대 100). 그룹별 funnel은 기존 `computeCohort` 재사용.
- `CERTIFICATE`는 master 부재로 여전히 미지원(400).

## 3. Out of scope

- `CERTIFICATE` dimension(master 없음, free-text 부정확).
- 과거 free-text 학력 소급 매칭(08c와 동일 — schoolId 없는 건은 '기타').
- statistics audit(07c 정책: 집계값만, audit 없음).

## 4. 변경 파일

### Created (main)
- `dto/response/FunnelSchoolEducationRow.java` (projection: applicationId/educationLevel/schoolId)

### Modified (main)
- `service/FunnelStatisticsService.java` — `computeSchoolDimension` + `finalSchoolByApplication`/`pickFinalEducation`, `ApplicationEducationRepository`/`SchoolRepository` 주입, `parseSupportedDimension`에 SCHOOL 허용.
- `domain/repository/ApplicationEducationRepository.java` — `findFunnelSchoolEducations`(공고 코호트 학력 projection).
- `enumeration/FunnelDimension.java` / `controller/AdminStatisticsController.java` — javadoc(SCHOOL 지원, topN 적용) 갱신.

### Modified (test)
- `controller/AdminStatisticsControllerTest.java` — unsupported dimension을 `CERTIFICATE`로 변경, SCHOOL dimension 테스트 2건 추가.

## 5. 클래스별 설명

- `FunnelStatisticsService`(수정):
  - `computeSchoolDimension`: schoolId로 코호트 분할 → 인원 desc·id asc 정렬 → topN 개별 + (초과 학교 + 미매칭)='기타'. 학교명은 `SchoolRepository.findAllById`.
  - `finalSchoolByApplication`: `findFunnelSchoolEducations` 결과를 지원자별로 reduce해 최종학력 1교 schoolId 산출.
  - `pickFinalEducation`: 더 높은 `EducationLevel.ordinal()` 우선, 동률이면 schoolId 보유 쪽 우선.
- `FunnelSchoolEducationRow`(Response/projection DTO): 집계 입력.
- `ApplicationEducationRepository`(수정): `findFunnelSchoolEducations`(submittedAt != null 코호트).

## 6. API 목록

| Method | Path | 접근 | Purpose |
| --- | --- | --- | --- |
| GET | `/api/admin/job-postings/{jobPostingId}/statistics/funnel?dimension=SCHOOL&topN=` | admin | 학교별 funnel(최종학력 매칭 + 미매칭/초과='기타') |

- dimension 미지정=overall, POSITION=분야별, SCHOOL=학교별, CERTIFICATE=400. topN은 SCHOOL에서만 적용(POSITION 무시).
- 응답 구조는 기존 `FunnelResponse`/`DimensionFunnelResponse(groupId, groupName, ...)` 재사용. SCHOOL: groupId=schoolId/groupName=schoolName, 기타: groupId=null/groupName="기타".

## 7. Entity 관계 요약

- 신규 테이블/엔티티 없음(읽기 전용 통계 확장). `ApplicationEducation.schoolId`(08c, app-level) → `School`(08b) 매칭을 in-memory 집계.

## 8. 비즈니스 규칙

- 학교 = 지원자 최종학력(최고 `EducationLevel`) 1교. 그 학력의 schoolId가 null이면 미매칭='기타'.
- application 단위 distinct(한 지원자는 정확히 한 그룹).
- topN(기본 10, 최대 100) 학교만 개별, 초과 학교 + 미매칭은 '기타' 합산.
- 그룹 정렬: 인원 desc, schoolId asc. '기타'는 항상 마지막.
- 집계값만(개인정보 없음), audit 없음.

## 9. 테스트 커버리지

- `AdminStatisticsControllerTest` (SCHOOL +2):
  - 최종학력 1교 매칭(고졸+대학 → 대학), 학교별 그룹 population/funnelPassedCount, 미매칭(직접입력 + 학력없음)='기타' p=2.
  - topN=1 → 인원 최다 학교만 개별, 초과 학교 + 미매칭이 '기타'로 합산.
  - 기존 `unsupported_dimension_returns_bad_request`를 `CERTIFICATE`로 변경(SCHOOL 지원 반영).
- 회귀: overall/POSITION/분포/비율/인가 등 기존 funnel 테스트 비파괴.

## 10. 테스트 결과

- 명령: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*AdminStatisticsControllerTest" --no-daemon`
- 결과: BUILD SUCCESSFUL — 기존 + SCHOOL 2건 전부 통과.
- 비고: 통계 테스트는 엔티티를 repository로 직접 영속화해 클럭 의존(접수기간) 없이 안정적.

## 11. Known limitations

- "최종학력 1교"는 최고 레벨 학력의 schoolId만 사용 — 그 학력이 free-text면 하위 레벨에 매칭 학교가 있어도 '기타'(설계 정의).
- schoolId 존재 검증 없음(08c) → dangling schoolId면 groupId는 있으나 groupName이 null일 수 있음.
- 학교별 통계는 in-memory 집계(공고 단위, bounded). 대형 공고는 07f/후속에서 GROUP BY 전환 검토.
- CERTIFICATE는 미지원 유지.

## 12. Next phase considerations

- (선택) CERTIFICATE dimension(자격명 정규화/master 후), 대형 공고 GROUP BY 전환.
- 메시지 발송, 개인정보 파기/감사(영속 ActivityLog).
