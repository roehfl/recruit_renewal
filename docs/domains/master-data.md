# 공통코드·학교·주소 (`master-data`)

> 경로 표기 `{BE}` `{BT}` `{BR}` `{FE}` — 정의: [_index.md](_index.md). API 경로는 `/api` 접두 생략.
> 관련 카드: [application](application.md)(학력 섹션이 `SchoolModalBody` 사용, 기본정보 주소 검색·코드 검증) · [job-posting](job-posting.md)(`WORK_LOCATION`) · [admin-application](admin-application.md)(PDF·엑셀 표시명) · [statistics](statistics.md)(학교별 퍼널) · [auth-account](auth-account.md)(`SecurityConfig`) · [stage-result](stage-result.md)(`UploadProperties` 공유)

## 요약

- **공통코드**: 공개 `GET /codes`(활성만) = 드롭다운 소스. 관리자 CRUD·화면 `/admin/codes`. 삭제 없음(soft delete). **시드 없음** — 관리자 화면이 유일한 등록 경로.
- **학교 검색** `GET /schools`: 외부 OpenAPI 프록시(고교=NEIS, 그 외=공공데이터포털 대학 표준데이터). 로컬 `school` 테이블은 검색에 안 쓴다. 모달 `SchoolModalBody.vue`를 지원서 학력 섹션이 연다.
- **관리자 학교** `/admin/schools`(CRUD + xlsx import): 옛 계약은 폐기(2026-08-27)라 했지만 코드 잔존·FE 호출 없음 → 🔴.
- **주소 검색** `GET /addresses`: juso.go.kr 도로명주소 프록시. 지원서 기본정보 주소 모달이 쓴다.
- 외부 API 3종은 DMZ 웹서버 경유 — 폐쇄망 주의(`## 함정·결정` 첫 항목).

## 용어

| 용어 | 뜻 |
|---|---|
| CommonCode | 관리자가 런타임에 추가·수정·비활성화하는 코드성 lookup master. 고정적이고 분기에 쓰는 값은 enum, 관리자가 목록을 늘리는 값은 CommonCode. 같은 값을 enum과 동시에 두지 않는다(중복 진실원 금지) |
| `groupCode` / `code` | 그룹 문자열(별도 그룹 테이블 없음) / 그룹 안 코드. `code`는 **생성 후 불변**(지원서·공고에 저장되는 안정 키) |
| soft delete | `active=false`. 공개 조회에서 사라지고 관리자 조회엔 남음. 하드 삭제 없음 |
| `CODE_GROUP` | 그룹 메타를 담는 자기참조 그룹(FE 관례). `code`=그룹코드, `displayName`=한글명, `description`=사용 화면 메모 |
| 검증 결합 그룹 | 백엔드가 저장 시 활성 코드인지 검사하는 그룹: `NATIONALITY`·`DISABILITY_GRADE`·`DISABILITY_TYPE`·`APPLICATION_ROUTE`(지원서), `WORK_LOCATION`(공고) |
| School(학교 master) | `school` 테이블. 설계상 자동완성·통계 기준이었으나 현재는 관리자 CRUD/import만 있고 검색·통계에 안 쓴다 |
| 지원서 `schoolName` | 지원서 학력의 자유입력 표시값(스냅샷). `School.schoolName`(master 정규명)과 같은 것으로 취급하지 않는다 |
| `schoolCode` / `schoolSource` | 학교 검색 결과 식별자와 출처. NEIS=`SD_SCHUL_CODE`, `UNIV_INFO`=학교명(학교코드 없음). 둘을 함께 해석. 직접 입력이면 둘 다 null |
| `SchoolSource` | `NEIS` · `UNIV_INFO` · `UNIV_DEPT`(학과 단위, 검색 미사용) |
| natural key | 학교 import upsert 키 `(schoolName, schoolType, region)`, null은 IS NULL 매칭 |
| 조회 범위 상한 | juso가 허용하는 `currentPage × countPerPage` 최대(기본 9000, `totalCount`와 무관). `maxPage`는 이를 반영한 마지막 페이지 |

## 파일 지도

### 백엔드

