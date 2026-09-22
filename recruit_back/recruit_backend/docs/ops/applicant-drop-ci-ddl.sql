-- Applicant.ci(CI 원문, AES 암호문) 컬럼 제거 — 운영(MariaDB) 수동 DDL
-- H2(dev/test, ddl-auto)는 엔티티 선언으로 자동 생성된다. 운영·개발 서버 DB에만 적용.
-- ddl-auto=update 는 컬럼을 지우지 않으므로 엔티티에서 필드를 뺀 뒤에도 컬럼이 남는다(null 허용이라 동작에는 지장 없음).
--
-- 배경: 이 사이트코드의 NICE 계약에 CI·DI 제공이 없다(2026-09-22 실응답 확인). 가입자 중복 판정은
-- 이름+생년월일+성별의 HMAC 으로 바꿨고, 그 값은 기존 ci_hash 컬럼에 저장한다(컬럼 유지).
--
-- 적용 전: 컬럼 존재 여부와 값이 모두 비었는지 확인한다.
--   SELECT COUNT(*) FROM applicant WHERE ci IS NOT NULL;
-- 0 이 아니면 목업 시절 가입분이다(프론트가 만든 임의 UUID). 그 계정들은 ci_hash 도 옛 방식이라
-- 새 중복 판정과 맞지 않으므로 지우는 편이 낫다 — 운영 오픈 전이라 실사용자 데이터는 없다.

ALTER TABLE applicant DROP COLUMN ci;
