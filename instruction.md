비차단 개선 이슈
3. getLayout, getPreview는 ApplicationFormConfig == null 방어가 없음

saveLayout은 ApplicationFormConfig가 없으면 명시적으로 InvalidApplicationFormLayoutException을 던진다. 그런데 getLayout, getPreview는 formConfig를 그대로 calculateEnabledSections, calculateRequiredSections에 넘긴다.

현재 데이터가 항상 ApplicationFormConfig를 가진다는 전제면 당장 터지지 않겠지만, 관리자 화면 진입 API에서 NPE/500이 날 가능성을 남기는 건 별로다.

권장 수정:

private ApplicationFormConfig requireFormConfig(JobPosting jobPosting) {
    ApplicationFormConfig formConfig = jobPosting.getApplicationFormConfig();
    if (formConfig == null) {
        throw new InvalidApplicationFormLayoutException("지원서 항목 설정이 없는 채용공고입니다.");
    }
    return formConfig;
}

그리고 getLayout, saveLayout, getPreview 모두 이 메서드를 쓰게 해라.

4. 관리자 권한 테스트가 직접적으로는 부족함

구현 문서에는 /admin/** 보안 정책으로 ROLE_ADMIN 또는 ROLE_RECRUIT_ADMIN이 필요하다고 적혀 있다.

다만 05c 컨트롤러 테스트는 기능 테스트 중심이고, 익명/지원자 권한으로 401/403이 나는지 직접 확인하는 테스트는 보이지 않는다. 전역 SecurityConfig에서 이미 검증하고 있다면 큰 문제는 아니지만, 관리자 API가 새로 추가됐으니 최소한 아래 정도는 보강하는 게 낫다.

- anonymous GET /admin/.../application-form-layout -> 401
- applicant role GET /admin/.../application-form-layout -> 403
- admin role GET /admin/.../application-form-layout -> 200