| 레이어 | 파일 | 역할 |
|---|---|---|
| controller | `{BE}/controller/CommonCodeController.java` | `GET /codes` |
| controller | `{BE}/controller/AdminCommonCodeController.java` | `/admin/codes` 조회·생성·수정 |
| controller | `{BE}/controller/AdminSchoolController.java` | `/admin/schools` 목록·생성·수정·import(🔴 FE 미사용) |
| controller | `{BE}/controller/SchoolSearchController.java` | `GET /schools` |
| controller | `{BE}/controller/AddressSearchController.java` | `GET /addresses` |
| service | `{BE}/service/CommonCodeService.java` `{BE}/service/CommonCodeNames.java` | 공통코드 조회·생성·수정 / 엑셀 export 표시명 캐시 |
| service | `{BE}/service/SchoolService.java` `{BE}/service/SchoolImportService.java` `{BE}/service/SchoolImportParser.java` | 관리자 학교 CRUD, xlsx 파싱·upsert |
| service | `{BE}/service/SchoolSearchService.java` `{BE}/service/NeisSchoolClient.java` `{BE}/service/UnivInfoSchoolClient.java` `{BE}/service/PublicDataServiceKey.java` | 학교 검색 라우팅 / NEIS·대학 API 호출 / 서비스키 정규화 |
| service | `{BE}/service/UnivDeptSchoolClient.java` | 학과 단위 데이터셋 클라이언트 — 호출처 없음 |
| service | `{BE}/service/AddressSearchService.java` `{BE}/service/JusoAddressClient.java` `{BE}/service/JusoApiResponse.java` | 주소 검증·`maxPage` / juso 호출·오류 분류 / 원본 모델 |
| entity | `{BE}/domain/entity/CommonCode.java` `{BE}/domain/entity/School.java` | `common_code`(unique `group_code, code`) / `school`(unique 없음) |
| repository | `{BE}/domain/repository/CommonCodeRepository.java` `{BE}/domain/repository/SchoolRepository.java` | `existsByGroupCodeAndCodeAndActiveTrue` / `findByNaturalKey`·`adminSearch`·`search`(미사용) |
| dto | `{BE}/dto/request/CommonCodeCreateRequest.java` `{BE}/dto/request/CommonCodeUpdateRequest.java` `{BE}/dto/response/CommonCodeResponse.java` | 공통코드 |
| dto | `{BE}/dto/request/SchoolCreateRequest.java` `{BE}/dto/request/SchoolUpdateRequest.java` `{BE}/dto/response/SchoolResponse.java` `{BE}/dto/request/SchoolImportRowRequest.java` `{BE}/dto/response/SchoolImportResponse.java` `{BE}/dto/response/SchoolImportRowError.java` | 관리자 학교·import |
| dto | `{BE}/dto/response/SchoolSearchResponse.java` `{BE}/dto/response/AddressSearchResponse.java` | 학교·주소 검색 응답 |
| enum | `{BE}/enumeration/SchoolSource.java` | 검색 결과 출처 |
| exception | `{BE}/exception/CommonCodeNotFoundException.java` `{BE}/exception/SchoolNotFoundException.java` | 404 |
| exception | `{BE}/exception/InvalidCommonCodeException.java` `{BE}/exception/InvalidSchoolException.java` `{BE}/exception/InvalidAddressSearchRequestException.java` | 400 |
| exception | `{BE}/exception/SchoolSearchException.java` `{BE}/exception/AddressSearchException.java` | 502(외부 의존 실패) |
| config | `{BE}/config/JusoProperties.java` `{BE}/config/JusoClientConfig.java` | `recruit.juso.*`, `jusoRestClient` |
| config | `{BE}/config/NeisProperties.java` `{BE}/config/UnivInfoProperties.java` `{BE}/config/UnivDeptProperties.java` `{BE}/config/SchoolOpenApiClientConfig.java` | `recruit.neis.*`·`recruit.univ-info.*`·`recruit.univ-dept.*`, RestClient 빈 3개 |
| config | `{BR}/application.yaml` | 공유 파일 — `recruit.juso`·`neis`·`univ-info`·`univ-dept` 절(키 기본값 빈 값) |
| test | `{BT}/controller/CommonCodeControllerTest.java` `{BT}/domain/repository/CommonCodeRepositoryTest.java` `{BT}/service/CommonCodeNamesTest.java` | 공통코드 API·권한, 활성 검사, 표시명 fallback |
| test | `{BT}/controller/SchoolControllerTest.java` `{BT}/controller/SchoolImportControllerTest.java` `{BT}/service/SchoolImportParserTest.java` | 관리자 학교, import |
| test | `{BT}/controller/SchoolSearchControllerTest.java` `{BT}/service/SchoolSearchServiceTest.java` `{BT}/service/UnivInfoSchoolClientTest.java` `{BT}/service/PublicDataServiceKeyTest.java` | 학교 검색 |
| test | `{BT}/service/AddressSearchServiceTest.java` `{BT}/service/JusoAddressClientTest.java` | 주소 검색 |

### 프론트

