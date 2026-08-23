package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.common.crypto.CryptoHolder;
import com.shinyoung.recruit.config.CryptoConfig;
import com.shinyoung.recruit.config.JpaConfig;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CryptoConfig.class, JpaConfig.class, CryptoHolder.class})
public class EmployeeRepositoryTest {

    @Autowired
    private EmployeeRepository repository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void 저장_조회_entity_검증() {
        Employee employee = new Employee();
        employee.setLoginId("22791");
        employee.setName("이솔");
        employee.setDeptName("IT센터");

        employee = repository.save(employee);

        Employee found = repository.findById(employee.getId()).get();
        User user = userRepository.findById(employee.getId()).get();

        assertThat(found).isEqualTo(employee);
        assertThat(user).isInstanceOf(Employee.class);
    }

    @Test
    void 동일_deptName_임직원_2명을_저장할_수_있다() {
        // 같은 부서 임직원은 여러 명일 수 있다 — deptName unique 제약 제거 후 동일 부서 JIT 생성이 막히지 않음을 검증.
        Employee first = new Employee();
        first.setLoginId("same-dept-1");
        first.setName("임직원1");
        first.setDeptName("IT센터");
        repository.saveAndFlush(first);

        Employee second = new Employee();
        second.setLoginId("same-dept-2");
        second.setName("임직원2");
        second.setDeptName("IT센터");
        repository.saveAndFlush(second);

        assertThat(repository.findAll())
                .filteredOn(e -> "IT센터".equals(e.getDeptName()))
                .hasSize(2);
    }
}
