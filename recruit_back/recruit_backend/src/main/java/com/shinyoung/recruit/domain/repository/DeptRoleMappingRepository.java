package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.DeptRoleMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeptRoleMappingRepository extends JpaRepository<DeptRoleMapping, Long> {
    List<DeptRoleMapping> findDeptRoleMappingsByDeptName(String deptName);

    List<DeptRoleMapping> findAllByOrderByIdAsc();

    boolean existsByDeptNameAndRoleName(String deptName, String roleName);

    boolean existsByDeptNameAndRoleNameAndIdNot(String deptName, String roleName, Long id);

    /**
     * 부서 그룹명 안에 매핑 부서명이 <b>포함</b>되는 행을 찾는다.
     *
     * <p>AD가 부서를 사용자 속성이 아니라 {@code ou=부서} 아래 그룹으로 관리하고, 그룹 {@code cn}이
     * {@code 내부채널_부서_6315}처럼 부서명 뒤에 코드가 덧붙는 형태라 완전일치로는 찾을 수 없다.
     * 그래서 {@code dept_role_mapping.dept_name}("내부채널")이 그룹명의 부분문자열인지로 판정한다.
     *
     * <p><b>주의</b>: 부분일치라 짧은 부서명이 다른 부서 그룹에 걸릴 수 있다.
     * 예를 들어 {@code dept_name}에 "채널"을 등록하면 "내부채널_부서_6315"에도 매칭된다.
     * 등록하는 부서명은 다른 부서 그룹명의 부분문자열이 되지 않도록 충분히 구체적으로 넣어야 한다.
     *
     * <p>빈 {@code dept_name}은 모든 그룹에 매칭되므로 제외한다.
     */
    @Query("select m from DeptRoleMapping m "
            + "where length(m.deptName) > 0 and :groupName like concat('%', m.deptName, '%')")
    List<DeptRoleMapping> findByDeptNameContainedIn(@Param("groupName") String groupName);
}
