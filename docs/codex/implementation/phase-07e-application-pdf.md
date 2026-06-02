# Phase 07e - Application PDF (admin)

## 1. Phase 요약

- Date: 2026-06-02
- Work type: implementation (Phase 07 다섯 번째 슬라이스, read-only, admin 전용).
- Goal: 운영자가 지원자 1명의 지원서를 PDF 1개로 출력한다. Thymeleaf XHTML → jsoup 정규화 → openhtmltopdf(PDFBox)로 렌더하고, CJK 폰트는 임베드(번들 시), 생성 시 audit를 남긴다.
- Out-of-scope: 지원자 본인 PDF(deferred), batch/zip, attachment binary embed.

## 2. 구현 범위 (Implemented)

- `GET /api/admin/applications/{applicationId}/pdf` → `application/pdf`, attachment, `no-store`/`no-cache`/`nosniff`.
- 지원서 양식 섹션 미러: 기본정보(name/phone/email/공고/분야/status/submittedAt) + 학력/경력/자격/어학/병역/수상/공백기간/질문답변. (전형결과는 설계 범위 밖이라 제외 — 리뷰2.)
- 섹션 데이터는 `AdminApplicationSectionService` 재사용(자격번호/병역 면제사유 마스킹 정책 상속).
- 템플릿 보안: applicant free-text는 `th:text`만(HTML injection 차단), 줄바꿈은 CSS `white-space: pre-wrap`, 외부 resource(font/image) 로드 금지(번들 local 폰트만).
- CJK 폰트 **번들 임베드**: `src/main/resources/fonts/`의 SIL OFL 폰트를 jar/classpath로 배포해 시스템 폰트 없이도 한글 출력. 기본 `NanumGothic-Regular.ttf`(정적), 고정 패밀리 `ApplicationPdfFont`로 등록.
- PDF 생성 SLF4J audit(actor/applicationId/jobPostingId/jobPositionId/요청 메타; PII 값 미기록).

## 3. Out of scope / Deferred

- 지원자 본인 PDF 다운로드.
- batch/zip 다중 PDF.
- attachment 파일 embed/목록(설계상 binary embed 범위 밖).
- 학력 학기별 성적은 요약 필드로 포함(상세 표는 후속 개선 여지).
- 영속 `ActivityLog` 이관.

## 4. 변경 파일

### Created (main)

- `config/PdfProperties.java`
- `exception/PdfGenerationException.java`
- `dto/response/ApplicationPdfView.java` (Header/Section/RecordRow/Field generic 표시 모델)
- `service/ApplicationPdfDocument.java`
- `service/ApplicationPdfRenderer.java`
- `service/ApplicationPdfService.java`
- `service/PdfAuditLogger.java`
- `controller/ApplicationPdfController.java`
- `src/main/resources/templates/application-pdf.html`

### Modified (main)

- `build.gradle` — `spring-boot-starter-thymeleaf`, `com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10`(PDFBox 2.0.24).
- `exception/GlobalExceptionHandler.java` — `PdfGenerationException` → 500.
- `src/main/resources/application.yaml` — `recruit.pdf.font-classpath`(기본 `fonts/NanumGothic-Regular.ttf`).

### Created (resources)

- `src/main/resources/fonts/NanumGothic-Regular.ttf`, `NotoSansKR[wght].ttf` (SIL OFL 1.1, 사용자 배치) + `fonts/README.md`(라이선스/설정 안내).

### Created (test)

- `controller/ApplicationPdfControllerTest.java` (4)

## 5. 신규 클래스 (클래스별)

- `PdfProperties` (Config): `fontClasspath`(default `fonts/NanumGothic-Regular.ttf`). 폰트는 `src/main/resources/fonts/`에 번들(jar 포함). 렌더러가 고정 패밀리 `ApplicationPdfFont`로 등록.
- `PdfGenerationException` (Exception): 렌더 실패 500.
- `ApplicationPdfView` (Response DTO): 템플릿용 generic 표시 모델. 모든 값이 `th:text`로만 렌더되도록 label/value 평탄화. `ci`/`ciHash`/`password` 미포함.
- `ApplicationPdfDocument` (Service record): PDF byte[] + fileName + jobPostingId/jobPositionId(audit용).
- `ApplicationPdfRenderer` (Service): Thymeleaf 렌더 → jsoup XML 정규화 → openhtmltopdf. 폰트 classpath 존재 시 `useFont` 임베드, 없으면 생략(경고). 외부 resource 미로드.
- `ApplicationPdfService` (Service): `findById`로 지원서 로드 + `AdminApplicationSectionService`로 섹션 집계 → 표시 모델 빌드 → 렌더. read-only tx.
- `PdfAuditLogger` (Service): 생성 audit(`recruit.audit.pdf`).
- `ApplicationPdfController` (Controller): 엔드포인트 + audit + 보안 헤더 + attachment disposition(ASCII fallback + UTF-8 filename*).

