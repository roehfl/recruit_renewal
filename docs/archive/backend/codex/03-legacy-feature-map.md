# 03. Legacy Feature Map

이 문서는 레거시 채용 시스템에 어떤 기능이 있었는지 Spring Boot API 설계 관점에서 정리한 기능 맵이다.

원본 Excel의 `WBS(화면)`, `WBS(서버)` 탭은 제외했다. 아래 내용은 관리자/지원자/면접관 화면 목록과 legacy endpoint 정보를 기준으로 재정리했다.

## 1. 활용 원칙

- 레거시 JSP 경로와 Action/Endpoint는 신규 REST API 설계 참고 자료다.
- 신규 API는 레거시 Endpoint 이름을 그대로 복사하지 않는다.
- 레거시 기능 단위, 입력/출력 흐름, 엑셀/PDF/메시지 발송 여부를 파악하는 데 사용한다.
- 레거시 테이블명은 신규 도메인 매핑 참고용이며 신규 Entity/table 설계 기준은 `02-domain-design.md`를 따른다.

## 2. 레거시 관리자 기능 전체 요약

| 대분류 | 주요 기능 | 신규 백엔드 도메인 후보 |
| --- | --- | --- |
| 로그인 | 관리자/임직원 로그인 | `User`, `Employee`, `DeptRoleMapping`, Spring Security Session, LDAP |
| 전형설정 | 지원공고 관리, 지원서 항목 설정, 면접질문/툴팁/합격문구 관리 | `JobPosting`, `JobPosition`, `ApplicationFormConfig`, `QuestionTemplate`, `QuestionSet`, `Stage` |
| 지원현황 조회 | 지원자 검색, 지원서 작성현황, 지원서 PDF, 엑셀 다운로드, 상세 조회, 전형결과 변경, 이메일 발송, 임시 지원서 생성/삭제 | `Application`, `Applicant`, `Attachment`, `StageResult`, `MessageBatch`, `MessageSendLog` |
| 전형결과관리 | 서류/1차/최종 전형별 조회, 엑셀 다운로드, 엑셀 업로드, 저장 | `Stage`, `StageResult`, `DocumentEvaluation`, `InterviewEvaluation` |
| 면접점수관리 | 면접 점수 조회, 엑셀 업로드/다운로드 | `Interview`, `InterviewEvaluation`, `InterviewParticipant` |
| 면접 스케줄링 | 1차/최종 면접 스케줄 세팅, 면접관 세팅, 엑셀 업로드/다운로드, 이메일/SMS 발송, 인쇄 | `Interview`, `InterviewParticipant`, `MessageBatch`, `MessageSendLog`, `Attachment` |
| 추가사항 관리 | 지원자 추가 입력사항 등록/조회/엑셀 | `ApplicationFollowUpQuestion` |
| 면접평가 조회 | 면접관/면접조/지원번호/지원분야 기준 조회 | `InterviewEvaluation`, `InterviewParticipant`, `Application` |
| 지원자 통계 | 접수/서류합격/1차합격/최종합격 기준 지원율, 학교별, 전문자격/우대 통계 | `Application`, `StageResult`, `Education`, `ApplicationCertificate`, read model/query service |
| SMS/Email 발송 | 지원자 대상 메시지 발송, 미리보기, 테스트 발송, 실발송, 발송 리스트/결과 조회 | `MessageBatch`, `MessageSendLog` |
| 지원상세 항목관리 | 상세 입력 항목 조회/등록/수정/사용여부 | `ApplicationFormConfig`, `CommonCode` 또는 별도 item config |
| 사용자 권한 관리 | 관리자 관리, 면접관 관리, 삭제 | `User`, `Employee`, `DeptRoleMapping`, role 관리 |
| 테스트 계정 관리 | 테스트 계정 조회/등록/수정/삭제 | `User`, `Applicant`, `Employee`, `JobPosting` |
| 지원자 비밀번호 관리 | 지원자 비밀번호 변경/초기화 | `Applicant`, PasswordEncoder |
| 지원자 정보 파기 관리 | 과거 지원자 정보 일괄/개별 파기, 파기 후 메일 발송 | 개인정보 파기 service, `ActivityLog`, `MessageSendLog` |

