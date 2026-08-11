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

    /** AD 부서 그룹 cn 실제 형태(부서명 뒤에 코드가 붙는다). */
    private static final String GROUP_NAME = "내부채널_부서_6315";

    @Autowired
    private DeptRoleMappingRepository repository;

    private void saveMapping(String deptName, String roleName) {
        DeptRoleMapping deptRoleMapping = new DeptRoleMapping();
        deptRoleMapping.setDeptName(deptName);
        deptRoleMapping.setRoleName(roleName);
        repository.save(deptRoleMapping);
    }

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

    /*
     * 아래는 부서 그룹명 부분일치 조회(findByDeptNameContainedIn) 검증이다.
     * AD는 부서를 ou=부서 아래 그룹으로 관리하고 그룹 cn이 "내부채널_부서_6315"처럼
     * 부서명 뒤에 코드가 붙는 형태라, dept_name("내부채널")을 그룹명의 부분문자열로 판정한다.
     */

    @Test
    void 부서명이_그룹명에_포함되면_매핑을_찾는다() {
        saveMapping("내부채널", "ROLE_RECRUIT_ADMIN");

        List<DeptRoleMapping> found = repository.findByDeptNameContainedIn(GROUP_NAME);

        assertThat(found).extracting(DeptRoleMapping::getRoleName)
                .containsExactly("ROLE_RECRUIT_ADMIN");
    }

    @Test
    void 한_부서에_여러_역할을_등록하면_모두_찾는다() {
        saveMapping("내부채널", "ROLE_RECRUIT_ADMIN");
        saveMapping("내부채널", "ROLE_INTERVIEWER");

        List<DeptRoleMapping> found = repository.findByDeptNameContainedIn(GROUP_NAME);

        assertThat(found).extracting(DeptRoleMapping::getRoleName)
                .containsExactlyInAnyOrder("ROLE_RECRUIT_ADMIN", "ROLE_INTERVIEWER");
    }

    @Test
    void 관계없는_부서는_매칭되지_않는다() {
        saveMapping("인사기획부", "ROLE_RECRUIT_ADMIN");

        assertThat(repository.findByDeptNameContainedIn(GROUP_NAME)).isEmpty();
    }

    @Test
    void 완전일치_그룹명도_매칭된다() {
        saveMapping(GROUP_NAME, "ROLE_RECRUIT_ADMIN");

        assertThat(repository.findByDeptNameContainedIn(GROUP_NAME)).hasSize(1);
    }

    /*
     * 부분일치의 알려진 위험을 고정해 둔다.
     * 짧은 부서명은 다른 부서 그룹에도 걸리므로, 등록하는 dept_name은 충분히 구체적이어야 한다.
     */
    @Test
    void 짧은_부서명은_다른_부서_그룹에도_걸린다() {
        saveMapping("채널", "ROLE_RECRUIT_ADMIN");

        assertThat(repository.findByDeptNameContainedIn(GROUP_NAME))
                .as("'채널'이 '내부채널_부서_6315'에 부분일치하므로 의도치 않게 매칭된다")
                .hasSize(1);
    }

    @Test
    void 빈_부서명은_모든_그룹에_매칭되지_않는다() {
        saveMapping("", "ROLE_RECRUIT_ADMIN");

        assertThat(repository.findByDeptNameContainedIn(GROUP_NAME)).isEmpty();
    }
}
