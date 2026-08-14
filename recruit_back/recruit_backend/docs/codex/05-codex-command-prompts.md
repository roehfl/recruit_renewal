# 05. Codex Command Prompts

이 문서는 Codex Cloud에 바로 줄 수 있는 작업 지시문 예시다. 실제 작업 범위에 맞춰 복사 후 수정해서 사용한다.

## 1. 신규 컨테이너 첫 점검 지시문

```text
AGENTS.md와 docs/codex/*.md를 먼저 읽어라.

현재 프로젝트가 Codex Cloud 신규 컨테이너에서 정상적으로 빌드/테스트 가능한지 점검해라.

전제:
- LDAP 하드코딩 값은 제거된 상태여야 한다.
- gradle-wrapper.properties에는 distributionUrl이 있어야 한다.
- src/main/resources/static 정적 파일은 제거된 상태여야 하며 복구하지 마라.
- 원본 Excel이 있더라도 WBS(화면), WBS(서버) 탭은 확인하지 마라.

작업:
1. 프로젝트 구조와 설정 파일을 확인해라.
2. 필요한 환경변수가 있으면 dummy/test 값으로 실행 가능한지 확인해라.
3. ./gradlew clean test를 실행해라.
4. 실패하면 원인을 환경 문제/기존 코드 문제/테스트 문제로 분류해라.
5. 명확한 오타나 설정 누락은 수정해라. 단, 구조를 크게 바꾸지 마라.
6. 변경 파일, 테스트 결과, 남은 이슈를 보고해라.
```

## 2. AGENTS 문서 준수 여부 점검 지시문

```text
AGENTS.md와 docs/codex/*.md를 기준으로 현재 프로젝트가 지침을 위반하는 부분이 있는지 점검해라.

특히 다음을 확인해라.
1. LDAP/DB/암호화 키 등 secret 하드코딩 여부
2. src/main/resources/static 정적 파일 복구 여부
3. Gradle Wrapper distributionUrl 존재 여부
4. CryptoConfig placeholder 정상 여부
5. SecurityConfig의 permitAll 상태와 현재 개발 단계상 위험도
6. Entity/DTO/Service/Controller 패키지 구조 일관성
7. 테스트 실행 가능 여부

수정은 명확하고 작은 범위만 진행해라. 큰 리팩터링은 하지 마라.
```

## 3. JobPosting 1차 구현 지시문

```text
AGENTS.md와 docs/codex/02-domain-design.md를 읽고 JobPosting vertical slice를 구현해라.

범위:
- JobPosting Entity
- JobPostingStatus enum
- 필요하면 JobPostingType enum
- JobPostingRepository
- 관리자용 request/response DTO
- JobPostingService
- 관리자용 JobPostingController
- Repository/Service 테스트

요구사항:
1. BaseEntity를 상속해라.
2. PK는 Long id + IDENTITY 전략을 사용해라.
3. 공고 제목, 유형, 내용, 시작일, 종료일, 상태를 포함해라.
4. 날짜 검증을 추가해라. 종료일은 시작일보다 뒤여야 한다.
5. 응답은 ApiResponse<T>로 감싸라.
6. Entity를 Controller 응답으로 직접 반환하지 마라.
7. frontend static 파일은 만들지 마라.
8. ./gradlew clean test를 실행하고 결과를 보고해라.
```

## 4. JobPosition + ApplicationFormConfig 구현 지시문

```text
AGENTS.md와 docs/codex/02-domain-design.md를 읽고 JobPosting에 연결되는 지원사항/지원서 설정 기능을 구현해라.

범위:
- JobPosition Entity/Repository/DTO/Service/API
- ApplicationFormConfig Entity/Repository/DTO/Service/API
- 테스트

요구사항:
1. JobPosition은 JobPosting N:1 관계다.
2. ApplicationFormConfig는 JobPosting 1:1 관계다.
3. ApplicationFormConfig에는 requireAward, requireEducation, requireGap, requireCareer, requireLanguage, requireCertificate, requireCareerExplain, requireGrade를 포함해라.
4. 같은 JobPosting에 config가 중복 생성되지 않게 해라.
5. API 응답은 ApiResponse<T>를 유지해라.
6. ./gradlew clean test를 실행하고 결과를 보고해라.
```

## 5. Stage 기본 구현 지시문