| 구분 | 파일 | 역할 |
|---|---|---|
| route | `{FE}/routes/adminRoutes.ts` | `AdminCommonCodeManage`(`/admin/codes`), 부모 `ADMIN_ROLES` 상속(공유) |
| view | `{FE}/views/admin/AdminCommonCodeManageView.vue` | 공통코드 관리 |
| component | `{FE}/views/common/SchoolModalBody.vue` | 학교 찾기 모달. props `open`·`educationLevel`·`showExtraOptions`·`initial`, expose `schoolForm` |
| api | `{FE}/api/adminCommonCodeApi.ts` `{FE}/api/commonApi.ts` `{FE}/api/application/addressApi.ts` | 관리자 코드 3종 / `getCommonCodes` / `getAddresses` |
| types | `{FE}/types/commonCode.ts` `{FE}/types/application/address.ts` | 코드 타입(`CommonCodeResponse` 미사용) / 주소 타입(**`maxPage` 누락**) |

학교 검색 호출 `educationApi.getSchools`와 타입 `schoolItem`·`schoolSource`는 [application](application.md) 소유 `{FE}/api/application/sections/educationApi.ts`·`{FE}/types/application/sections/education.ts`에 있다.

## API 계약

응답은 `ApiResponse<T>` = `{ success, data, message }`. `CommonCodeResponse` = `{ id, groupCode, code, displayName, sortOrder, active, description }`. `ADMIN·RECRUIT_ADMIN` = `/api/admin/**` broad 매처, 공개 = `anyRequest().permitAll()`(전용 매처 없음).

| 상태 | 메서드 | 경로 | 요청 요약 | 응답 요약 | 권한 |
|---|---|---|---|---|---|
| 🟢 | GET | /codes | query `groupCode`(필수) | `CommonCodeResponse[]` 활성만, `sortOrder`→`id` | 공개 |
| 🟢 | GET | /admin/codes | query `groupCode`(선택, 생략=전체) | `CommonCodeResponse[]` 비활성 포함 | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/codes | `{ groupCode*, code*, displayName*, sortOrder?, active?, description? }` | `CommonCodeResponse` | ADMIN·RECRUIT_ADMIN |
| 🟢 | POST | /admin/codes/{id} | `{ displayName*, sortOrder?, active?, description? }` | `CommonCodeResponse` | ADMIN·RECRUIT_ADMIN |
| 🔴 | GET | /admin/schools | query `q?`, `schoolType?`, `page`(0), `size`(20) | `PageResponse<SchoolResponse>` | ADMIN·RECRUIT_ADMIN |
| 🔴 | POST | /admin/schools | `{ schoolName*, schoolType?, schoolCategory?, educationMode?, region?, address?, countryCode?, active? }` | `SchoolResponse` | ADMIN·RECRUIT_ADMIN |
| 🔴 | POST | /admin/schools/{id} | 생성과 같은 모양(전체 교체) | `SchoolResponse` | ADMIN·RECRUIT_ADMIN |
| 🔴 | POST | /admin/schools/import | multipart `file`(.xlsx) | `{ totalRows, inserted, updated, skipped, errors:[{ rowNumber, reason }] }` | ADMIN·RECRUIT_ADMIN |
| 🟢 | GET | /schools | query `q?`, `educationLevel`(필수 enum) | `[{ schoolCode, schoolName, schoolSource, region }]` ≤20건 | 공개 |
| 🔴 | GET | /addresses | query `keyword`(필수), `currentPage`(1), `countPerPage`(10) | `{ totalCount, currentPage, countPerPage, maxPage, addresses:[{ roadAddr, jibunAddr, zipNo, siNm, sggNm, emdNm, bdNm, engAddr }] }` | 공개 |

### 엔드포인트 상세

**`GET /codes` — 🟢(옛 계약 섹션 없음)**: `groupCode` 누락 400 `"Invalid request."`, 공백 400 `"groupCode은(는) 필수입니다."`, trim 후 조회, 없는 그룹은 빈 배열. FE `commonCodeApi.getCommonCodes()` ↔ `CommonCodeService.getActiveCodes()`.

**관리자 공통코드 — 🟢 확정(2026-08-28), 백엔드는 Phase 08a 그대로**
- `GET /admin/codes`: 생략 시 전체를 `groupCode`→`sortOrder`→`id` 순. 화면은 그룹 목록도 여기서 파생하므로 항상 생략 형태로 호출.
- `POST /admin/codes`: `sortOrder` 미지정 0, `active` 미지정 true(화면은 그룹 내 `최대값 + 10`을 보냄). 400: 필수값 공백, 길이 초과(100/100/200/500), `groupCode`+`code` 중복.
- `POST /admin/codes/{id}`: `active=false`가 soft delete. `groupCode`/`code`는 요청에 없다(보내도 무시). 400(필수값·길이), 404(미존재). 삭제 API 없음.

**그룹 사용처(코드 기준, 화면은 각 카드 소유)**

