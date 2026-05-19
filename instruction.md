Phase 03h-1 Applicant My Applications 설계를 진행한다.

목표:
- 지원자가 본인 지원서 목록을 조회하는 `GET /applications/me` API를 설계한다.
- 이번 Phase는 설계 전용이다. Java 코드/테스트/설정/DB schema는 변경하지 않는다.

설계 범위:
1. API 후보
   - GET /applications/me
   - 로그인한 applicant 본인 지원서만 조회
   - employee/admin 접근 불가
   - page/size 적용 여부 판단
   - 추천: page/size 적용

2. 응답 필드 후보
   - applicationId
   - jobPostingId
   - jobPostingTitle
   - jobPostingStatus
   - jobPositionId
   - jobPositionName
   - applicationStatus
   - createdAt
   - submittedAt
   - withdrawnAt
   - receptionStartDateTime
   - receptionEndDateTime
   - accepting
   - announcedResultCount
   - latestAnnouncedStageName
   - latestResultStatus 후보

3. 결과 요약 정책
   - StageResult를 목록 응답에 포함할지 판단
   - 추천:
     - 상세 결과는 `GET /applications/{applicationId}/stage-results` 유지
     - `/applications/me`에는 발표된 결과 존재 여부와 최신 발표 stage/result 정도만 요약
   - score/comment/decidedBy/correctedBy/history 노출 금지

4. 조회 정책
   - DRAFT, SUBMITTED, WITHDRAWN 모두 목록에 포함
   - 정렬 기본값: createdAt DESC, id DESC
   - 공고가 CLOSED여도 기존 지원서는 목록에 표시
   - 결과 발표 여부는 `Stage.status == RESULT_ANNOUNCED || CLOSED` 기준

5. Repository/Service 설계
   - JobApplicationRepository에 applicantId 기준 목록 조회 후보
   - StageResult 요약 조회 방식 후보
     - application 목록 조회 후 result summary batch 조회
     - 처음에는 N+1 방지 가능한 batch query 추천
   - Service 후보:
     - `ApplicationMyPageService`
     - 또는 기존 `JobApplicationService` 확장
   - 추천:
     - 기존 `JobApplicationService` 확장

6. 테스트 전략
   - 본인 지원서 목록 조회 성공
   - 타인 지원서 미포함
   - DRAFT/SUBMITTED/WITHDRAWN 포함
   - createdAt DESC, id DESC 정렬
   - accepting 계산 검증
   - 발표된 결과 요약 포함
   - 미발표 StageResult는 요약 제외
   - score/comment/decidedBy/correction history 미노출
   - employee/admin 접근 차단은 기존 03e-3 정책에 맞춰 controller test 후보로 정리

7. 구현 Phase 분리
   - Phase 03h-1: 설계 문서만 작성
   - Phase 03h-2: `GET /applications/me` 기본 목록 구현
   - Phase 03h-3 후보: 지원서 dashboard summary, 작성완료율/부족항목 안내

문서 작업:
- 새 설계 문서 생성:
  - docs/codex/design/phase-03h-applicant-my-applications-design.md
- 사람용 HTML 리포트 생성:
  - docs/codex/reports/phase-03h-applicant-my-applications-design.html
- 기존 문서 갱신:
  - docs/codex/design/phase-03-application-design.md
  - docs/codex/07-implementation-history.md

HTML 리포트 요구사항:
- 외부 CDN/JS/CSS 없이 self-contained HTML로 작성한다.
- API 후보, 응답 필드, 결과 요약 정책, 테스트 전략, 구현 Phase 분리를 표로 정리한다.

금지:
- Java source 변경 금지
- test source 변경 금지
- SecurityConfig 변경 금지
- build.gradle/settings.gradle 변경 금지
- application.yml 변경 금지
- DB schema 변경 금지
- API 구현 금지
- Repository/Service/Controller/DTO 생성 금지
- StageResult 조회 API 변경 금지
- applicant result response 변경 금지
- admin API 변경 금지

완료 후 보고:
- 변경 파일 목록
- API 설계 결론
- 응답 필드 결론
- 결과 요약 정책
- Phase 03h-2 구현 권장 범위
- 테스트 실행 여부: 문서 작업이므로 실행하지 않았다고 명시