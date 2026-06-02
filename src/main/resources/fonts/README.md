# PDF 임베드용 CJK 폰트

Application PDF(Phase 07e)에서 한글을 출력하기 위해 CJK 폰트를 이 디렉터리에 번들한다.
폰트는 빌드 산출물(jar/classpath)에 포함되므로 컨테이너에 시스템 CJK 폰트가 없어도 한글이 정상 렌더된다.

## 번들 폰트

| 파일 | 용도 | 라이선스 |
| --- | --- | --- |
| `NanumGothic-Regular.ttf` | **기본값**(`recruit.pdf.font-classpath`). 정적 TTF라 PDFBox 2.x 임베드에 안정적. | SIL Open Font License 1.1 |
| `NotoSansKR[wght].ttf` | 대안. 변수폰트(weight 축)라 PDFBox 2.x 호환성 이슈 가능 → 기본값에서 제외. | SIL Open Font License 1.1 |

## 설정

- 사용 폰트는 `recruit.pdf.font-classpath`(기본 `fonts/NanumGothic-Regular.ttf`)로 지정한다.
- 렌더러는 이 폰트를 고정 패밀리 `ApplicationPdfFont`로 등록하고, `templates/application-pdf.html`의
  `font-family: 'ApplicationPdfFont'`가 이를 참조한다.

## 라이선스 준수

두 폰트 모두 **SIL Open Font License 1.1**이며 임베드/재배포가 허용된다. 배포 시 OFL 라이선스 전문(`OFL.txt`)을
폰트와 함께 포함해야 한다. (각 폰트 배포 패키지의 `OFL.txt`를 이 디렉터리에 함께 두는 것을 권장한다.)
