package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.common.crypto.CryptoHolder;
import com.shinyoung.recruit.config.CryptoConfig;
import com.shinyoung.recruit.config.JpaConfig;
import com.shinyoung.recruit.domain.entity.DeptRoleMapping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CryptoConfig.class, JpaConfig.class, CryptoHolder.class})
public class DeptRoleMappingRepositoryTest {

    @Autowired
    private DeptRoleMappingRepository repository;

    @Test
    void 저장_조회_entity_검증() {
        DeptRoleMapping deptRoleMapping = new DeptRoleMapping();
        deptRoleMapping.setRoleName("ROLE_ADMIN");
        deptRoleMapping.setDeptName("IT센터");
        repository.save(deptRoleMapping);

        deptRoleMapping = new DeptRoleMapping();
        deptRoleMapping.setDeptName("아무개");
        deptRoleMapping.setRoleName("ROLE_INTERVIEWER");
        repository.save(deptRoleMapping);

        List<DeptRoleMapping> deptRoleMappings = repository.findDeptRoleMappingsByDeptName("IT센터");

        assertThat(deptRoleMappings).hasSize(1);
    }
}