## 3. 관리자 기능 상세

## 3.1 로그인

레거시:

- JSP: `/COM/mangLogin`
- Action: `Login`
- Endpoint: `/COM/login`
- 비고: Spring Security login form 사용

신규 구현 방향:

- `/auth/login`
- `/auth/logout`
- `/auth/me`
- Session 기반 인증
- 임직원 LDAP 인증 + JIT Employee 생성
- 부서별 권한 매핑

## 3.2 지원공고 관리

레거시 기능:

| 기능 | Legacy endpoint | 설명 |
| --- | --- | --- |
| 공고 목록 조회 | `/PSS/AplcAnnounceList` | 등록된 공고 목록 조회 |
| 공고 추가 화면 이동 | `/PSS/mangPSSAplcAnnouncementForm` | 화면 이동 |
| 공고 수정 화면 이동 | `/PSS/mangPSSAplcAnnouncementModyForm` | 화면 이동 |
| 공고 등록 | `/PSS/mangPSSAplcAnnouncementRegi` | form 입력 후 insert, Summernote HTML 편집기 사용 |
| 공고 수정 | `/PSS/mangPSSAplcAnnouncementMody` | form 입력 후 update, Summernote HTML 편집기 사용 |

관련 legacy table:

- `TRMMAA100M00`: 공고 master
- `TRMMAA200L00`: 공고 HTML 내용

신규 도메인/API 후보:

- `JobPosting`
- `JobPosition`
- `ApplicationFormConfig`
- `Stage`
- `QuestionSet`

