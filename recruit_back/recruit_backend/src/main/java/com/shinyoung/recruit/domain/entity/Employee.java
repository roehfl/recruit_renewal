package com.shinyoung.recruit.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@PrimaryKeyJoinColumn(name = "user_id")
public class Employee extends User {
    // deptName은 unique가 아니다 — 같은 부서 임직원은 여러 명일 수 있고, JIT 생성 시 LDAP deptName을 그대로 저장한다.
    private String deptName;
}
