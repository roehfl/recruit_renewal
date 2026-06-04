package com.shinyoung.recruit.enumeration;

/**
 * ActivityLog 행위자 유형(Phase 09a).
 *
 * <p>enum 에는 4종을 모두 두되, Phase 09 의 실제 emission 대상은 {@code EMPLOYEE}/{@code SYSTEM}/{@code ANONYMOUS}
 * 로 제한한다(applicant 자가 행위 감사는 범위 밖). {@code ADMIN}/{@code INTERVIEWER} 구분은 actorType 이 아니라
 * {@code ActivityLog.actorRoleSnapshot}(행위 시점 권한 스냅샷)으로 한다.
 */
public enum ActorType {
    EMPLOYEE,
    SYSTEM,
    APPLICANT,
    ANONYMOUS
}
