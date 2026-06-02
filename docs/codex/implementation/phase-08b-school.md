# Phase 08b - School (검색/자동완성 + admin CRUD)

## 1. Phase 요약

- Date: 2026-06-02
- Work type: implementation (Phase 08 두 번째 슬라이스). 설계 `docs/codex/design/phase-08-commoncode-school-master-design.md` §6 기준.
- Goal: 학교 master(`School`)를 추가형으로 도입한다. public 자동완성/검색 + admin CRUD. xlsx 일괄 import 와 `ApplicationEducation.schoolId` 링크는 08c.

## 2. 구현 범위 (Implemented)

- `School` 엔티티: `schoolCode`(nullable, unique)+`schoolName`+`schoolType`+`educationMode`+`region`+`address`+`countryCode`+`active`, `BaseEntity`.
- public 검색: `GET /api/schools?q=&schoolType=` — 활성만, 이름 prefix 우선 + contains, top-N(20). 경량 응답(id/name/type/region).
- admin: `GET /api/admin/schools`(비활성 포함 페이지 목록), `POST /api/admin/schools`(생성), `POST /api/admin/schools/{id}`(수정).
- `schoolCode` 식별 키 불변, soft delete(`active=false`), 중복 schoolCode 거부(선검사 + DataIntegrityViolation→400).

## 3. Out of scope

- xlsx 일괄 import(upsert) — 08c.
- `ApplicationEducation.schoolId` 링크 — 08c.
- `schoolType`/`educationMode` 의 CommonCode 백엔드 validation 결합(설계: 미결합, 코드 문자열 저장 + 프론트 표시).
- SCHOOL funnel 통계(07c 보류, 별도 후속).

## 4. 변경 파일

### Created (main)

- `domain/entity/School.java`
- `domain/repository/SchoolRepository.java`
- `exception/SchoolNotFoundException.java`, `exception/InvalidSchoolException.java`
- `dto/request/SchoolCreateRequest.java`, `dto/request/SchoolUpdateRequest.java`
- `dto/response/SchoolResponse.java`(admin 전체), `dto/response/SchoolSearchResponse.java`(public 경량)
- `service/SchoolService.java`
- `controller/SchoolSearchController.java`(public), `controller/AdminSchoolController.java`(admin)

### Modified (main)

- `exception/GlobalExceptionHandler.java` — `SchoolNotFoundException` 404, `InvalidSchoolException` 400.

### Created (test)

- `controller/SchoolControllerTest.java` (10)

## 5. 클래스별 설명

- `School` (Entity): schoolCode(불변 식별 키)/schoolName(필수)/schoolType/educationMode/region/address/countryCode/active. `update(...)` 는 서술 필드만(schoolCode 제외). schoolType/educationMode 는 코드 문자열(백엔드 validation 미결합).
- `SchoolRepository` (Repository): `search`(활성 prefix 우선+contains, Pageable top-N), `adminSearch`(비활성 포함 페이지 + 옵션 필터), `existsBySchoolCode`.
- `SchoolService` (Service): public search(q blank → 빈 목록, top-N 20), admin 페이지 목록, create(중복 schoolCode 선검사 + saveAndFlush DataIntegrityViolation→400), update(404). schoolCode 불변.
- `SchoolSearchController`(public `GET /schools`) / `AdminSchoolController`(admin 목록/생성/수정).
- `SchoolCreateRequest`(schoolCode 포함)/`SchoolUpdateRequest`(schoolCode 미포함, 불변): record + Bean Validation.
- `SchoolResponse`(admin 전체) / `SchoolSearchResponse`(public 경량: id/name/type/region).

## 6. API 목록

| Method | Path | 접근 | Purpose | Request | Response |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/schools?q=&schoolType=` | permitAll | 활성 학교 자동완성(prefix 우선+contains, top-N) | — | `ApiResponse<List<SchoolSearchResponse>>` |
| GET | `/api/admin/schools?q=&schoolType=&page=&size=` | admin | 비활성 포함 페이지 목록 | — | `ApiResponse<PageResponse<SchoolResponse>>` |
| POST | `/api/admin/schools` | admin | 생성(중복 schoolCode 400) | `SchoolCreateRequest` | `ApiResponse<SchoolResponse>` |
| POST | `/api/admin/schools/{id}` | admin | 수정(soft delete 포함, schoolCode 불변) | `SchoolUpdateRequest` | `ApiResponse<SchoolResponse>` |

- 보안: `/api/schools` permitAll(anyRequest), `/api/admin/schools` admin(`/api/admin/**`) → SecurityConfig 변경 없음.
- 수정은 admin 커맨드 컨벤션상 POST.

## 7. Entity 관계 요약

- 신규 `school` 테이블. 강한 FK 없음(`ApplicationEducation.schoolId` 는 08c 에서 application-level 참조). `school_code` unique(nullable → 다중 NULL 허용) + `school_name`/`school_type` 인덱스.
- 스키마는 `ddl-auto`(update) 자동 생성(수동 DDL 없음).

## 8. 비즈니스 규칙

- `schoolName` 필수. `schoolCode` 는 있으면 unique·생성 후 불변(수정 요청에 미포함, 와도 무시). 중복 schoolCode → 400(선검사 + 동시성 race 는 DataIntegrityViolation→400).
- public 검색은 활성만, prefix 일치 우선 정렬 후 이름 asc, top-N(20). q 가 비면 빈 목록(전건 매칭 방지).
- soft delete(`active=false`): public 검색 제외, admin 목록 포함.
- `schoolType`/`educationMode` 는 코드 문자열(표시는 프론트가 CommonCode group 으로). 백엔드 validation 미결합.

## 9. 테스트 커버리지

- `SchoolControllerTest` (10): public 검색 prefix 우선+활성만+비활성 제외, schoolType 필터, blank q 빈 목록, admin create+중복 schoolCode 400, 다중 null schoolCode 허용, blank schoolName 400, update(region/active)+soft delete 후 검색 제외+schoolCode 유지, unknown update 404, admin 목록 비활성 포함(paged totalElements), 인가(applicant 403/anonymous 401).

## 10. 테스트 결과

- 명령: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*SchoolControllerTest" --no-daemon`
- 결과: BUILD SUCCESSFUL — 10건 통과.
- 비고: 부분 실행(School). JSON body 수기 문자열 + UTF-8.

## 11. Known limitations

- (name, type, region) fallback 멱등은 import(08c)에서 적용. 08b create 는 schoolCode unique 만 강제(동명 학교 중복 생성 가능 — 관리자 책임).
- 검색 q 의 LIKE 특수문자(`%`,`_`) 미이스케이프(자동완성 영향 경미). 필요 시 후속.
- `ApplicationEducation.schoolId` 미연결(08c).

## 12. Next phase considerations

- Phase 08c - School xlsx import(upsert: schoolCode 우선 + (name,type,region) fallback) + `ApplicationEducation.schoolId` optional 링크.