| groupCode | 조회 FE | 백엔드 |
|---|---|---|
| `NATIONALITY` | [application](application.md) 기본정보, [admin-application](admin-application.md) 지원서 상세 | 외국인이면 필수+활성 검증, PDF·엑셀 국가명·해외 학교 소재지 |
| `DISABILITY_GRADE` · `DISABILITY_TYPE` | 기본정보, 지원서 상세 | 장애 대상이면 필수+활성 검증, PDF·엑셀 |
| `APPLICATION_ROUTE` | 기본정보 | 값 있을 때만 활성 검증, 엑셀 |
| `WORK_LOCATION` | [job-posting](job-posting.md) 공고 폼 | 활성 코드만 허용 + `displayName` 공고 스냅샷 |
| `MAJOR_TYPE` | [application](application.md) 학력 | 검증 없음, 엑셀 |
| `LANGUAGE_TYPE` · `LANGUAGE_TEST_{languageCode}`(동적) | [application](application.md) 어학 | 없음 |
| `CODE_GROUP` | 공통코드 관리 화면 | 없음 |

옛 계약의 `LANGUAGE_CONVERSATION`·`LANGUAGE_LEVEL`은 현재 코드가 조회하지 않는다.

**`GET /schools` — 🟢 확정(2026-08-27, 프론트 반영 완료)**
- 2026-08-27 School DB 검색 → 외부 OpenAPI 프록시. 옛 `schoolType`(한글 라벨) 파라미터 제거, 활성 개념 없음.
- 라우팅: `HIGH_SCHOOL`→NEIS(`SCHUL_KND_SC_NM=고등학교`), `COLLEGE`→`UNIV_SE_NM=전문대학`, `UNIVERSITY`→`대학`, `MASTER`/`DOCTOR`→`대학원`(전국대학및전문대학정보, 행 1건=학교 1곳). **출처 간 우선순위·fallback 없음** — 한 요청은 한 출처만 부르고 실패해도 로컬 DB로 대체하지 않는다. 실제 `schoolSource`는 `NEIS`·`UNIV_INFO`뿐.
- `q` 공백=빈 목록(외부 호출 없음). `educationLevel` 누락·enum 밖 400 `"Invalid request."`. 키 미설정·외부 장애·파싱 실패 502.
- 대학 API(2026-08-31 운영 실호출 검증): 요청 대문자 스네이크(`SCHL_NM`, `UNIV_SE_NM`, `pageNo`, `numOfRows`≤1000, `type=json`). **`SCHL_NM`은 완전일치** — 부분어·와일드카드는 `resultCode=03`(NODATA). 대응: 모달에 "대학은 학교명을 전체 입력해야 검색됩니다" 안내(전량 조회 후 로컬 필터링은 후속 과제). 응답은 최상위 `header`/`body`, 목록 `body.items.item`(1건이면 객체), 항목 lowerCamel(`schlNm`·`univSeNm`·`ctpvNm`). 학교코드 없음 → `schoolCode`=학교명. 대학원은 별도 행.
- NEIS(2026-08-28): `hub/schoolInfo`, `KEY`/`Type`/`pIndex`/`pSize`, `SCHUL_NM`(부분일치)/`SCHUL_KND_SC_NM`, 출력 `SD_SCHUL_CODE`/`SCHUL_NM`/`LCTN_SC_NM`.
- 학과 단위 데이터셋 클라이언트는 전공 자동완성 후속용으로 보존, 호출하지 않는다.
- 지원서 학력 식별자(🟢 2026-08-27, [application](application.md) 소유): `schoolId` → `schoolCode`(50자)+`schoolSource`. 학교별 퍼널은 `schoolCode` 그룹(`groupId` null, 직접 입력은 '기타'). 옛 `school_id` 컬럼은 운영 수동 DROP 필요(`ddl-auto: update`는 안 지움).

**`/admin/schools` 4종 — 🔴(계약 폐기 vs 코드 잔존)**: 옛 계약(2026-08-27)은 "`school` 테이블·관리자 학교 관리(xlsx import 포함) 폐기". 코드·테스트는 남아 있고 `{FE}/api`에서 호출하지 않는다. 제거(⛔)·유지는 사용자 확인 필요. 현재 동작: 목록 비활성 포함, `q` 학교명 대소문자 무시 contains(LIKE escape), `schoolType` 완전일치, 정렬 `schoolName`→`id`, `page<0`→0, `size` 1~200 보정. `PageResponse` = `{ content, page, size, totalElements, totalPages, first, last }`, `SchoolResponse` = 요청 필드 + `id`·`active`.

