locking — 폰트 OFL.txt 누락

7e에서 CJK 폰트를 src/main/resources/fonts/에 번들하기로 했고, 7f 문서도 운영 준비 항목으로 PDF 폰트 OFL.txt 동봉을 남겨뒀다.

문제는 이미 폰트 바이너리를 resources에 넣은 상태인데, fonts/README.md는 “배포 시 OFL 라이선스 전문(OFL.txt)을 폰트와 함께 포함해야 한다”고 적고 있다.
그런데 저장소 검색상 OFL 관련 파일은 README만 잡히고 OFL.txt는 확인되지 않는다.

이 상태로 “Phase 07 종료”라고 보기엔 찝찝하다. 폰트 파일을 번들한 순간 라이선스 전문 동봉은 운영 후속이 아니라 현재 커밋에 포함되어야 할 배포 산출물 조건이다.

수정 지시
Phase 07f 마무리 수정:

1. `src/main/resources/fonts/OFL.txt`를 추가한다.
   - NanumGothic-Regular.ttf의 원 배포 패키지에 포함된 SIL Open Font License 1.1 전문을 그대로 포함한다.
   - NotoSansKR[wght].ttf도 함께 유지할 거면 해당 폰트 배포 패키지의 라이선스/저작권 고지도 누락 없이 포함한다.
   - 두 폰트의 저작권 고지가 다르면 `NanumGothic-OFL.txt`, `NotoSansKR-OFL.txt`처럼 분리해도 된다.

2. `src/main/resources/fonts/README.md`에서 “권장” 표현을 “동봉됨”으로 바꾼다.
   - 현재 README는 동봉해야 한다고만 되어 있으므로 실제 파일 존재와 맞춘다.

3. `phase-07f-stabilization-test-hardening.md`와 implementation history에 OFL.txt 추가를 기록한다.
   - Phase 07 종료 조건으로 폰트 라이선스 파일 포함 완료를 명시한다.