API 후보:

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/admin/job-postings` | 공고 목록 |
| GET | `/admin/job-postings/{id}` | 공고 상세 |
| POST | `/admin/job-postings` | 공고 등록 |
| PUT/PATCH | `/admin/job-postings/{id}` | 공고 수정 |
| POST | `/admin/job-postings/{id}/publish` | 공고 게시 |
| POST | `/admin/job-postings/{id}/close` | 공고 마감 |

## 3.3 입사지원서 관리/전형설정

레거시 기능:

| 영역 | 기능 | Legacy endpoint | 설명 |
| --- | --- | --- | --- |
| 지원 사항 | 응시구분/지원분야/근무지 변경 | `/PSS/mangJobApplictionApntClsRegist` | 지원사항 form 입력/수정 |
| 지원서 편집 | 지원서 항목 관리 | `/PSS/mangJobApplicationQstnEditRegist` | 항목 사용 여부 관리 |
| 면접질문관리 | 면접 질문 저장 | `/PSS/mangInerviewQuestionRegist` | 면접 질문 관리 |
| 면접툴팁관리 | 면접 툴팁 저장 | `/INS/InterviewTooltipSave` | 면접 툴팁 관리 |
| 합격문구관리 | 단계별 합격 문구 저장 | `/PSS/QlcrSucsProc` | 단계별 합격 문구 관리 |

관련 legacy table:

- `TRMMAB300L00`: 지원서 질문
- `TRMMAB300D00`: 지원서 Y/N 값들
- `TRMMAB400D00`: 면접 질문 후보
- `TRMMIS800L00`: 면접 툴팁
- `TRMMIT700D00`: 합격 문구 후보

신규 도메인 후보:

- `JobPosition`
- `ApplicationFormConfig`
- `QuestionTemplate`
- `QuestionSet`
- `Stage`
- 합격 문구가 필요하면 `StageResultMessageTemplate` 같은 별도 도메인 후보

## 3.4 지원현황 조회

레거시 기능:

| 기능 | Legacy endpoint | 설명 |
| --- | --- | --- |
| 지원자 검색 | `/AIN/mangAINAplcPrssInquery` | 조건별 지원자 row 조회 |
| 입사지원서 작성현황 | `/AIN/mangAINAplcTotInquery` | 전체 통계 조회 |
| PDF 인쇄 | `지원자/generatePdf` | Puppeteer 사용 |
| 엑셀 다운로드 | `/AIN/excelTran...` | 검색 결과 엑셀 다운로드 |
| 지원서 상세 | 여러 상세 화면/action | 지원자 지원서 상세 조회 |
| 결과 변경 | legacy 결과 update action | 전형 결과 변경 |
| 이메일 발송 | legacy email action | 지원자 이메일 발송 |
| 임시 지원서 생성/삭제 | legacy temporary application action | 관리자 대행 생성/삭제 성격 |

관련 legacy table:

- `TRALAA100M00`: 지원자
- `TRALFA200L00`: 파일 정보
- `TRMMCA100L00`: 전형 결과
- `TRALSA300M00`: 지원서 지원 정보
- `TRALPA100M00`: 지원서 기본정보

신규 도메인/API 후보:

- `Application`
- `Applicant`
- `Attachment`
- `StageResult`
- `MessageBatch`
- `MessageSendLog`
- PDF query service
- Excel export service

주의:

- PDF 생성은 별도 Puppeteer 서비스와 연동될 수 있다.
- 엑셀 다운로드는 대량 데이터/개인정보 마스킹을 고려한다.

## 3.5 전형결과관리

레거시 기능은 서류전형, 1차면접, 최종면접에 대해 같은 패턴을 반복한다.

| 전형 | 조회 | 엑셀 다운로드 | 엑셀 업로드 | 저장 |
| --- | --- | --- | --- | --- |
| 서류전형 | `/PRM/mangPRMDocuPhase` | `/PRM/mangPRMDocuPhaseExcelDown` | `/PRM/mangPRMDocuPhaseExcelUp` | `/PRM/mangPRMDocuPhaseSave` |
| 1차면접 | `/PRM/mangPRMFirstInterview` | `/PRM/mangPRMFirstInterviewExcelDown` | `/PRM/mangPRMFirstInterviewExcelUp` | `/PRM/mangPRMFirstInterviewSave` |
| 최종면접 | `/PRM/mangPRMLastInterview` | `/PRM/mangPRMLastInterviewExcelDown` | `/PRM/mangPRMLastInterviewExcelUp` | `/PRM/mangPRMLastInterviewSave` |

신규 도메인 후보:

- `Stage`
- `StageResult`
- `DocumentEvaluation`
- `InterviewEvaluation`

신규 구현 방향:

- 전형별로 Controller를 복제하기보다 `stageId` 또는 `stageType` 기반 공통 API를 우선 검토한다.
- 엑셀 업로드는 validation/result preview/commit 단계를 분리하는 것이 안전하다.

API 후보:

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/admin/stages/{stageId}/results` | 전형 결과 목록 |
| PUT | `/admin/stages/{stageId}/results` | 전형 결과 저장 |
| GET | `/admin/stages/{stageId}/results/excel` | 엑셀 다운로드 |
| POST | `/admin/stages/{stageId}/results/excel` | 엑셀 업로드 |

## 3.6 면접점수관리

레거시 기능:

| 기능 | Legacy endpoint | 설명 |
| --- | --- | --- |
| 최종면접 점수 조회 | `/INS/mangINSLastInterview` | row 조회 |
| 엑셀 업로드 | `/INS/mangINSLastInterviewExcelUp` | 엑셀로 row 업데이트 |
| 엑셀 다운로드 | `/INS/mangINSLastInterviewExcelDown` | 현재 form 엑셀 다운로드 |

신규 도메인 후보:

- `InterviewEvaluation`
- `Interview`
- `InterviewParticipant`

구현 방향:

- 면접관별 개별 평가와 관리자 일괄 점수 관리 기능을 분리한다.
- 면접 점수 엑셀 업로드는 면접조/지원자/면접관 매핑 오류를 검증해야 한다.

## 3.7 면접 스케줄링

레거시 기능:

