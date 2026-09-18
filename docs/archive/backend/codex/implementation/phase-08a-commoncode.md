# Phase 08a - CommonCode

## 1. Phase 요약

- Date: 2026-06-02
- Work type: implementation (Phase 08 첫 슬라이스). 설계 `docs/codex/design/phase-08-commoncode-school-master-design.md` 기준.
- Goal: 관리자가 런타임에 관리하는 코드성 lookup master(`CommonCode`)를 추가형으로 도입한다. public read(드롭다운 소스) + admin CRUD. 기존 enum 은 전환하지 않는다(ADR 0003).

## 2. 구현 범위 (Implemented)

- `CommonCode` 엔티티: `groupCode`+`code`(불변)+`displayName`+`sortOrder`+`active`+`description`, `(groupCode, code)` unique, `BaseEntity` 상속.
- public read: `GET /api/codes?groupCode=` — active 만 sortOrder 순.
- admin CRUD: `GET /api/admin/codes`(비활성 포함, groupCode 선택), `POST /api/admin/codes`, `PUT /api/admin/codes/{id}`.
- code/groupCode 불변, soft delete(`active=false`), 중복(groupCode,code) 거부.

## 3. Out of scope

- 기존 enum → CommonCode 전환(0건, 카탈로그만 — 설계 §7).
- 백엔드 도메인 필드 validation 결합(workLocation 등 free-text 유지).
- CommonCode seeding(무-seed, admin CRUD 관리).
- School(08b)/import(08c).

## 4. 변경 파일

### Created (main)

- `domain/entity/CommonCode.java`
- `domain/repository/CommonCodeRepository.java`
- `exception/CommonCodeNotFoundException.java`, `exception/InvalidCommonCodeException.java`
- `dto/request/CommonCodeCreateRequest.java`, `dto/request/CommonCodeUpdateRequest.java`
- `dto/response/CommonCodeResponse.java`
- `service/CommonCodeService.java`
- `controller/CommonCodeController.java` (public read)
- `controller/AdminCommonCodeController.java` (admin CRUD)

### Modified (main)

- `exception/GlobalExceptionHandler.java` — `CommonCodeNotFoundException` 404, `InvalidCommonCodeException` 400.

- `src/main/resources/application.yaml` — `spring.jpa.hibernate.ddl-auto: ${SPRING_JPA_DDL_AUTO:update}` 명시(리뷰).

### Created (test)

- `controller/CommonCodeControllerTest.java` (7)

## 5. 클래스별 설명

- `CommonCode` (Entity): groupCode/code(불변)/displayName/sortOrder/active/description. 생성 시 필수값 trim·검증, `update(displayName, sortOrder, active, description)` 로 code/groupCode 외만 변경. soft delete 는 active=false.
- `CommonCodeRepository` (Repository): `findByGroupCodeAndActiveTrueOrderBySortOrderAscIdAsc`(public), `findByGroupCodeOrderBySortOrderAscIdAsc`/`findAllByOrderByGroupCodeAscSortOrderAscIdAsc`(admin), `existsByGroupCodeAndCode`(중복 검사).
- `CommonCodeService` (Service): public 활성 조회, admin 조회(비활성 포함), create(중복 거부), update(404). 읽기 readOnly, 변경 @Transactional.
- `CommonCodeController` (Controller): `GET /codes`(permitAll). `AdminCommonCodeController`: admin CRUD.
- `CommonCodeCreateRequest`/`CommonCodeUpdateRequest` (Request DTO): record + Bean Validation(@NotBlank/@Size). update 는 groupCode/code 미포함(불변).
- `CommonCodeResponse` (Response DTO): `from(entity)`.

## 6. API 목록

| Method | Path | 접근 | Purpose | Request | Response |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/codes?groupCode=` | permitAll | 활성 코드(sortOrder 순) | — | `ApiResponse<List<CommonCodeResponse>>` |
| GET | `/api/admin/codes?groupCode=` | admin | 비활성 포함(groupCode 선택) | — | `ApiResponse<List<CommonCodeResponse>>` |
| POST | `/api/admin/codes` | admin | 코드 생성 | `CommonCodeCreateRequest` | `ApiResponse<CommonCodeResponse>` |
| POST | `/api/admin/codes/{id}` | admin | 수정(soft delete 포함) | `CommonCodeUpdateRequest` | `ApiResponse<CommonCodeResponse>` |

- 보안: `/api/codes` 는 `anyRequest().permitAll()`, `/api/admin/**` 는 admin → SecurityConfig 변경 없음.
- 수정은 프로젝트 admin 커맨드 컨벤션에 맞춰 **POST** 사용(PUT 아님, 리뷰).
- 스키마: **수동 DDL 없음**. Hibernate `ddl-auto`(`update`)로 `common_code` 자동 생성(migration 프레임워크 부재). 운영(MariaDB)은 validate/none + 관리형 DDL 권장.

## 7. Entity 관계 요약

- 신규 `common_code` 테이블. 강한 FK 없음(application-level 참조). `(group_code, code)` unique + `group_code` 인덱스. 다른 엔티티와 연관관계 없음.

## 8. 비즈니스 규칙

- `groupCode`/`code`/`displayName` 필수. `(groupCode, code)` unique, 중복 생성 → 400. 선검사(`existsByGroupCodeAndCode`) + **동시 생성 race 는 `saveAndFlush` 의 `DataIntegrityViolationException` 을 `InvalidCommonCodeException`(400)으로 service-local 변환**(리뷰).
- `code`/`groupCode` 생성 후 불변(수정 API 에 미포함). 수정 body 에 code/groupCode 가 섞여 와도 무시(`fail-on-unknown-properties=false`)되고 기존 값 유지.
- 삭제는 soft delete(`active=false`)만. public read 는 active 만, admin read 는 비활성 포함.
- code/표시명은 비민감 라벨 → public read 허용.

## 9. 테스트 커버리지

- `CommonCodeControllerTest` (7): public read active+정렬+비활성 제외, admin create + 중복 400, blank code 400, admin update(displayName/sortOrder/active) + soft delete 후 public 제외·admin 포함, **수정 body 에 code/groupCode 가 와도 불변(무시)**, unknown update 404, 인가(applicant 403/anonymous 401).

## 10. 테스트 결과

- 명령: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*CommonCode*" --no-daemon`
- 결과: BUILD SUCCESSFUL — 7건 통과.
- 비고: 부분 실행(CommonCode). JSON body 는 프로젝트 컨벤션대로 수기 문자열(ObjectMapper 빈 미autowire), UTF-8 인코딩.

## 11. Known limitations

- seeding 무-seed(admin 이 초기값 입력). 개발 편의 seed 는 미도입.
- 중복 생성은 400(InvalidCommonCode)로 처리(409 미사용, 프로젝트 컨벤션 일치). 동시 생성 race 도 동일하게 400으로 변환.
- 스키마는 `ddl-auto`(update) 자동 생성. 운영(MariaDB)은 관리형 DDL + validate/none 전환 필요.
- 백엔드 필드 validation 미결합(설계 결정).

## 12. Next phase considerations

- Phase 08b - School(엔티티/검색·자동완성/admin CRUD).
- 08c - School xlsx import + `ApplicationEducation.schoolId` 링크.
- `schoolType`/`educationMode` 등을 CommonCode group(`SCHOOL_TYPE`)으로 운용 가능(08b 확정).