**`GET /addresses` — 🔴(옛 계약 🟢, 코드와 차이)**
- 차이 1: 계약은 "프론트는 반드시 `maxPage`로 페이지네이션"인데 [application](application.md) `BasicInfoSection.vue`는 `totalCount`로 계산하고 FE 타입에 `maxPage`가 없다 → 결과가 9000건을 넘으면 뒤쪽 페이지에서 400.
- 차이 2: 계약 오류표는 승인키 미설정도 `주소 검색에 실패했습니다…`라 했지만 코드는 `"주소 검색 서비스가 설정되지 않았습니다."`(502는 같음). 옛 계약의 "프론트 미반영"도 낡았다.
- 외부 호출 `GET addrLinkApi.do?confmKey&currentPage&countPerPage&keyword&resultType=json`. 승인키는 서버 설정에만, 클라이언트는 보내지 않는다. 2026-07-31: `maxPage` 추가, 범위 초과 400 선차단, 오류코드 400/502 분류.
- 보정: `currentPage`<1→1, `countPerPage` 1~`max-count-per-page`(100). `currentPage×countPerPage > max-search-range`(9000)면 외부 호출 전 400. 실측(2026-07-31, `중앙로`, totalCount 10,715): 900×10 정상 / 901×10 E0015 / 90×100 정상 / 91×100 E0015 → `countPerPage`와 무관한 offset 상한.
- 응답: `totalCount`·`currentPage`·`countPerPage`는 juso 에코값(파싱 실패 0). **`totalCount`로 페이지 수를 계산하지 않는다.** `maxPage = min(ceil(totalCount/countPerPage), floor(maxSearchRange/countPerPage))`, 결과 없음이면 0. 결과 없음은 200 + 빈 배열.

  | 상황 | 상태 | message |
  |---|---|---|
  | 검색어 누락 / 공백 | 400 | `Invalid request.` / `검색어를 입력해 주세요.` |
  | 범위 상한 초과(선차단) | 400 | `조회 가능한 검색 범위(9000건)를 초과했습니다. 검색어를 더 자세히 입력해 주세요.` |
  | juso 검색어 거부(`E0006`, `E0015`) | 400 | juso 원문(예: `주소를 상세히 입력해 주시기 바랍니다.`) |
  | 승인키 미설정 | 502 | `주소 검색 서비스가 설정되지 않았습니다.` |
  | 승인키 거부(`E0005`)·미확인 코드·네트워크·타임아웃·파싱 실패 | 502 | `주소 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.` |

- `E0006`은 행정구역명 단독 검색(`영등포구`)에서 상시 발생 — 도로명/건물명 필요. 프론트는 400 message를 그대로 노출한다. 미확인 코드를 502로 보내는 이유: 서버 문제를 사용자 탓으로 오분류해 승인키 관련 메시지가 새는 것을 막는다.
- 범위 밖: 상세주소 입력, 좌표 조회, 영문주소 표시 정책.

## 규칙·불변식

**공통코드**
- 필수값은 trim 후 검사, `description` 공백→null. ({BE}/domain/entity/CommonCode.java — requireText/normalize)
- `(groupCode, code)` 유일: 서비스 선검사 + DB unique `uk_common_code_group_code`. 동시 생성 race는 `saveAndFlush`의 `DataIntegrityViolationException`을 같은 400으로 변환. ({BE}/service/CommonCodeService.java — create)
- `groupCode`·`code` 불변. 수정 시 `sortOrder`·`active`는 null이면 유지, **`description`은 항상 덮어씀**(빠뜨리면 지워짐). ({BE}/domain/entity/CommonCode.java — update)
- 검증 결합: 지원서 4그룹은 `{BE}/service/ApplicationBasicInfoService.java`([application](application.md)), `WORK_LOCATION`은 `{BE}/service/JobPostingService.java`([job-posting](job-posting.md)). 검사 쿼리 ({BE}/domain/repository/CommonCodeRepository.java — existsByGroupCodeAndCodeAndActiveTrue)
- 표시명: 미등록·비활성 코드는 **원문 코드 그대로**(누락을 감추지 않음), export는 그룹당 1회 조회. ({BE}/service/CommonCodeNames.java — name; PDF는 `{BE}/service/ApplicationPdfService.java` codeName)
- 조회 실패(`86d12c9`): 관리자 지원서 상세는 실패를 삼키고 원문 코드 표시(`{FE}/views/admin/application/Application.vue` — loadCommonCode), 공고 폼은 오류 메시지, 지원서 섹션은 별도 처리 없음.
- **enum 마이그레이션 금지**: CommonCode는 추가만 한다. 기존 enum을 CommonCode로 바꾸지 않는다. 전환은 "관리자가 런타임에 값을 추가해야 한다"는 구체 요구가 있는 그룹만 별도 결정(ADR 0003).
- 시드 없음(투입 코드·SQL 없음). 새 환경은 관리자 화면에서 등록해야 드롭다운이 채워진다.

