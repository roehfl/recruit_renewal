# Phase 07f - Stabilization / Test Hardening

## 1. Phase 요약

- Date: 2026-06-02
- Work type: 테스트 전용 stabilization slice (운영 코드 변경 없음). Phase 07 종료.
- Goal: 설계 §16.2 Test Strategy 중 07a~07e에서 비어 있던 경계/보안 회귀를 채운다. 구체적으로 (1) upload 파일 레벨 경계(maxUploadRows/maxUploadFileSize/확장자/header)를 parser 단위로 고정, (2) Application PDF 보안 불변식(th:utext 미사용·외부 resource 차단·free-text escape·audit)을 회귀로 고정.
- 운영 코드는 변경하지 않았다(불변식이 이미 성립함을 회귀 테스트로 잠금).

## 2. 구현 범위 (Implemented)

- `StageResultUploadParserTest` (unit, 5): maxUploadRows 초과 거부, maxUploadFileSize 초과 거부, `.xls` 확장자 거부, header signature 불일치 거부, 정상 파싱.
- `ApplicationPdfSecurityHardeningTest` (@SpringBootTest, 3): 템플릿 `th:utext` 미사용 + 외부 resource(src/href/url http) 미참조 convention, applicant free-text HTML escape(injection 방어), PDF 생성 시 audit 로그 기록.

## 3. Out of scope

- 신규 운영 코드/엔티티/마이그레이션 없음.
- 전체 스위트 일괄 실행은 PC 성능 이슈로 부분 실행(미실행 사유 명시). `Infra 01` 기록의 날짜 의존 사전-실패(StageController/StageService 8건)는 별도 과제.

## 4. 변경 파일

### Created (test)

- `src/test/java/.../service/StageResultUploadParserTest.java` (5)
- `src/test/java/.../controller/ApplicationPdfSecurityHardeningTest.java` (3)

### Created (resources, 리뷰 반영)

- `src/main/resources/fonts/OFL.txt` — 번들 폰트(NanumGothic, Noto Sans KR)의 저작권/Reserved Font Name 고지 + SIL OFL 1.1 전문(배포 동봉본). 저작권 고지는 각 폰트 name table에서 확인한 값 사용.

### Modified

- `src/main/resources/fonts/README.md` — "권장" → "동봉됨"으로 정정(실제 `OFL.txt` 존재와 일치), 각 폰트 저작권 고지 명시.
- 운영 코드: 없음.

## 5. 테스트별 설명

### `StageResultUploadParserTest` (Test, unit)

- 책임: upload 파일 레벨 방어(설계 §16.2 "maxUploadRows/maxUploadFileSize 초과 거부", 확장자, header)를 Spring 컨텍스트 없이 빠르게 회귀.
- 핵심: `UploadProperties`를 작은 한도로 주입한 `StageResultUploadParser`에 POI로 만든 xlsx/임의 바이트를 `MockMultipartFile`로 넣어 `InvalidStageResultUploadException`을 단언.
- 검증: 행수 초과("최대 행 수"), 파일크기 초과("크기"), `.xls`(".xlsx"), wrong header("헤더"), 정상 2행 파싱.

### `ApplicationPdfSecurityHardeningTest` (Test, @SpringBootTest)

- 책임: 설계 §16.2 PDF 보안 항목(th:utext 미사용/외부 resource 차단/free-text escape/audit)을 회귀.
- 핵심:
  - convention: `templates/application-pdf.html`을 읽어 `th:utext` 부재 + `(src|href)=https?://`·`url(https?://` 부재 단언(xmlns 네임스페이스는 대상 아님).
  - injection: applicant name을 `<ZZINJECTZZ>`로 두고 PDF 텍스트에 마커 `ZZINJECTZZ`가 보존됨을 단언(HTML 해석 시 태그가 소거되어 사라짐 → escape 증명).
  - audit: logback `ListAppender`를 `recruit.audit.pdf` 로거에 붙여 생성 시 `eventType=APPLICATION_PDF applicationId=...` 이벤트가 기록됨을 단언.

## 6. 비즈니스/보안 규칙(회귀로 고정)

- upload는 `.xlsx`만, `maxUploadRows`/`maxUploadFileSize` 초과 시 파일 전체 거부, header signature 검증.
- PDF 템플릿은 `th:text`만 사용(utext 금지), 외부 URL resource 미로드, applicant free-text는 escape.
- PDF 생성은 audit를 남긴다(actor/applicationId/jobPostingId/jobPositionId).

## 7. 테스트 결과

- 명령: `$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationPdf*" --tests "*Upload*" --tests "*Export*" --tests "*Statistics*" --no-daemon`
- 결과: BUILD SUCCESSFUL — 07f 신규 8건(parser 5 + PDF 보안 3) + 07a~07e 회귀(export/upload/statistics/PDF) 전부 통과.
- 비고: 부분 실행(Phase 07 영역). 엔티티 직접 영속화로 클럭 의존(접수기간) 없이 안정적. 전체 스위트는 본 슬라이스 범위상 미실행.

## 8. Known limitations

- 외부 resource 차단은 템플릿 convention(정적 검사) + free-text escape 회귀로 보장한다. 런타임 네트워크 fetch 차단을 강제하는 별도 sandbox는 도입하지 않았다(openhtmltopdf는 baseUri=null + 외부 참조 없는 입력으로 운용).
- 전체 스위트 일괄 green은 날짜 의존 fixture 안정화(별도 과제) 이후 가능.

## 9. 리뷰 반영 (instruction.md, locking)

- **(Locking) 폰트 OFL.txt 누락** — 폰트 바이너리를 resources에 번들한 이상 SIL OFL 1.1은 저작권 고지 + 라이선스 전문 동봉을 요구하므로, OFL.txt는 운영 후속이 아니라 현재 커밋 포함 조건이다. 반영:
  - `src/main/resources/fonts/OFL.txt` 추가(두 폰트의 name table에서 확인한 저작권/Reserved Font Name 고지 + OFL 1.1 전문). NanumGothic=`Copyright (c) 2011 NHN Corporation`(Reserved Font Name "Nanum"/"NanumGothic"), Noto Sans KR=`Copyright (c) 2014-2021 Adobe`(Reserved Font Name 'Source').
  - `fonts/README.md`의 "권장" 표현을 "동봉됨"으로 정정(실제 파일 존재와 일치).
  - **Phase 07 종료 조건으로 폰트 라이선스 파일 포함 완료**를 명시.

## 10. Next phase considerations

- Phase 07 종료(폰트 라이선스 동봉 포함). 후속 후보: Phase 08(CommonCode/School master), 메시지 배치/발송 이력, privacy purge/retention/activity audit(영속 ActivityLog).
- 운영 준비: 영속 DB `StageResult.version` 컬럼 DDL(`docs/codex/ops/phase-07d-stage-result-version-column.sql`) 반영, 날짜 의존 테스트 fixture 안정화.