| 영역 | 기능 | Legacy endpoint | 설명 |
| --- | --- | --- | --- |
| 1차면접 스케줄링 | 조회 | `/INS/mangINSSchlFsSetting` | 스케줄 row 조회 |
| 1차면접 스케줄링 | 엑셀 업로드 | `/INS/mangINSSchlFsExcelUp` | 스케줄 row 업로드 |
| 1차면접 스케줄링 | 엑셀 다운로드 | `/INS/mangINSSchlFsExcelDown` | 현재 form 엑셀 다운로드 |
| 1차면접 스케줄링 | 면접관 세팅 | `/INS/mangINSSchlFsSetting` | 조별 면접관 등록 |
| 1차면접 스케줄링 | 등록 | `/INS/mangINSSchlEmplRegi` | 스케줄 row 등록 |
| 최종면접 스케줄링 | 조회 | `/INS/mangINSSchlLastSetting` | 스케줄 row 조회 |
| 최종면접 스케줄링 | 면접관 세팅 | `/INS/mangINSSchlLastSetting` | 조별 면접관 등록 |
| 최종면접 스케줄링 | 등록 | `/INS/mangINSSchlLastListRegi` | 스케줄 row 등록 |
| 스케줄링 조회 | 조회 | `/INS/mangINSSchlInquery` | 스케줄 row 조회 |
| 스케줄링 조회 | 엑셀 다운로드 | `/INS/mangINSSchlInqueryExcelDown` | 현재 form 엑셀 다운로드 |
| 스케줄링 조회 | 이메일 발송 | `/INS/mangINSEmailSend` | 면접 일정 이메일 발송 |
| 스케줄링 조회 | SMS 발송 | `/INS/mangINSSmsSend` | 면접 일정 SMS 발송 |
| 스케줄링 조회 | 인쇄 | `지원자/generatePdf` | 면접표/지원서 출력 가능성 |

관련 legacy table:

- `TRMMIS100L00`: 면접 정보/조
- `TRMMIS200L00`: 면접 일정
- `TRMMCA100L00`: 전형 결과
- `TRALSA300M00`: 지원정보
- `TRALAA100M00`: 지원자
- `TRALPA100M00`: 기본정보

신규 도메인 후보:

- `Interview`
- `InterviewParticipant`
- `Stage`
- `Application`
- `MessageBatch`
- `MessageSendLog`