**학교 검색**
- 결과는 `schoolCode` 기준 중복 제거 후 상위 20건. ({BE}/service/SchoolSearchService.java — distinctByCode)
- NEIS `RESULT.CODE`: `INFO-000` 정상, `INFO-200` 빈 목록, 그 외 502. ({BE}/service/NeisSchoolClient.java — checkResultCode)
- 대학 `header.resultCode`: `00` 정상, `03` 빈 목록, 그 외 502. 행은 `schlNm`에 검색어 포함 + `univSeNm` 일치일 때만 채택, `item` 없으면 빈 목록(로그). ({BE}/service/UnivInfoSchoolClient.java — parse)
- 키 미설정이면 외부 호출 없이 502 `"학교 검색 서비스가 설정되지 않았습니다."`, 네트워크·타임아웃·파싱 실패는 502 `"학교 검색에 실패했습니다. 잠시 후 다시 시도해 주세요."`. 키·상위 오류코드는 로그에만. ({BE}/service/NeisSchoolClient.java — search, {BE}/service/UnivInfoSchoolClient.java — search)
- 공공데이터 키는 Encoding 표기로 정규화하고 URI를 직접 조립한다(URI 빌더에 넘기면 `+`가 공백 → 403). ({BE}/service/PublicDataServiceKey.java — toQueryValue, {BE}/service/UnivInfoSchoolClient.java — requestUri)

**관리자 학교·import(🔴 FE 미사용)**
- `schoolName` 필수(≤200), 나머지(`schoolType`·`schoolCategory`·`educationMode` 50, `region` 100, `address` 500, `countryCode` 10) 선택·공백→null, 백엔드 검증 미결합. 생성 시 중복 검사 없음. 수정은 서술 필드 전체 교체, `active` null이면 유지. ({BE}/domain/entity/School.java — create/update)
- import 파일 레벨(위반 시 파일 전체 400): 비어 있지 않음, 확장자 `.xlsx`, 크기 ≤ `recruit.upload.max-file-size`(5MB), 데이터 행 ≤ `recruit.upload.max-rows`(10000), 첫 시트 1행 헤더가 정확히 `schoolName, schoolType, schoolCategory, educationMode, region, address, countryCode`(7열 순서 고정). ({BE}/service/SchoolImportParser.java — parse/validateHeader)
- 셀은 문자열로 판독(정수는 정수 문자열, 날짜 ISO), 빈 행은 행 수에서 제외. ({BE}/service/SchoolImportParser.java — readCellString/isEmptyRow)
- 행 단위 적용(사유와 함께 skip): 수식 셀 행, `schoolName` 누락·길이 초과 행, natural key 매칭 2건 이상(모호). 1건 → 서술 필드 update(**`active` 보존**), 0건 → insert. `rowNumber`는 시트 행 번호(헤더=1). 한 트랜잭션이라 행 검증을 컬럼 길이와 맞춰 flush 오류 전체 롤백을 막는다. ({BE}/service/SchoolImportService.java — importSchools/findExisting)

**주소 검색**
- 검증은 외부 호출 전, 범위 검사는 `long` 곱셈(오버플로 우회 차단). ({BE}/service/AddressSearchService.java — search)
- juso `errorCode`≠`"0"`: `E0006`·`E0015`→400 + 원문, 그 외·`results.common` 없음·파싱 실패→502. ({BE}/service/JusoAddressClient.java — search)

**프론트 화면**
- 공통코드 관리 `VALIDATED_GROUPS`(경고 대상)는 백엔드 검증 결합 그룹을 미러링해야 한다 — 현재 `APPLICATION_ROUTE` 누락. 수정 모드는 그룹코드·코드 입력 비활성. (`{FE}/views/admin/AdminCommonCodeManageView.vue`)
- 학교 모달: 결과 클릭 → 이름·코드·출처 채움, 직접 입력하면 코드·출처 null(불일치 방지). 검색 실패는 경고 후 직접 입력. (`{FE}/views/common/SchoolModalBody.vue` — selectSchool/clearSelectedSchool)

## 변경 레시피

### 새 공통코드 그룹 추가
1. 값은 코드 문자열로 저장한다. enum을 만들거나 전환하지 않는다(ADR 0003).
2. 백엔드 변경 없이 `/admin/codes`에서 `CODE_GROUP` 행 → 새 그룹 코드를 등록. 배포 체크리스트에 "코드 등록"을 적는다(시드 없음).
3. 소비 화면(해당 카드)에서 `commonCodeApi.getCommonCodes('<GROUP>')`, 조회 실패 동작을 정한다.
4. 백엔드 검증이 필요하면 `existsByGroupCodeAndCodeAndActiveTrue` 검사 → `VALIDATED_GROUPS` 추가 → "그룹 사용처" 표 갱신. 엑셀·PDF 표시명은 `CommonCodeNames.name` / `ApplicationPdfService.codeName`.
5. 관련 테스트, `node tools/check-docs.mjs`.

