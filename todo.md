# TODO

메일·SMS 발송 기능(S1~S4) 이후 남은 일. 코드는 커밋 `49a2845`까지 반영돼 있다.
근거 문서: `docs/domains/message.md`, `docs/domains/message-delivery.md`, 설계서 `docs/archive/superpowers/specs/2026-09-19-message-send-design.md`(17절 미결).

## 1. 결정이 필요한 것

- [ ] **메일 HTML 디자인**: 실제 발송 메일은 `recruit_back/recruit_backend/src/main/resources/templates/message-mail.html`, 미리보기는 프론트 `MessagePreview.vue`가 따로 그린다. 디자인을 바꾸려면 둘 다 고쳐야 한다. 미리보기가 서버에서 만든 실제 HTML을 그대로 보여 주는 방식(미리보기 전용 API + iframe)으로 바꾸면 둘이 항상 같아진다. 메일 HTML 제약: 표 구조 + 태그에 직접 쓴 스타일만 안전, 스크립트·외부 CSS 불가, 이미지는 공개 주소 필요.

## 2. 솔루션 스펙 확인 (실제 연동 전)

- [ ] **거래 ID 유일성(가장 중요)**: 메일·SMS를 통틀어 유일하고 재사용되지 않는지. 겹치거나 재사용되면 다른 수신자의 결과가 바뀌거나 결과가 버려질 수 있다. 확인되면 `DeliveryReport`에 채널을 넣고 채널별로만 반영하도록 고친다(설계서 17절 8번).
- [ ] **성공 결과코드 목록과 코드별 설명**: 현재 `recruit.message.success-result-codes` 기본값은 `0000` 하나다. 실패 사유는 결과코드 원문을 저장·표시한다.
- [ ] **결과 단위**: 거래 1건에 수신자별 결과가 따로 오는지(현재는 거래 단위 결과를 그 거래 수신자 전원에게 적용).
- [ ] **연동 구현체 추가**: `MailGateway`·`SmsGateway` 실제 구현을 `recruit.message.gateway` 값으로 교체. 연결·응답 타임아웃 필수(테스트 발송은 요청 트랜잭션 안에서 동기 호출).
- [ ] **결과 수신 소켓 클라이언트**: 받은 메시지를 `DeliveryReport`로 바꿔 `DeliveryReportHandler.handle`만 호출하면 된다.
- [ ] 실제 SMTP를 쓰면 `spring-boot-starter-mail` 의존성 추가 승인 필요.

## 3. 운영 반영 전

- [ ] **새 테이블 DDL**: `message_template`·`message_send`·`message_recipient` 생성 SQL을 `recruit_back/recruit_backend/docs/ops/`에 둘지 결정(운영 `ddl-auto`가 `validate`/`none`이면 필요).
- [ ] **초기 템플릿**: 종류별 기본 문구를 초기 데이터로 넣을지, 관리자가 직접 입력할지.
- [ ] **발신 정보 실제 값**: 발신 이메일·표시 이름·SMS 발신번호·채용 사이트 주소(`recruit.message.*` 환경변수).
- [ ] **메뉴 등록**: `/admin/menus`에서 메시지 발송(`/admin/messages`)·발송 이력(`/admin/messages/history`)·메시지 템플릿(`/admin/messages/templates`).
- [ ] **로컬 H2**: S3 스키마로 만든 DB가 있으면 `message_send`·`message_recipient`를 지우고 다시 만들어야 한다(삭제한 컬럼·새 상태 값 때문). 이 PC에는 아직 DB 파일이 없다.

## 4. 알고 넘어간 위험 (필요할 때 개선)

- [ ] 발송 접수는 쓰기 트랜잭션 안에서 대상 재조회 + 수신자 건별 insert(최대 3,000건)라 대량일 때 응답이 느릴 수 있다.
- [ ] 테스트 발송 중 먼저 온 결과를 반영하다 DB 오류가 나면 요청 트랜잭션이 롤백되어 500이 난다(게이트웨이는 이미 호출된 뒤). 매우 드묾.
- [ ] 60분을 넘게 걸리는 대량 발송은 진행 중에도 이력에 "발송 중단"으로 보일 수 있다(지연 판정 기준이 요청 시각).
- [ ] 서버가 접수 직후 내려가면 그 발송은 `PENDING`·`REQUESTED`로 남고 자동 재개하지 않는다.
- [ ] 발송 요청에 멱등키가 없다. 화면은 응답을 못 받으면 재시도를 유도하지 않고 이력 확인을 안내한다.

## 5. 로컬 도구 (저장소 밖, git 제외)

- [ ] `recruit_front/.claude/mock/`: 백엔드 없이 관리자 메시지 화면을 보는 Vite 목업. 쓰려면 `.claude/launch.json`에 실행 설정을 다시 추가해야 한다(커밋에서 제외하려고 지웠다).
  `{ "name": "recruit-front-mock", "runtimeExecutable": "node", "runtimeArgs": ["recruit_front/node_modules/vite/bin/vite.js", "recruit_front", "--config", "recruit_front/.claude/mock/vite.mock.config.ts"], "port": 5174 }`
- [ ] `design/공고-상세.html`, `design/채용공고-리스트.html`: 2026-09-11에 만든 이번 기능과 무관한 목업(각 약 4MB, 미추적). 필요 없으면 지운다.
