# Phase 07e - Application PDF (admin)

## 1. Phase 요약

- Date: 2026-06-02
- Work type: implementation (Phase 07 다섯 번째 슬라이스, read-only, admin 전용).
- Goal: 운영자가 지원자 1명의 지원서를 PDF 1개로 출력한다. Thymeleaf XHTML → jsoup 정규화 → openhtmltopdf(PDFBox)로 렌더하고, CJK 폰트는 임베드(번들 시), 생성 시 audit를 남긴다.
- Out-of-scope: 지원자 본인 PDF(deferred), batch/zip, attachment binary embed.

## 2. 구현 범위 (Implemented)

- `GET /api/admin/applications/{applicationId}/pdf` → `application/pdf`, attachment, `no-store`/`no-cache`/`nosniff`.
- 지원서 양식 섹션 미러: 기본정보(name/phone/email/공고/분야/status/submittedAt) + 학력/경력/자격/어학/병역/수상/공백기간/질문답변/전형결과.
- 섹션 데이터는 `AdminApplicationSectionService` 재사용(자격번호/병역 면제사유 마스킹 정책 상속).
- 템플릿 보안: applicant free-text는 `th:text`만(HTML injection 차단), 줄바꿈은 CSS `white-space: pre-wrap`, 외부 resource(font/image) 로드 금지(번들 local 폰트만).
- CJK 폰트 임베드(설정 경로에 존재 시), 없으면 임베드 생략(경고 로그) — ASCII는 기본 폰트.
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
- `src/main/resources/application.yaml` — `recruit.pdf.font-classpath`, `recruit.pdf.font-family`.

### Created (test)

- `controller/ApplicationPdfControllerTest.java` (3)

## 5. 신규 클래스 (클래스별)

- `PdfProperties` (Config): `fontClasspath`(default `fonts/NotoSansKR-Regular.ttf`), `fontFamily`. 폰트 바이너리는 저장소 미포함, 운영/개발에서 배치.
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

- `ApplicationPdfControllerTest` (3):
  - 생성: 200, `application/pdf`, Content-Disposition `application-{id}.pdf`, `no-store`/`nosniff`, `%PDF-` magic bytes. PDFBox 텍스트 추출로 지원자명 존재 + `ci`/`password` 부재 단언(ASCII 데이터라 폰트 미임베드 환경에서도 추출 가능).
  - unknown application 404.
  - 인가: applicant 403 / anonymous 401.

## 10. 테스트 결과

- 명령: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationPdfControllerTest" --no-daemon`
- 결과: BUILD SUCCESSFUL — 3건 통과. openhtmltopdf+Thymeleaf+jsoup 파이프라인 end-to-end 검증(Spring 컨텍스트 정상 기동 = thymeleaf auto-config 비파괴).
- 비고: 부분 실행(PDF). CJK 폰트 바이너리가 없어 테스트는 ASCII 데이터로 검증(한글 렌더는 폰트 배치 후 확인 필요).

## 11. Known limitations

- **CJK 폰트 바이너리(.ttf) 미포함**: 저장소에 폰트를 넣지 않으므로, 한글 출력을 위해 운영/개발 환경에서 `recruit.pdf.font-classpath`(기본 `src/main/resources/fonts/NotoSansKR-Regular.ttf`) 위치에 SIL OFL 1.1 폰트를 배치해야 한다. 미배치 시 한글 글리프가 누락될 수 있다(경고 로그).
- 학기별 성적은 요약 필드로 포함(상세 테이블 후속 여지).
- PDF byte[]를 메모리 보유(지원자 1명 단위라 안전). 대량/배치는 범위 밖.
- audit는 SLF4J 구조적 로그(영속 ActivityLog 미도입).

## 12. Next phase considerations

- Phase 07f: Stabilization / Test Hardening(row cap·upload 경계 회귀, PII 부재 교차 검증, PDF 폰트 배치 후 한글 렌더 검증).
- 폰트 배치 ops 문서/Dockerfile 반영, 지원자 본인 PDF(권한 분리), attachment 목록 포함 여부 결정.
