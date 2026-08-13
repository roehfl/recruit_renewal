package com.shinyoung.recruit.security.auth;

import java.util.List;

/**
 * 권한(authority) 문자열의 단일 출처.
 *
 * <p>{@code dept_role_mapping.role_name} / {@code user_role_mapping.role_name}에 저장되는 값이
 * 이미 {@code ROLE_} 접두어를 포함한 완전한 authority 문자열이므로, SecurityConfig 매처는
 * {@code hasRole}이 아니라 {@code hasAuthority}/{@code hasAnyAuthority}로 이 상수를 소비한다
 * ({@code hasRole} 사용 시 {@code ROLE_ROLE_} 이중 접두어가 되므로 금지).
 */
public final class RoleNames {

    /** IT/시스템 관리자 */
    public static final String ADMIN = "ROLE_ADMIN";
    /** 채용 운영 관리자 */
    public static final String RECRUIT_ADMIN = "ROLE_RECRUIT_ADMIN";
    /** 정보보호(개인정보) 관리자 */
    public static final String PRIVACY_ADMIN = "ROLE_PRIVACY_ADMIN";
    /** 면접관 */
    public static final String INTERVIEWER = "ROLE_INTERVIEWER";
    /** 일반 임직원 */
    public static final String EMPLOYEE = "ROLE_EMPLOYEE";
    /** 일반 지원자 — 로그인 시 하드코딩 부여(CustomUserDetailsService). 매핑 화면에서 부여 불가. */
    public static final String APPLICANT = "ROLE_APPLICANT";

    /** 권한 관리 화면에서 부여 가능한 role과 표시 라벨. 지원자는 매핑 대상이 아니라 제외한다. */
    public static final List<AssignableRole> ASSIGNABLE_ROLES = List.of(
            new AssignableRole(ADMIN, "IT 관리자"),
            new AssignableRole(RECRUIT_ADMIN, "채용 운영 관리자"),
            new AssignableRole(PRIVACY_ADMIN, "정보보호 관리자"),
            new AssignableRole(INTERVIEWER, "면접관"),
            new AssignableRole(EMPLOYEE, "일반 임직원")
    );

    public record AssignableRole(String name, String label) {
    }

    public static boolean isAssignable(String roleName) {
        return ASSIGNABLE_ROLES.stream().anyMatch(role -> role.name().equals(roleName));
    }

    private RoleNames() {
    }
}
