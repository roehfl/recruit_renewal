Major 보정 필요
1. loginId 대소문자/정규화 기준이 빠져 있다

현재 가입은 request.loginId().trim()만 하고, 로그인/JIT도 입력 loginId를 그대로 사용한다.

설계는 email lowercase 정규화만 후속 과제로 빼놨는데, 실제 위험은 loginId 자체의 비교 semantics다. MariaDB collation이 case-insensitive면 user01/USER01이 충돌할 수 있고, H2 테스트와 운영 DB 동작이 달라질 수 있다.

구현 전 설계에 아래 중 하나를 명시해야 한다.

loginId 정규화 정책:
- 05y에서는 loginId는 trim only로 유지한다.
- 대소문자 구분 여부는 DB collation에 의존하지 않도록 후속 phase에서 명시 결정한다.
- 테스트는 최소한 H2 unique 동작만 검증하며, 운영 MariaDB collation 차이는 DDL 적용 전 점검 항목으로 남긴다.

더 낫게 가려면 normalizeLoginId()를 만들어 Applicant signup, Auth login, LDAP JIT 저장 경로에 동일 적용해야 한다. 단, LDAP sAMAccountName 대소문자 정책을 건드릴 수 있으므로 이번 5y에서 강제 lowercase까지 가는 건 보류가 맞다.

2. LDAP JIT 동시 생성 race는 “차단”이지 “복구”가 아니다

설계는 unique 제약이 Employee JIT 중복 생성의 backstop이 된다고 쓰고 있다. 이 말 자체는 맞다. 하지만 현재 JIT 경로는 findUserByLoginId()에서 없으면 LDAP 인증 후 바로 employeeRepository.save(employee)를 한다. 동시 최초 로그인 2건이면 하나는 unique violation으로 실패할 가능성이 높다.

운영 품질까지 보려면 둘 중 하나를 설계에 박아야 한다.

선택 A - 최소안:
동시 JIT 중복 시 한 요청은 409로 실패할 수 있으며, 재시도 시 기존 Employee를 조회해 로그인 가능하다. 본 슬라이스는 영구 중복 데이터 방지만 보장한다.
선택 B - 권장:
RoutingAuthenticationProvider.processLdapAndJit()에서 Employee save 중 DataIntegrityViolationException 발생 시 UserRepository.findUserByLoginId(loginId)를 재조회하고, Employee면 processLdap(authentication, user)로 복구한다.

이건 구현 난이도 낮다. 나는 선택 B를 권장한다. 계정 hardening이면 “중복 데이터 방지”에서 끝내지 말고 “정상 로그인 복구”까지 가는 게 맞다.

3. 전화번호 변경 API는 현재 비밀번호 재확인을 붙이는 게 낫다

비밀번호 변경은 currentPassword를 요구하는데, 전화번호 변경은 phoneNumber만 받도록 되어 있다. 설계상 전화번호는 향후 메시지/본인확인/알림 채널과 연결될 가능성이 높은 개인정보다. 현재 05x도 SMS verification/rate limiting이 빠진 임시 가입 API라는 한계를 갖고 있다.

최소한 둘 중 하나로 정리해라.

권장안:
ApplicantPhoneNumberChangeRequest(currentPassword, phoneNumber)
- currentPassword 불일치 시 400
- phoneNumber trim 후 저장
최소안:
전화번호 변경은 현재 비밀번호 재확인 없이 허용한다.
단, SMS 인증/계정 복구 수단으로 phoneNumber를 사용하기 전에는 반드시 재인증 또는 변경 알림을 도입한다.

금융권/채용 시스템 성격이면 권장안이 맞다.

Medium 보정

check-email은 현재 signup 정책과 약간 어긋난다. 현재 ApplicantSignUpRequest.email은 @Email, @Size만 있고 @NotBlank가 없어 optional이다.
그런데 5y의 check-email은 @NotBlank를 요구한다. API 자체는 문제 없지만, 프론트에서 “가입 전 필수 검증”처럼 쓰면 현재 signup과 충돌한다. 설계에 “email 입력값이 있을 때만 호출하는 advisory API”라고 명확히 써라.

운영 DDL도 보강해라. 현재 설계의 DDL은 unique 추가와 중복 loginId 조회만 있다. unique 추가 전에는 아래 점검까지 같이 넣는 게 안전하다.

-- 중복 loginId
SELECT login_id, COUNT(*)
FROM users
WHERE login_id IS NOT NULL
GROUP BY login_id
HAVING COUNT(*) > 1;

-- null / blank loginId 현황
SELECT COUNT(*)
FROM users
WHERE login_id IS NULL OR TRIM(login_id) = '';

-- 기존 인덱스/제약명 충돌 확인
SHOW INDEX FROM users;

테스트 계획에는 JIT race/복구 테스트가 빠져 있다. 최소한 RoutingAuthenticationProvider 단위 테스트로 employeeRepository.save()가 DataIntegrityViolationException을 던질 때 재조회 복구 여부를 검증해라.