## 6. API 목록

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/applications/{applicationId}/pdf` | 지원서 PDF(admin 전용) | — | `application/pdf` (attachment) |

## 7. 렌더링 스택

- Thymeleaf 3.1.3(Apache-2.0) → jsoup(이미 의존)으로 well-formed XHTML 정규화 → openhtmltopdf 1.0.10 + PDFBox 2.0.24(Apache-2.0). iText(AGPL) 미사용.
- 표시 모델은 record. 템플릿은 record accessor를 메서드 호출(`${header.applicantName()}`)로 참조해 SpEL property-accessor 버전 차이에 비의존.

## 8. 비즈니스/보안 규칙

- admin 전용(`/api/admin/**`). 지원자/익명 차단.
- `ci`/`ciHash`/`password`는 어떤 필드/응답에도 미포함(name/phone/email 기본정보는 export/PDF의 PII surface).
- 자격번호/병역 면제사유는 admin read의 마스킹 정책을 상속(원문 미노출).
- applicant free-text는 `th:text`만(HTML injection 차단), pre-wrap 줄바꿈, 외부 resource 미로드.
- 응답 헤더 `no-store`/`no-cache`/`nosniff`, attachment disposition.

## 9. 테스트 커버리지

- `ApplicationPdfControllerTest` (4):
  - 생성(한글): 200, `application/pdf`, Content-Disposition `application-{id}.pdf`, `no-store`/`nosniff`, `%PDF-` magic bytes. PDFBox 텍스트 추출로 **한글 지원자명("홍길동") + 한글 섹션 제목("학력") 존재**(번들 폰트 임베드 검증) + `ci`/`password` 부재 단언.
  - 전형결과 제외: 추출 텍스트에 "전형결과" 부재 단언(리뷰2).
  - unknown application 404.
  - 인가: applicant 403 / anonymous 401.

## 10. 테스트 결과

- 명령: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationPdfControllerTest" --no-daemon`
- 결과: BUILD SUCCESSFUL — 4건 통과. openhtmltopdf+Thymeleaf+jsoup 파이프라인 end-to-end + **번들 NanumGothic 임베드로 한글 렌더·추출 회귀** 검증. Spring 컨텍스트 정상 기동(thymeleaf auto-config 비파괴).

## 11. Known limitations

- 변수폰트 `NotoSansKR[wght].ttf`는 PDFBox 2.x 호환성 이슈로 기본값에서 제외(정적 `NanumGothic-Regular.ttf` 사용). Noto가 필요하면 정적 인스턴스(예: `NotoSansKR-Regular.ttf`)를 배치하고 `recruit.pdf.font-classpath`로 지정.
- SIL OFL 1.1 준수: 배포 패키지에 `OFL.txt`를 폰트와 함께 포함해야 한다(`fonts/README.md` 안내).
- 학기별 성적은 요약 필드로 포함(상세 테이블 후속 여지).
- PDF byte[]를 메모리 보유(지원자 1명 단위라 안전). 대량/배치는 범위 밖.
- audit는 SLF4J 구조적 로그(영속 ActivityLog 미도입).

## 11b. 리뷰 반영 (instruction.md, 2 blocking)

- **(Blocking 1) CJK 폰트 임베드 미보장** — 폰트가 없으면 경고만 남기던 구조라 한글 출력이 보장되지 않았다. SIL OFL 폰트(`NanumGothic-Regular.ttf`, `NotoSansKR[wght].ttf`)를 `src/main/resources/fonts/`에 번들(jar/classpath 배포)하고, 기본값을 정적 `NanumGothic-Regular.ttf`로 지정, 렌더러/템플릿 폰트 패밀리를 고정 상수 `ApplicationPdfFont`로 결합. 테스트를 **한글 데이터**로 바꿔 PDFBox 추출로 한글 렌더 회귀를 고정. `fonts/README.md`로 폰트/라이선스/설정을 문서화.
- **(Blocking 2) 설계 범위 초과(전형결과 포함)** — `StageResult.comment` 등 내부 운영/평가성 정보가 "지원서 PDF"로 유출될 수 있어 `전형결과` 섹션을 **제거**(`stageResultSection` 삭제). 추출 텍스트에 "전형결과" 부재를 테스트로 고정. 필요 시 별도 admin report로 분리(정책 확정 후).

## 12. Next phase considerations

- Phase 07f: Stabilization / Test Hardening(row cap·upload 경계 회귀, PII 부재 교차 검증, PDF 폰트 배치 후 한글 렌더 검증).
- 폰트 배치 ops 문서/Dockerfile 반영, 지원자 본인 PDF(권한 분리), attachment 목록 포함 여부 결정.
