package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.common.crypto.CryptoHolder;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.CryptoConfig;
import com.shinyoung.recruit.config.JpaConfig;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({CryptoConfig.class, JpaConfig.class, CryptoHolder.class})
public class UserRepositoryTest {

    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ApplicantRepository applicantRepository;

    @Test
    void 조회_타입검증() {
        Employee employee = new Employee();
        employee.setLoginId("22791");
        employee.setDeptName("IT센터");
        employee.setName("이솔");
        employee = employeeRepository.save(employee);

        String ciValue = "testCIValue";
        Applicant applicant = new Applicant(ciValue, HashUtil.sha256(ciValue));
        applicant.setEmail("roehfl@gmail.com");
        applicant.setUserName("이솔");
        applicant.setPhoneNumber("01071939211");

        applicant = applicantRepository.save(applicant);

        List<User> users = userRepository.findAll();
        User applicantUser = userRepository.findById(applicant.getId()).get();
        User employeeUser = userRepository.findById(employee.getId()).get();

        assertThat(users).hasSize(2);
        assertThat(applicantUser).isInstanceOf(Applicant.class);
        assertThat(employeeUser).isInstanceOf(Employee.class);
    }

    @Test
    void 동일_loginId_2건은_unique_제약에_걸린다() {
        // H2 unique 동작 확인 — 대소문자 차이 케이스는 collation 의존이라 검증 범위 아님(05y 설계 §4 Scope A-5).
        Employee employee = new Employee();
        employee.setLoginId("dup-login");
        employee.setDeptName("부서A");
        employee.setName("임직원");
        employeeRepository.saveAndFlush(employee);

        Applicant applicant = new Applicant("dup-ci", HashUtil.sha256("dup-ci"));
        applicant.setLoginId("dup-login");
        applicant.setUserName("지원자");

        assertThatThrownBy(() -> applicantRepository.saveAndFlush(applicant))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void null_loginId_2건은_제약에_걸리지_않는다() {
        Applicant first = new Applicant("null-ci-1", HashUtil.sha256("null-ci-1"));
        first.setUserName("지원자1");
        applicantRepository.saveAndFlush(first);

        Applicant second = new Applicant("null-ci-2", HashUtil.sha256("null-ci-2"));
        second.setUserName("지원자2");
        applicantRepository.saveAndFlush(second);

        assertThat(userRepository.findAll()).hasSize(2);
    }

    @Test
    void existsByLoginId는_서브타입_무관하게_users_전체에서_판정한다() {
        Employee employee = new Employee();
        employee.setLoginId("exists-emp");
        employee.setDeptName("부서B");
        employee.setName("임직원");
        employeeRepository.save(employee);

        assertThat(userRepository.existsByLoginId("exists-emp")).isTrue();
        assertThat(userRepository.existsByLoginId("not-exists")).isFalse();
    }
}
