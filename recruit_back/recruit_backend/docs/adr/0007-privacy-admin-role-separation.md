# 비가역 파기·민감 감사 권한은 ROLE_PRIVACY_ADMIN 으로 분리하고 채용운영 권한(ROLE_RECRUIT_ADMIN)을 재사용하지 않는다

Phase 09 의 비가역/민감 작업은 채용 운영 권한과 **분리**한다. **`ROLE_PRIVACY_ADMIN` 전용** = purge execute, `RetentionPolicy`/`RetentionHold` 변경(CUD), `ActivityLog` 민감필드(`ipAddress`/`userAgent`) 원문 조회, purge batch 상세/실행결과 원문 조회. **`ROLE_RECRUIT_ADMIN` 까지 허용** = retention dry-run/candidate scan, retention 결과 조회, `ActivityLog` **마스킹** 목록 조회, `RetentionPolicy` read-only. 두 권한 모두 코드 하드코딩이 아니라 기존 `DeptRoleMapping`(개인정보보호/컴플라이언스 부서 매핑)에서 파생한다.

## Status

accepted (2026-06-04, Phase 09a 착수 시점에 정책 확정으로 전환 — 구현 히스토리 기준 "ADR-0006/0007 은 9a 착수 시 accepted". SecurityConfig requestMatcher 구현은 9b/9c/9d 에서 수행. 최초 proposed: 2026-06-04, Phase 09 design / grill-with-docs)

## Considered Options

- **ROLE_RECRUIT_ADMIN 재사용** — 거부. 채용 운영자가 곧 비가역 개인정보 파기 권한을 갖게 되어 직무 분리(SoD)가 무너진다.
- **메서드 레벨 @PreAuthorize 만** — 보완책으로는 쓰되 단독으로는 부족. URL 레벨 분리가 1차 방어선이어야 한다.
- **전용 ROLE_PRIVACY_ADMIN + URL 분리(채택)** — purge execute / raw audit / retention write 를 좁은 경로로 묶어 전용 권한으로 막는다.

## Consequences

- **requestMatcher 순서가 보안 요구사항**이다. 기존 `/api/admin/** = hasAnyAuthority(ROLE_ADMIN, ROLE_RECRUIT_ADMIN)` broad matcher 가 있으므로, 좁은 PRIVACY 경로를 broad matcher 보다 **먼저** 등록해야 한다. 순서가 뒤집히면 broad matcher 에 먹혀 권한 분리가 무력화된다.
- **path 뿐 아니라 HTTP method 까지 분기**해야 한다(같은 `/api/admin/retention/policies/**` 에서 GET=RECRUIT, write=PRIVACY). 구현 지시 수준:
  ```java
  .requestMatchers(HttpMethod.POST,   "/api/admin/retention/purge-batches/execute").hasAuthority("ROLE_PRIVACY_ADMIN")
  .requestMatchers(HttpMethod.POST,   "/api/admin/retention/policies/**").hasAuthority("ROLE_PRIVACY_ADMIN")
  .requestMatchers(HttpMethod.PUT,    "/api/admin/retention/policies/**").hasAuthority("ROLE_PRIVACY_ADMIN")
  .requestMatchers(HttpMethod.DELETE, "/api/admin/retention/policies/**").hasAuthority("ROLE_PRIVACY_ADMIN")
  .requestMatchers(HttpMethod.POST,   "/api/admin/retention/holds/**").hasAuthority("ROLE_PRIVACY_ADMIN")
  .requestMatchers(HttpMethod.DELETE, "/api/admin/retention/holds/**").hasAuthority("ROLE_PRIVACY_ADMIN")
  .requestMatchers(HttpMethod.POST,   "/api/admin/retention/job-postings/*/anchor").hasAuthority("ROLE_PRIVACY_ADMIN")
  .requestMatchers(HttpMethod.GET,    "/api/admin/retention/**").hasAnyAuthority("ROLE_RECRUIT_ADMIN","ROLE_PRIVACY_ADMIN")
  .requestMatchers(HttpMethod.GET,    "/api/admin/audit/**").hasAnyAuthority("ROLE_RECRUIT_ADMIN","ROLE_PRIVACY_ADMIN")
  // ↑ 전부 기존 .requestMatchers("/api/admin/**") 보다 위
  ```
  audit 원문(ip/ua)·purge batch 원문은 GET 통과 후 컨트롤러/서비스의 권한별 projection 으로 추가 게이팅(마스킹 vs 원문).
- 운영에 `ROLE_PRIVACY_ADMIN` 을 부여하는 부서 매핑이 없으면 purge 가 아예 실행 불가다(안전한 기본값 — fail-safe).
- audit read 는 동일 데이터라도 권한에 따라 응답이 다르다(마스킹 vs 원문). 같은 엔드포인트에서 권한별 projection 분기 또는 별도 엔드포인트로 구현한다.
