# Phase 08e - CERTIFICATE Funnel Dimension

## 1. Phase 요약

- Date: 2026-06-02
- Work type: implementation (Phase 08 다섯 번째 슬라이스 — 07c에서 보류한 자격별 funnel dimension 해소).
- Goal: 07c funnel 통계에 **CERTIFICATE dimension**을 추가한다. 자격명(free-text)을 정규화해 **자격명별 보유 지원자 distinct**로 그룹화하고, topN + '기타'로 cardinality를 제한한다.
- 설계 근거: `CONTEXT.md`(Dimension: 자격별=자격명별 보유 지원자 distinct, topN+기타), 07c 설계.

## 2. 구현 범위 (Implemented)

- `GET /api/admin/job-postings/{jobPostingId}/statistics/funnel?dimension=CERTIFICATE&topN=` 활성화.
- 자격명 **정규화(trim + 내부 공백 압축)** 후 그룹 키로 사용(빈 자격명 제외).
- 그룹 = 해당 자격을 **보유한 지원자 distinct**(P 내). SCHOOL/POSITION과 달리 **그룹이 중복**(한 지원자가 여러 자격 보유 시 여러 그룹) → 그룹 population 합이 |P|를 초과할 수 있다.
- 보유자 수 desc, 자격명 asc 정렬. topN(기본 10, 최대 100; `0/null`→10) 자격만 개별, **초과 자격 보유자(distinct)는 '기타'**로 합산.
- 무보유(자격 없음) 지원자는 어느 그룹에도 포함하지 않는다(보유 의미).
- POSITION/SCHOOL/CERTIFICATE 외 잘못된 dimension 값은 400.

## 3. Out of scope

- 자격 master/표준화(자격명 동의어 통합 등). 현재는 정규화(trim+공백)로만 묶음.
- '미보유(없음)' 별도 버킷(보유 지원자 통계라 제외).
- 과거 데이터 정제. statistics audit(07c 정책: 집계값만, audit 없음).

## 4. 변경 파일

### Created (main)
- `dto/response/FunnelCertificateRow.java` (projection: applicationId/certificateName)

### Modified (main)
- `service/FunnelStatisticsService.java` — `computeCertificateDimension` + `normalizeCertificateName`/`rowsOf`, `ApplicationCertificateRepository` 주입, dimension 상수 일반화(`DEFAULT_DIMENSION_TOP_N`/`MAX_DIMENSION_TOP_N`/`OTHER_GROUP_NAME`), `parseSupportedDimension`에서 CERTIFICATE 허용(전 dimension 지원, 잘못된 값만 400), **dimension dispatch를 switch expression으로 전환(exhaustiveness 강제, 리뷰)**.
- `domain/repository/ApplicationCertificateRepository.java` — `findFunnelCertificates`(공고 코호트 자격 projection).
- `enumeration/FunnelDimension.java` / `controller/AdminStatisticsController.java` — javadoc 갱신(CERTIFICATE 지원).

### Modified (test)
- `controller/AdminStatisticsControllerTest.java` — unsupported dimension을 잘못된 값(`NOT_A_DIMENSION`)으로 변경, CERTIFICATE 테스트 3건 추가(distinct/정규화/중복, topN/'기타', top↔기타 중복).

## 5. 클래스별 설명

- `FunnelStatisticsService`(수정):
  - `computeCertificateDimension`: 자격명 정규화 → `Map<자격명, Set<applicationId>>`(distinct) → 보유자 수 desc·이름 asc → topN 개별 + 초과 자격 보유자 union='기타'. 그룹 cohort는 `cohortById`로 사상해 기존 `computeCohort` 재사용.
  - `normalizeCertificateName`: trim + `\\s+`→' '. 빈 값 null(제외).
  - dimension 상수/`OTHER_GROUP_NAME`은 SCHOOL과 공유(일반화).
- `FunnelCertificateRow`(projection DTO): 집계 입력.
- `ApplicationCertificateRepository`(수정): `findFunnelCertificates`(submittedAt != null 코호트).

## 6. API 목록

