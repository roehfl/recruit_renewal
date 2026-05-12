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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
