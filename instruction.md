Low 1 — 보안 matcher 검증 테스트는 약하다

ApplicantAccountControllerTest는 MockMvcBuilders.webAppContextSetup(context).build()만 쓰고 있고 Spring Security filter chain을 붙이지 않았다.
그래서 미인증 401, 임직원 403 테스트는 실제 /api/applicant/** SecurityConfig matcher 검증이라기보다, 컨트롤러 진입 후 CurrentApplicantService의 심층 방어 검증에 가깝다.

운영 코드는 matcher가 들어가 있으므로 기능 결함은 아니다. SecurityConfig도 /api/applicant/**를 ROLE_APPLICANT로 보호하고 있다.

그래도 9b 전에 아래 중 하나는 추가하는 게 낫다.

MockMvcBuilders.webAppContextSetup(context)
    .apply(springSecurity())
    .build();

또는 DelegatingFilterProxy("springSecurityFilterChain")를 붙인 Security 전용 테스트를 별도로 둬라.

Low 2 — 히스토리 문서에 stale 문구가 남아 있다

07-implementation-history.md 상단에는 구현 완료 항목이 정상 추가됐다.
그런데 아래에 남아 있는 설계 항목 끝에는 아직 상태: 구현 미착수가 남아 있다.

실제 코드는 문제 없지만 문서가 헷갈린다. 아래처럼 고쳐라.

상태: 설계 완료. 구현은 상단 "Phase 05y - Applicant Account Hardening 구현" 항목 참조.
별도 후속 리스크 — Employee.deptName unique는 위험하다

5y 구현 결함은 아니지만, 이번 JIT race 검토 과정에서 계속 드러나는 구조적 리스크다. Employee.deptName에 unique 제약이 걸려 있다.

부서명이 같은 임직원은 당연히 여러 명 있을 수 있다. 현재 JIT는 LDAP에서 받은 deptName을 Employee에 저장하므로, 같은 부서의 다른 임직원이 최초 로그인하면 deptName unique 때문에 JIT 저장이 실패할 수 있다. 5y 구현은 이 경우를 loginId race가 아닌 제약 위반으로 전파하도록 정확히 처리했다.

이건 5y 범위 밖이지만, 인증 안정성 관점에서는 별도 fix로 제거해야 한다.

// Employee.java
// 현재
@Column(unique = true)
private String deptName;

// 권장
private String deptName;

이건 별도 작은 Fix phase로 빼는 게 맞다.