API 후보:

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/admin/interviews` | 면접 일정/조 목록 |
| POST | `/admin/interviews` | 면접 일정/조 생성 |
| PUT | `/admin/interviews/{id}` | 면접 일정/조 수정 |
| POST | `/admin/interviews/{id}/participants` | 지원자/면접관 배정 |
| GET | `/admin/interviews/excel` | 스케줄 엑셀 다운로드 |
| POST | `/admin/interviews/excel` | 스케줄 엑셀 업로드 |
| POST | `/admin/interviews/{id}/messages` | 일정 안내 메시지 발송 |

## 3.8 추가사항 관리

레거시 기능:

| 기능 | Legacy endpoint | 설명 |
| --- | --- | --- |
| 추가사항 등록 | `/INS/mangINSAgainAppeRegi` | 지원자 추가 입력사항 저장 |
| 추가사항 조회 | `/INS/mangINSAppeInquery` | 지원자별 추가 입력사항 제출 조회 |
| 전체 엑셀다운 | `/INS/excelDown` | 전체 엑셀 다운로드 |

관련 legacy table:

- `TRMMIT600D00`
- `TRMMIT500L00`

신규 도메인 후보:

- `ApplicationFollowUpQuestion`

구현 방향:

- 질문 공개 시간과 마감 시간을 서버에서 검증한다.
- 지원자별 답변 제출 상태를 관리한다.

## 3.9 면접평가 조회

레거시 기능:

- 면접관 순서 조회
- 면접조 순서 조회
- 지원번호 순서 조회
- 지원분야 순서 조회

Legacy endpoint:

- `/INS/mangINSEvalInqueryForm`

관련 legacy table:

- `TRMMIS700L00`
- `TRALPA100M00`
- `TRALAA100M00`
- `TRMMIS600L00`
- `TRMMIS900L00`
- `TRMMIT100L00`

신규 도메인 후보:

- `InterviewEvaluation`
- `InterviewParticipant`
- `Application`
- `Applicant`
- `JobPosition`

구현 방향:

- 정렬 기준을 query parameter로 받는 공통 조회 API로 설계한다.
- 관리자 조회와 면접관 본인 평가 조회를 분리한다.

## 3.10 지원자 통계

레거시 기능:

전형 상태별로 다음 통계가 있었다.

| 상태 | 통계 종류 | Legacy endpoint |
| --- | --- | --- |
| 서류접수 | 지원율 통계, 학교별 통계, 전문자격/우대 | `/APS/mangAPSAplcStatistics` |
| 서류합격 | 지원율 통계, 학교별 통계, 전문자격/우대 | `/APS/mangAPSAplcStatisticsDocuPass` |
| 1차면접합격 | 지원율 통계, 학교별 통계, 전문자격/우대 | `/APS/mangAPSAplcFPassStatistics` |
| 최종면접합격 | 지원율 통계, 학교별 통계, 전문자격/우대 | `/APS/mangAPSAplcLPassStatistics` |

신규 도메인 후보:

- `Application`
- `StageResult`
- `Education`
- `ApplicationCertificate`
- `JobPosition`

구현 방향:

- 초기에는 JPA query/projection으로 구현한다.
- 데이터가 커지면 통계 전용 read model 또는 batch 집계를 검토한다.

## 3.11 SMS/Email 발송

레거시 기능:

| 기능 | Legacy endpoint | 설명 |
| --- | --- | --- |
| 발송 대상 검색 | `/SME/mangSMESmseSend` | 지원자 row 조회 |
| 엑셀 리스트 조회 | `/SME/mangSMESmseExcelList` | 엑셀 기반 발송 대상 조회 후보 |
| 엑셀 다운로드 | `/SME/mangSMESmseSendExcelDown` | row 엑셀 다운로드 |
| 이메일 발송 | `/SME/mangSMEEmailSend` | 미리보기 -> 테스트발송 -> 실발송 |
| SMS 발송 | `/SME/mangSMESmsSend` | 미리보기 -> 테스트발송 -> 실발송 |
| SMS 발송 리스트 | `/SME/mangSMESmseListSend` | OLTP 발송, EAI 결과수신 |
| Email 발송 리스트 | `/SME/mangSMESmseListSend` | OLTP 발송, EAI 결과수신 |

관련 legacy table:

- `TRMMSD200M00`
- `TRMMSD100L00`
- `TRMMSD100M00`
- `TRMMSD300M00`

신규 도메인 후보:

- `MessageBatch`
- `MessageSendLog`

구현 방향:

- 미리보기, 테스트발송, 실발송 단계를 분리한다.
- 실제 발송 adapter와 발송 이력 저장 service를 분리한다.
- 외부 OLTP/EAI 연동은 port/adapter 구조로 감싼다.

## 3.12 지원상세 항목관리

레거시 기능:

| 기능 | Legacy endpoint | 설명 |
| --- | --- | --- |
| 조회 | `/DIM/mangDIMAplcDtlItem` | 상세 항목 조회 |
| 신규등록 | `/DIM/mangDIMAplcDtlItemAddNew` | 항목 등록 |
| 수정 | `/DIM/mangDIMAplcDtlItemEdit` | 항목 수정 |
| 사용여부 | `/DIM/mangDIMAplcDtlItemEdit` | 사용 여부 변경 |

관련 legacy table:

- `TRMMSA100D00`
- `TRMMLA100M00`

신규 도메인 후보:

- `CommonCode`
- `ApplicationFormConfig`
- 필요 시 `ApplicationDetailItemConfig`

## 3.13 사용자 권한 관리

레거시 기능:

| 영역 | 기능 | Legacy endpoint |
| --- | --- | --- |
| 사용자 관리 | 조회 | `/AUM/mangAUMAdmin` |
| 사용자 관리 | 사용자등록 | `/AUM/mangAUMAdminAddNew` |
| 사용자 관리 | 수정 | `/AUM/mangAUMAdminEdit` |
| 사용자 관리 | 삭제 | `/AUM/mangAUMAdminDelete` |
| 면접관 관리 | 조회 | `/AUM/mangAUMInterview` |
| 면접관 관리 | 면접관등록 | `/AUM/mangAUMAdminAddNew` |
| 면접관 관리 | 수정 | `/AUM/mangAUMAdminEdit` |
| 면접관 관리 | 삭제 | `/AUM/mangAUMAdminInterviewDelete` |

관련 legacy table:

- `TRMMLA200M00`
- `TRMMIS600L00`

신규 도메인 후보:

- `User`
- `Employee`
- `DeptRoleMapping`
- role/authority 관리

구현 방향:

- 임직원은 LDAP 기반이므로 계정 자체를 새로 만드는 기능과 역할 부여 기능을 분리한다.
- 면접관은 별도 subclass보다 Employee + role로 우선 표현한다.

## 3.14 테스트 계정 관리

레거시 기능:

| 기능 | Legacy endpoint |
| --- | --- |
| 조회 | `/TUM/mangTUMTestUser` |
| 계정등록 | `/TUM/mangTUMTestUserAddNew` |
| 수정 | `/TUM/mangTUMTestUserEdit` |
| 삭제 | `/TUM/mangTUMTestUserDelete` |

관련 legacy table:

- `TRMMEA100M00`
- `TRMMAA100M00`

신규 구현 방향:

- 운영 계정과 테스트 계정 구분 필드 또는 별도 관리 정책 필요
- 보안상 production profile에서는 제한 검토

## 3.15 지원자 비밀번호 관리

레거시 기능:

| 기능 | Legacy endpoint | 설명 |
| --- | --- | --- |
| 비밀번호 저장/변경 | `/TUM/mangTUMApplyUserSave` | 이메일, 공고 key 값 받아서 password 변경 |

신규 도메인 후보:

- `Applicant`
- Password reset token 또는 관리자 초기화 이력

구현 방향:

- 단순 이메일+공고key 기반 변경은 보안 검토 필요
- 관리자 초기화와 사용자 본인 변경을 분리한다.

## 3.16 지원자 정보 파기 관리

레거시 기능:

| 영역 | 기능 | Legacy endpoint | 설명 |
| --- | --- | --- | --- |
| 일괄 파기 | 조회 | `/AHT/apexHistoryDiscardAjax` | 파기 대상 조회 |
| 일괄 파기 | 파기처리 | `/AHT/mangAHTDeleteProcess` | 일괄 삭제/파기 |
| 개별 파기 | 지원서 보기/특정인 파기 | `/AHT/mangAHTDiscardSingleNew` | 파기 후 메일 발송 |

관련 legacy table이 매우 많다.

- 지원자 기본정보
- 지원서 기본정보
- 학력/성적
- 병역/경력/어학/포상/자격증
- 파일 정보
- 전형결과
- 메시지 이력

신규 구현 방향:

- 물리 삭제, 익명화, 암호화 키 파기 중 정책을 먼저 결정한다.
- 파기 이력은 `ActivityLog` 또는 별도 `DiscardLog`가 필요하다.
- 파기 후 메일 발송은 `MessageBatch`/`MessageSendLog`와 연결한다.

## 4. 지원자 기능 전체 요약

| 대분류 | 주요 기능 | 신규 백엔드 도메인 후보 |
| --- | --- | --- |
| 회사소개 | 보상/평가, 교육제도, 복리후생, 개인정보처리방침 | 정적 페이지 또는 CMS 후보 |
| 채용안내 | 채용절차, 직무소개 | 정적 페이지 또는 CMS 후보 |
| 채용공고 | 공개채용/수시채용 공고 목록/상세 | `JobPosting` |
| 전형결과 확인 | 로그인 후 결과 확인 | `Application`, `StageResult`, `Applicant` |
| 지원자 마이페이지 | 지원자 추가사항 입력 | `ApplicationFollowUpQuestion` |
| 지원서 작성/확인 | 사진등록, 복무기간 계산, 최종제출, 임시저장, 화면 제약 | `Application` 및 세부 지원서 도메인 |
| 나의 정보 | 비밀번호 변경 | `Applicant` |
| 면접관 화면 | 면접관 스케줄 조회, 지원서 보기, 평가 페이지, 평가 작성/임시저장 | `Interview`, `InterviewParticipant`, `InterviewEvaluation` |

## 5. 지원자 기능 상세

## 5.1 회사소개/채용안내 정적 페이지

레거시 화면:

| 메뉴 | JSP | 성격 |
| --- | --- | --- |
| 보상 및 평가 | `/PRS/recuPRSCmpsValuation` | 정적 페이지 |
| 교육제도 | `/PRS/recuPRSWelfare` | 정적 페이지 |
| 복리 후생 제도 | `/PRS/recuPRSEdctSystem` | 정적 페이지 |
| 개인정보처리방침 | `/COM/recuPi` | 정적/문서 페이지 |
| 채용절차 | `/API/recuAPIRqrtProcedure` | 정적 페이지 |
| 직무소개 | `/API/recuAPIDutyIntroduction` | 직무별 modal/image |

신규 구현 방향:

- 현재 백엔드 우선순위에서는 낮다.
- 별도 CMS가 없다면 프론트 정적 페이지로 처리 가능하다.
- 개인정보처리방침 과거 이력 PDF는 파일/문서 관리가 필요할 수 있다.

## 5.2 채용공고

레거시 화면:

| 메뉴 | JSP | Action/Endpoint |
| --- | --- | --- |
| 공개채용 | `/APN/recuAPNOpblRecruit` | 공고 click -> `/APN/DtlNoticeForm` |
| 수시채용 | `/APN/recuAPNOpblRecruit` | 공고 click -> `/APN/DtlNoticeForm` |

신규 도메인/API 후보:

- `JobPosting`

API 후보:

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/job-postings` | 지원자용 공고 목록 |
| GET | `/job-postings/{id}` | 지원자용 공고 상세 |

