-- Applicant.gender(가입 시 NICE 본인확인 성별) 컬럼 추가 — 운영(MariaDB) 수동 DDL
--
-- 배경
--   - 지원현황 엑셀(인사팀 양식)에 성별 열이 필요하다. 가입 시 NICE 결과의 GENDER 코드(1=남성, 0=여성)를
--     MALE/FEMALE 로 바꿔 평문 저장한다(사용자 결정). 기존 가입자는 채우지 않는다(null).
--   - 신규/개발 H2(ddl-auto)에서는 엔티티 선언으로 자동 생성되므로 본 SQL은 불필요하다.
--   - Hibernate 는 @Enumerated(STRING) 컬럼을 네이티브 ENUM 으로 만든다. 같은 형태로 맞춘다.
--
-- 주의
--   - null 허용이라 기존 행에 영향이 없다. 적용 후 애플리케이션을 재기동한다.
--   - 파기(Applicant.purgePersonalData) 시 null 로 지운다.

-- MariaDB (운영 후보) / H2 (MODE=MySQL)
ALTER TABLE applicant
    ADD COLUMN gender ENUM('MALE','FEMALE') NULL;
