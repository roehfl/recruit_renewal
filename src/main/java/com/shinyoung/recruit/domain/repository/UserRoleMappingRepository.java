package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.UserRoleMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleMappingRepository extends JpaRepository<UserRoleMapping, Long> {
    /** 로그인 시 개인 부여 role 조회 — loginId 완전일치(부서 매핑과 달리 부분일치가 아니다). */
    List<UserRoleMapping> findByLoginId(String loginId);

    List<UserRoleMapping> findAllByOrderByIdAsc();

    boolean existsByLoginIdAndRoleName(String loginId, String roleName);

    boolean existsByLoginIdAndRoleNameAndIdNot(String loginId, String roleName, Long id);
}