주의:

- 지원자용 공고 목록은 `OPEN` 상태와 기간을 기준으로 필터링한다.
- 관리자용 공고 목록과 응답 필드는 다를 수 있다.

## 5.3 지원서 및 합격자 조회

레거시 기능:

| 기능 | JSP/Endpoint | 설명 |
| --- | --- | --- |
| 전형결과 확인 로그인 | `/COM/recuAPYRsltConfirm.jsp`, `/COM/RsltConfirmLogin` | 결과 확인용 로그인 |
| 지원자 마이페이지 | `/APY/recuAPYYApltMyForm` | 지원자 추가사항 입력 |
| 추가사항 저장 | `/APY/recuAPYAppeInputRegi` | 추가 입력사항 저장 |
| 지원서 작성/확인 | `/APY/recuAPYModiApplication` | 화면 로드 |
| 사진등록/수정 | 별도 action 후보 | 사진 업로드 |
| 복무기간 계산 | `/APY/recuAPYWrtnApplicationDateSetMili` | 신규에서는 클라이언트 로직 대체 가능 |
| 최종 제출 | `/APY/recuAPYWrtnApplicationFinalCommitRegist` | 최종 제출 |
| 임시 저장 | `/APY/recuAPYWrtnApplicationFinalCommitRegist` | 같은 endpoint 사용 |
| 화면 제약 | 별도 logic | 제출 상태/기간별 제약 |

