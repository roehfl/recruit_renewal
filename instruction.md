Major 1 — JIT 복구에서 processLdap()를 그대로 부르면 LDAP 인증을 두 번 한다

설계는 JIT save 실패 후 재조회 결과가 Employee면 processLdap(authentication, user)로 복구한다고 되어 있다.
그런데 현재 processLdap()는 내부에서 다시 ldapProvider.authenticate(authentication)를 호출한다.

즉 구현자가 설계 문구 그대로 따르면 흐름이 이렇게 된다.

LDAP 인증 성공
→ Employee save
→ unique race로 DataIntegrityViolationException
→ 재조회 성공
→ processLdap() 호출
→ LDAP 인증을 다시 수행
→ 토큰 생성

이건 복구 설계로는 부정확하다. 이미 LDAP 인증이 성공한 상태에서 DB 저장만 실패한 것이므로, 재인증하지 말고 기존 ldapUser로 Authentication을 만들어야 한다.

구현 지시문은 이렇게 고쳐라.

private Authentication buildEmployeeAuthentication(User user, CustomUserDetails ldapUser) {
    CustomUserDetails finalUser = CustomUserDetails.fromUser(user, ldapUser.getAuthorities());
    return new UsernamePasswordAuthenticationToken(finalUser, null, finalUser.getAuthorities());
}

그리고 processLdapAndJit() 안에서는 catch 후 processLdap()가 아니라 위 helper를 호출하게 해라. 테스트도 ldapProvider.authenticate()가 race 복구 경로에서 1회만 호출되는지 검증해야 한다.

Medium 1 — collation 확인 쿼리가 틀렸다

설계에 SHOW INDEX FROM users로 “기존 인덱스/제약명 충돌 확인 + collation 확인”이라고 되어 있다.
하지만 MariaDB/MySQL의 SHOW INDEX에서 Collation은 문자열 collation이 아니라 인덱스 정렬 방향 성격이다. login_id 컬럼의 case-sensitive/case-insensitive 여부를 확인하려면 아래처럼 봐야 한다.

SELECT COLUMN_NAME, COLLATION_NAME
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME = 'login_id';

또는:

SHOW FULL COLUMNS FROM users LIKE 'login_id';

DDL 사전 점검 쿼리에서 이 부분은 반드시 교체해라.

Medium 2 — DataIntegrityViolationException은 loginId race만 의미하지 않는다

현재 Employee.deptName에도 @Column(unique = true)가 걸려 있다.
따라서 JIT save 중 발생하는 DataIntegrityViolationException은 loginId race가 아니라 deptName unique 충돌일 수도 있다.

설계는 “재조회 결과가 Employee면 복구, 아니면 예외 전파”라고 되어 있어서 큰 방향은 맞다.
다만 문서의 “동시 JIT의 사용자 체감은 한 요청도 실패 없이 로그인 성공”이라는 표현은 과하다. loginId race일 때만 그렇다. deptName unique 같은 다른 제약 위반이면 실패해야 정상이다.

문구를 이렇게 낮춰라.

동시 JIT loginId race인 경우에는 양쪽 요청 모두 로그인 성공으로 복구한다.
단, loginId race가 아닌 다른 DB 제약 위반은 복구하지 않고 예외를 전파한다.
Low — history 요약 일부가 stale하다

07-implementation-history.md의 설계 범위 요약에는 아직 전화번호 변경이 단순 POST /applicant/account/phone-number로만 적혀 있고, currentPassword 재확인은 바로 아래 “리뷰 1차 반영”에만 나온다.

큰 문제는 아니지만, 히스토리 상단 요약도 이렇게 맞춰라.

④ POST /applicant/account/phone-number(currentPassword 재확인 + phoneNumber 변경)