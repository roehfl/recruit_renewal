package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

}
