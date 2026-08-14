package com.shinyoung.recruit.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자(loginId) 단위 role 매핑. 부서 매핑({@link DeptRoleMapping})으로 계산된 권한에
 * <b>추가 부여(합집합)</b>만 한다 — revoke 개념은 없다.
 *
 * <p>{@code loginId}는 users FK가 아니라 문자열이다. 임직원은 최초 로그인 시 JIT 생성되므로,
 * 아직 로그인한 적 없는 직원에게도 미리 권한을 걸 수 있어야 하기 때문이다.
 * {@code (loginId, roleName)} 중복은 DB 제약 없이 서비스에서 검증한다(dept_role_mapping 관례 동일).
 */
@Entity
@Getter
@NoArgsConstructor
public class UserRoleMapping extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String loginId;
    private String roleName;

    public static UserRoleMapping create(String loginId, String roleName) {
        UserRoleMapping mapping = new UserRoleMapping();
        mapping.loginId = loginId;
        mapping.roleName = roleName;
        return mapping;
    }

    public void update(String loginId, String roleName) {
        this.loginId = loginId;
        this.roleName = roleName;
    }
}