### 공통코드 API·필드 변경
1. 엔티티 `CommonCode` → 요청·응답 DTO → `CommonCodeService`. `ddl-auto: update`는 컬럼 추가만 반영 — 제약 변경·삭제는 수동 SQL을 `recruit_back/recruit_backend/docs/ops/`에.
2. `{BT}/controller/CommonCodeControllerTest.java` 케이스 추가 → 백엔드 검증.
3. `{FE}/types/commonCode.ts` → `{FE}/api/adminCommonCodeApi.ts`·`{FE}/api/commonApi.ts` → 관리 화면, `npm run type-check`.
4. 카드 API 표(🟡 → 구현 후 🟢)·규칙 갱신, `node tools/check-docs.mjs`.

### 학교 검색 출처·매핑 변경
1. 라우팅 `SchoolSearchService.univSchoolKind`, 파라미터·항목 상수 `NeisSchoolClient`·`UnivInfoSchoolClient`, 설정 Properties + `{BR}/application.yaml`(환경변수 이름 유지). 공공데이터 API는 `PublicDataServiceKey.toQueryValue` + URI 직접 조립 유지.
2. `SchoolSource`·`schoolCode` 체계 변경은 지원서 저장값(`schoolSource` 20자, [application](application.md))과 학교별 통계([statistics](statistics.md))를 깬다 — 영향 확인.
3. 학교 검색 테스트 갱신(NEIS 클라이언트 단위 테스트는 없음) → 백엔드 검증.
4. FE `educationApi.getSchools`·`schoolItem`([application](application.md) 소유) → `{FE}/views/common/SchoolModalBody.vue` 안내 문구(`isUniversitySearch`), `npm run type-check`, 카드 갱신, `node tools/check-docs.mjs`.

### 주소 검색 오류코드·한도·페이지네이션
1. 사용자 입력 오류 juso 코드는 서버 로그 `juso 오류코드=...`로 확인 후 `{BE}/service/JusoAddressClient.java` `USER_INPUT_ERROR_CODES`에 추가(모르는 코드는 502 유지). 한도는 설정 `JUSO_MAX_SEARCH_RANGE`·`JUSO_MAX_COUNT_PER_PAGE`로.
2. `{BT}/service/JusoAddressClientTest.java`·`{BT}/service/AddressSearchServiceTest.java` 갱신 → 백엔드 검증.
3. 🔴 해소: `{FE}/types/application/address.ts`에 `maxPage` 추가 → [application](application.md) `BasicInfoSection.vue`의 `pagination.total`을 `maxPage × pageSize`로. `npm run type-check`.
4. 상태 변경은 사용자 확인 후, 카드 갱신, `node tools/check-docs.mjs`.

## 검증

백엔드(`recruit_back/recruit_backend/`에서):

```powershell
# Windows PowerShell
$env:AES_SECRET_KEY='<로컬 예시 키>'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.CommonCodeControllerTest" --tests "com.shinyoung.recruit.controller.School*" --tests "com.shinyoung.recruit.domain.repository.CommonCodeRepositoryTest" --tests "com.shinyoung.recruit.service.CommonCodeNamesTest" --tests "com.shinyoung.recruit.service.School*" --tests "com.shinyoung.recruit.service.UnivInfoSchoolClientTest" --tests "com.shinyoung.recruit.service.PublicDataServiceKeyTest" --tests "com.shinyoung.recruit.service.AddressSearchServiceTest" --tests "com.shinyoung.recruit.service.JusoAddressClientTest" --no-daemon
```

```bash
# Linux
AES_SECRET_KEY='<로컬 예시 키>' ./gradlew test --tests "com.shinyoung.recruit.controller.CommonCodeControllerTest" --tests "com.shinyoung.recruit.controller.School*" --tests "com.shinyoung.recruit.domain.repository.CommonCodeRepositoryTest" --tests "com.shinyoung.recruit.service.CommonCodeNamesTest" --tests "com.shinyoung.recruit.service.School*" --tests "com.shinyoung.recruit.service.UnivInfoSchoolClientTest" --tests "com.shinyoung.recruit.service.PublicDataServiceKeyTest" --tests "com.shinyoung.recruit.service.AddressSearchServiceTest" --tests "com.shinyoung.recruit.service.JusoAddressClientTest" --no-daemon
```

외부 API 테스트는 목·`MockRestServiceServer`라 네트워크 없이 돈다. 검증 결합 그룹을 건드렸으면 `ApplicationBasicInfoServiceTest`·`JobPostingServiceTest`, 표시명 규칙이면 `ApplicationExportRowAssemblerTest`(모두 `service` 패키지)도 돌린다.

프론트(`recruit_front/`에서, 이 도메인 vitest spec 없음):

