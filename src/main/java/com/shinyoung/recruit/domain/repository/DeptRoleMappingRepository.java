package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.DeptRoleMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeptRoleMappingRepository extends JpaRepository<DeptRoleMapping, Long> {
    List<DeptRoleMapping> findDeptRoleMappingsByDeptName(String deptName);
}