신규 도메인 후보:

- `Application`
- `ApplicationFormConfig`
- `Answer`
- `Education`
- `Career`
- `ApplicationMilitary`
- `ApplicationLanguage`
- `ApplicationAward`
- `ApplicationCertificate`
- `ApplicationGap`
- `Attachment`
- `ApplicationFollowUpQuestion`

API 후보:

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/applications/me` | 내 지원서 목록/상태 |
| POST | `/applications` | 지원서 생성 |
| GET | `/applications/{id}` | 지원서 상세 |
| PUT | `/applications/{id}` | 임시저장 |
| POST | `/applications/{id}/submit` | 최종제출 |
| POST | `/applications/{id}/photo` | 사진 업로드 |
| GET | `/applications/{id}/result` | 전형 결과 확인 |

## 5.4 나의 정보

레거시 기능:

| 기능 | JSP/Endpoint |
| --- | --- |
| 비밀번호 변경 화면 | `/APY/recuAPYChagPSWD` |
| 비밀번호 변경 처리 | `/APY/recuAPYChagPSWDProcess` |

신규 구현 방향:

- `/me/password` 또는 `/auth/password` 계열 API
- 현재 password 확인 후 변경
- PasswordEncoder 사용

## 6. 면접관 기능

레거시 면접관 화면은 지원자 화면 목록에 포함되어 있다.

| 기능 | JSP/Endpoint | 설명 |
| --- | --- | --- |
| 면접관 스케줄링 조회 | `/MNG/syinterview` | 검색 |
| 수험번호 클릭 | `/MNG/ApplyPaperShow` | 지원서 보기 |
| 면접관 평가 페이지 | `/MNG/syinterviewEval` | 검색 |
| 수험번호 클릭 | modal | 지원서 + 평가 입력 화면 출력 |
| 작성 버튼 | modal | 항목별 평가 작성 |
| 비고 버튼 | `/MNG/ApplyPaperShow` | 지원서 보기 |
| 임시저장 | `/MNG/mangInsEvalUpdateList` | 평가 임시저장 |

신규 도메인 후보:

- `Interview`
- `InterviewParticipant`
- `InterviewEvaluation`
- `Application`
- `Attachment`

API 후보:

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/interviewer/interviews` | 내 면접 일정 목록 |
| GET | `/interviewer/interviews/{interviewId}/applications/{applicationId}` | 평가용 지원서 조회 |
| GET | `/interviewer/evaluations` | 내 평가 목록 |
| PUT | `/interviewer/evaluations/{id}` | 평가 임시저장/수정 |
| POST | `/interviewer/evaluations/{id}/submit` | 평가 제출 |