```bash
npm run type-check
```

## 함정·결정

- **폐쇄망 주의**: 주소(juso.go.kr)·학교(NEIS·공공데이터포털 대학 OpenAPI) 검색은 외부 API 의존이라 폐쇄망에서는 동작하지 않을 수 있다. 기본 base-url은 DMZ 웹서버 경유(HTTP 80, `/juso`→도로명주소, `/neis`→NEIS, `/gov`→공공데이터포털). 키는 환경변수로만 주입하고 저장소 기본값은 빈 문자열이다(실제 키를 커밋·문서화하지 않는다).
  - 주소: `recruit.juso.base-url`(`JUSO_API_BASE_URL`), `recruit.juso.confm-key`(`JUSO_CONFM_KEY`), `JUSO_CONNECT_TIMEOUT_MS`(3000)·`JUSO_READ_TIMEOUT_MS`(5000), `JUSO_MAX_COUNT_PER_PAGE`, `JUSO_MAX_SEARCH_RANGE`.
  - 고교: `recruit.neis.base-url`(`NEIS_API_BASE_URL`), `recruit.neis.api-key`(`NEIS_API_KEY`), `NEIS_CONNECT_TIMEOUT_MS`·`NEIS_READ_TIMEOUT_MS`·`NEIS_PAGE_SIZE`.
  - 대학: `recruit.univ-info.base-url`(`UNIV_INFO_API_BASE_URL`), `recruit.univ-info.service-key`(`UNIV_INFO_API_SERVICE_KEY`), `UNIV_INFO_CONNECT_TIMEOUT_MS`·`UNIV_INFO_READ_TIMEOUT_MS`·`UNIV_INFO_PAGE_SIZE`(yaml 기본 50, 클래스 기본 100). `recruit.univ-dept.*`는 미사용.
  - 미설정: 앱은 정상 기동(키는 `@NotBlank` 아님), 호출 시 외부 요청 없이 502 + ERROR 로그. 연결 실패·타임아웃(연결 3초·읽기 5초): 502 일반 메시지.
  - 화면: 학교 모달은 경고 후 직접 입력으로 진행 가능(코드 null → 학교별 통계 '기타'). 주소는 우편번호·기본주소 칸이 `readonly`라 **주소 입력 자체가 불가**(비필수 항목) — 폐쇄망 전환 시 직접 입력 허용 여부를 결정해야 한다.
- DMZ 프록시 간헐 장애(2026-08-31): Apache `/gov`가 끊긴 유휴 연결 재사용 → 60초 무응답 → 502. 조치는 인프라 `ProxyPass ... disablereuse=On connectiontimeout=5 timeout=15`. `b98a573` HTTP 80 전환.
- `a630a15`·`cf4e471`·`df58acd` 대학 API 스키마 3연속 정정 — 명세는 실응답으로 확인.
- `940bc6c`·`f359d74` 서비스키 두 표기 허용 + URI 직접 조립("디코딩 키만" 주석은 낡음).
- `706db0d` 학교 검색 외부 API 전환. `SchoolRepository.search`·관리자 학교 API 잔존(🔴), `School` 엔티티 주석("자동완성·통계 기준")은 낡았다.
- `8d7485d` `VALIDATED_GROUPS`에 `WORK_LOCATION` 추가, `SchoolModalBody` watch `immediate`(첫 오픈 `initial` 미반영 결함). `APPLICATION_ROUTE`는 아직 누락 — 비활성화해도 경고 없이 지원서 재저장이 400.
- `86d12c9` 공통코드 조회 실패 처리(원문 코드 표시).
- 소소한 FE 결함: `CODE_GROUP` 행 비활성화 문구("그룹 목록에서만 가려진다")와 달리 비활성 그룹도 계속 보인다. 주소 오류 fallback이 `'fallback 메세지'`, `addressApi.ts` 주석은 복붙 오류.
- `displayName` 변경은 `WORK_LOCATION` 공고 스냅샷에 공고 재저장 때만 반영된다([job-posting](job-posting.md)).
- `recruit_back/recruit_backend/docs/adr/0003-commoncode-additive-no-enum-migration.md` — 추가형, 기존 enum 전환 0(`MilitaryBranch/Rank/ServiceType`·`EmploymentType`·`JobPositionApplicationType`·`DayNightType`·`CampusType`은 후보 목록일 뿐). 단 "백엔드 validation 미결합"은 현재 코드와 다르다(검증 결합 5그룹).
- `recruit_back/recruit_backend/docs/adr/0004-school-optional-application-level-link.md` — 학교 연결은 선택·FK 없음, 지원서 `schoolName` 스냅샷 유지. 현 구현은 `schoolId` 대신 외부 식별자 `schoolCode`+`schoolSource`.
