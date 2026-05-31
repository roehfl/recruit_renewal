# Application PDF는 Thymeleaf + openhtmltopdf(PDFBox)로 렌더하고 iText(AGPL)를 회피한다

신영증권 채용 백엔드는 지원자 1명의 지원서를 서버측에서 한글 PDF로 렌더해야 하고, 운영 컨테이너에는 시스템 CJK 폰트가 없으며, 사내 폐쇄소스 제품이다. 따라서 Thymeleaf로 XHTML을 만들고 openhtmltopdf(PDFBox 백엔드)로 PDF를 렌더하며, OFL 라이선스 CJK 폰트(Noto Sans KR/나눔)를 번들해 문서에 임베드한다. 이렇게 하면 다단 한글 지원서를 HTML/CSS로 유지보수할 수 있고, PDFBox(Apache-2.0) + openhtmltopdf(LGPL 계열)로 라이선스가 안전하다.

## Status

accepted (2026-05-29, Phase 07 design)

## Considered Options

- **openpdf 직접 드로잉** — 거부. 다단 지원서 레이아웃을 좌표 기반 코드로 그려야 해 유지보수 부담이 크다.
- **iText 7** — 거부. AGPL이라 사내 폐쇄소스 백엔드에 부적합(상용 라이선스 필요).
- **Thymeleaf + openhtmltopdf(PDFBox)** — 채택. HTML/CSS 레이아웃 + CJK 폰트 임베드 용이, AGPL 회피.

## Consequences

- CJK TTF를 리소스로 번들하고 `@font-face`로 임베드해야 한다(컨테이너 시스템 폰트에 의존 금지).
- openhtmltopdf의 정확한 라이선스/버전은 의존성 추가 시점에 재확인한다.
- 추후 iText로 갈아타려면 AGPL 검토가 다시 필요하므로 사실상 락인이다.