| Method | Path | 접근 | Purpose |
| --- | --- | --- | --- |
| GET | `/api/admin/job-postings/{jobPostingId}/statistics/funnel?dimension=CERTIFICATE&topN=` | admin | 자격별 funnel(자격명 정규화 보유 지원자 distinct + topN/'기타') |

- 응답은 기존 `FunnelResponse`/`DimensionFunnelResponse(groupId, groupName, ...)` 재사용. CERTIFICATE: groupId=null(자격명은 식별 id 없음)/groupName=정규화 자격명, 기타: groupId=null/groupName="기타".
- topN은 SCHOOL/CERTIFICATE에 적용, POSITION 무시.

## 7. Entity 관계 요약

- 신규 테이블/엔티티 없음(읽기 전용 통계 확장). `ApplicationCertificate.certificateName`(free-text)을 in-memory 집계.

## 8. 비즈니스 규칙

- 그룹 키 = 정규화(trim+공백 압축) 자격명. 빈 자격명 제외.
- 그룹 = 자격 보유 지원자 distinct(P 내). **그룹 중복 허용**(한 지원자 여러 그룹). 그룹 합 ≠ |P| 가능.
- topN(기본 10, 최대 100; `0/null`→10) 개별, 초과 자격 보유자 distinct = '기타'. 무보유 지원자 제외.
- 집계값만(개인정보 없음 — 자격명은 비민감), audit 없음.

## 9. 테스트 커버리지

- `AdminStatisticsControllerTest` (CERTIFICATE +3):
  - 자격명별 보유 지원자 distinct + **정규화(trailing space 병합)** + **중복 집계**(app1이 정보처리기사·TOEIC 두 그룹), 무보유 지원자 제외.
  - topN=1 → 최다 보유 자격만 개별, 초과 자격 보유자(distinct)='기타'로 합산.
  - **top 그룹 ↔ '기타' 중복 허용**(app1=Common+Rare1, topN=1 → Common p=2, 기타 p=1로 app1이 양쪽에 distinct 집계, 리뷰).
  - 기존 `unsupported_dimension_returns_bad_request`를 잘못된 dimension 값으로 변경.
- 회귀: overall/POSITION/SCHOOL/분포/비율/인가 등 기존 funnel 테스트 비파괴.

## 10. 테스트 결과

- 명령: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*AdminStatisticsControllerTest" --no-daemon`
- 결과: BUILD SUCCESSFUL — 기존 + SCHOOL 5 + CERTIFICATE 3 전부 통과.
- 비고: 통계 테스트는 엔티티를 repository로 직접 영속화해 클럭 의존(접수기간) 없이 안정적.

## 11. 리뷰 반영 (instruction.md, 3건)

- **(Medium) top 그룹 ↔ '기타' 중복 허용 테스트** — app1이 topN 자격(Common)과 overflow 자격(Rare1)을 모두 보유하면 Common(top)과 '기타' 양쪽에 distinct 집계됨을 회귀로 고정("그룹 중복 허용 + 기타도 overflow 보유자 distinct" 정의 확정).
- **(Medium) 자격명 정규화 한계** — 동의어/표기차는 다른 그룹으로 남음(현 phase 문제 아님). **자격 master/표준화는 별도 후속 phase**로 명시(known limitation 유지).
- **(Low) future enum allowlist** — `dimension` dispatch를 `switch expression`으로 전환해 **exhaustiveness 강제**(FunnelDimension에 값 추가 시 dispatch 누락이 컴파일 에러). 빈 dimensions 로 조용히 새는 것을 방지.

## 12. Known limitations

- 자격명은 정규화(trim+공백)로만 묶어, 동의어/표기 차이("정보처리기사" vs "정보 처리 기사" 외 의미적 동치)는 다른 그룹이 된다 — **자격 master/표준화 phase에서 해결**(별도 후속).
- 그룹 중복(보유 지원자 distinct)이라 dimension population 합이 |P|와 다를 수 있음(설계 의도). overall funnel은 |P| 기준 그대로.
- in-memory 집계(공고 단위, bounded). 대형 공고는 후속 GROUP BY 전환 검토.

## 13. Next phase considerations

- (선택) 자격 master/표준화, 대형 공고 GROUP BY 전환.
- 메시지 발송, 개인정보 파기/감사(영속 ActivityLog).