```text
AGENTS.md와 docs/codex/02-domain-design.md, 03-legacy-feature-map.md를 읽고 전형 단계 Stage 기본 기능을 구현해라.

범위:
- Stage Entity
- StageType enum
- StageStatus enum
- StageRepository
- Stage request/response DTO
- StageService
- 관리자용 StageController
- 테스트

요구사항:
1. Stage는 JobPosting N:1 관계다.
2. stageName, stageOrder, stageType, stageStatus, finalStage를 포함해라.
3. 같은 공고 내 stageOrder 중복을 방지해라.
4. 공고 생성 시 자동 생성까지는 이번 범위에 넣지 말고, 별도 service method 후보로만 정리해라.
5. ./gradlew clean test를 실행하고 결과를 보고해라.
```

## 6. Application 기본 구현 지시문

```text
AGENTS.md와 docs/codex/02-domain-design.md, 03-legacy-feature-map.md를 읽고 지원서 Application 기본 기능을 구현해라.

범위:
- Application Entity
- ApplicationStatus enum
- ApplicationRepository
- Application request/response DTO
- ApplicationService
- 지원자용 ApplicationController
- 테스트

요구사항:
1. Application은 Applicant N:1, JobPosting N:1 관계다.
2. 임시저장(DRAFT)과 최종제출(SUBMITTED)을 구분해라.
3. 같은 지원자가 같은 공고에 중복 지원하지 못하게 해라.
4. 공고 접수 기간과 상태를 검증해라.
5. 최종제출 후 수정 제한을 고려해라.
6. 이번 작업에서는 학력/경력/어학 등 세부 항목은 만들지 말고 Application root만 구현해라.
7. ./gradlew clean test를 실행하고 결과를 보고해라.
```

## 7. 지원서 세부 항목 구현 지시문

```text
AGENTS.md와 docs/codex/02-domain-design.md를 읽고 지원서 세부 항목 중 [대상 도메인명]을 구현해라.

대상 도메인:
- 예: Education, EducationSemesterGrade, Career, ApplicationCertificate 등

요구사항:
1. Application N:1 관계를 사용해라.
2. BaseEntity를 상속해라.
3. Entity를 응답으로 직접 반환하지 마라.
4. ApplicationFormConfig의 require flag와 서버 검증 연결 가능성을 고려해라.
5. 테스트를 추가해라.
6. ./gradlew clean test를 실행하고 결과를 보고해라.
```

## 8. 면접 기능 구현 지시문

```text
AGENTS.md와 docs/codex/02-domain-design.md, 03-legacy-feature-map.md를 읽고 면접 도메인 기본 기능을 구현해라.

범위:
- Interview
- InterviewParticipant
- InterviewParticipantRole enum
- 기본 Repository/DTO/Service/API
- 테스트

요구사항:
1. Interview는 JobPosting 또는 Stage와 연결해라.
2. InterviewParticipant는 지원자와 면접관을 모두 표현할 수 있어야 한다.
3. role은 CANDIDATE, INTERVIEWER로 구분해라.
4. 면접 평가 InterviewEvaluation은 이번 범위에 포함하지 말고 다음 단계로 분리해라.
5. ./gradlew clean test를 실행하고 결과를 보고해라.
```

## 9. 면접 평가 구현 지시문

```text
AGENTS.md와 docs/codex/02-domain-design.md, 03-legacy-feature-map.md를 읽고 InterviewEvaluation 기능을 구현해라.

범위:
- InterviewEvaluation Entity
- InterviewEvaluationRepository
- 면접관용 request/response DTO
- InterviewEvaluationService
- 면접관용 Controller
- 테스트

요구사항:
1. InterviewEvaluation은 Application, Interview, interviewer를 연결한다.
2. 같은 Interview + Application + interviewer 조합이 중복되지 않게 해라.
3. 임시저장과 제출 상태가 필요한지 검토하고, 최소한 확장 가능하게 설계해라.
4. 면접관은 본인이 배정된 지원자 평가만 수정 가능해야 한다. 현재 권한 구현이 부족하면 TODO와 검증 포인트를 남겨라.
5. ./gradlew clean test를 실행하고 결과를 보고해라.
```

## 10. 문서 업데이트 지시문

```text
현재 구현 결과를 기준으로 docs/codex 문서를 업데이트해라.

요구사항:
1. AGENTS.md의 핵심 규칙은 함부로 바꾸지 마라.
2. 구현 완료된 도메인은 01-project-context.md의 현재 구현 현황에 반영해라.
3. 설계 변경이 있었으면 02-domain-design.md에 결정 사항으로 반영해라.
4. 레거시 기능 대응 상태가 바뀌었으면 03-legacy-feature-map.md에 구현 상태를 추가해라.
5. 문서만 변경하고 코드 포맷팅은 건드리지 마라.
```
