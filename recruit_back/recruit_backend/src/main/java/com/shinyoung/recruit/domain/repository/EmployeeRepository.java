package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByLoginId(String loginId);

    List<Employee> findByLoginIdIn(Collection<String> loginIds);
}