주의:

- 면접관은 본인이 배정된 면접/지원자만 조회 가능해야 한다.
- 관리자 평가 조회 API와 면접관 작성 API를 분리한다.

## 7. 신규 개발 우선순위 판단

레거시 기능이 많기 때문에 전체 복제식 개발은 위험하다.

우선순위는 다음 순서가 적합하다.

1. 공고 관리
2. 지원서 설정
3. 지원자 공고 조회
4. 지원서 작성/임시저장/최종제출
5. 관리자 지원현황 조회
6. 전형 단계/결과 관리
7. 면접 스케줄링
8. 면접관 평가
9. 메시지 발송 이력
10. 엑셀/PDF/통계/파기 관리

## 8. 레거시 기능 중 신규에서 대체/변경 가능한 부분

| 레거시 기능 | 신규 방향 |
| --- | --- |
| JSP 화면 이동 action | REST API에서는 제거. 프론트 라우터가 담당 |
| 복무기간 계산 Endpoint | 클라이언트 계산 + 서버 검증으로 대체 가능 |
| 전형별 중복 Controller | `Stage` 기반 공통 API로 통합 가능 |
| 면접관/면접조/지원번호/지원분야별 별도 조회 action | sort/filter parameter로 통합 가능 |
| 공고 HTML 별도 table | `JobPosting.descriptionHtml` 또는 content entity로 재설계 가능 |
| 메시지 발송 미리보기/테스트/실발송 | 명시적 상태 machine으로 분리 권장 |
| 파기 관리의 직접 삭제 | 정책 기반 익명화/파기 이력 저장 필요 |

## 9. Codex 구현 시 주의

- legacy endpoint명을 그대로 새 Controller path로 만들지 않는다.
- 신규 API는 리소스 중심으로 설계한다.
- 레거시 기능 범위가 크므로 한 PR/한 작업에서 여러 대분류를 섞지 않는다.
- 엑셀/PDF/메시지 발송은 외부 연동과 파일 처리 복잡도가 있으므로 core CRUD 이후 별도 phase로 둔다.
- 개인정보 파기는 가장 나중에 구현하되, 초기 Entity 설계부터 삭제/익명화 정책을 고려한다.
