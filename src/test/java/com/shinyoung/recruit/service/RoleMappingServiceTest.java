package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.dto.request.DeptRoleMappingSaveRequest;
import com.shinyoung.recruit.dto.request.UserRoleMappingSaveRequest;
import com.shinyoung.recruit.dto.response.AssignableRoleResponse;
import com.shinyoung.recruit.dto.response.DeptRoleMappingResponse;
import com.shinyoung.recruit.dto.response.UserRoleMappingResponse;
import com.shinyoung.recruit.exception.InvalidRoleMappingException;
import com.shinyoung.recruit.exception.RoleMappingNotFoundException;
import com.shinyoung.recruit.security.auth.RoleNames;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class RoleMappingServiceTest {

    @Autowired
    private RoleMappingService roleMappingService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void 부여가능_role_목록에_면접관이_포함되고_지원자는_제외된다() {
        List<AssignableRoleResponse> roles = roleMappingService.getAssignableRoles();

        assertThat(roles).extracting(AssignableRoleResponse::name)
                .contains(RoleNames.INTERVIEWER)
                .doesNotContain(RoleNames.APPLICANT);
    }

    @Test
    void 부서_매핑을_생성하고_목록에서_조회한다() {
        Long id = roleMappingService.createDeptMapping(
                new DeptRoleMappingSaveRequest("내부채널", RoleNames.RECRUIT_ADMIN));

        List<DeptRoleMappingResponse> mappings = roleMappingService.getDeptMappings();

        assertThat(mappings).anySatisfy(mapping -> {
            assertThat(mapping.id()).isEqualTo(id);
            assertThat(mapping.deptName()).isEqualTo("내부채널");
            assertThat(mapping.roleName()).isEqualTo(RoleNames.RECRUIT_ADMIN);
        });
    }

    @Test
    void 부서_매핑_생성시_부서명은_trim된다() {
        Long id = roleMappingService.createDeptMapping(
                new DeptRoleMappingSaveRequest("  내부채널  ", RoleNames.RECRUIT_ADMIN));

        assertThat(roleMappingService.getDeptMappings())
                .filteredOn(mapping -> mapping.id().equals(id))
                .singleElement()
                .satisfies(mapping -> assertThat(mapping.deptName()).isEqualTo("내부채널"));
    }

    @Test
    void 같은_부서_같은_role은_중복_등록할_수_없다() {
        roleMappingService.createDeptMapping(new DeptRoleMappingSaveRequest("내부채널", RoleNames.RECRUIT_ADMIN));

        assertThatThrownBy(() -> roleMappingService.createDeptMapping(
                new DeptRoleMappingSaveRequest("내부채널", RoleNames.RECRUIT_ADMIN)))
                .isInstanceOf(InvalidRoleMappingException.class);
    }

    @Test
    void 같은_부서라도_다른_role은_등록할_수_있다() {
        roleMappingService.createDeptMapping(new DeptRoleMappingSaveRequest("내부채널", RoleNames.RECRUIT_ADMIN));
        Long id = roleMappingService.createDeptMapping(
                new DeptRoleMappingSaveRequest("내부채널", RoleNames.INTERVIEWER));

        assertThat(id).isNotNull();
    }

    @Test
    void 부서명이_2자_미만이면_등록할_수_없다() {
        assertThatThrownBy(() -> roleMappingService.createDeptMapping(
                new DeptRoleMappingSaveRequest("채", RoleNames.RECRUIT_ADMIN)))
                .isInstanceOf(InvalidRoleMappingException.class);
    }

    @Test
    void 부여가능_목록에_없는_role은_등록할_수_없다() {
        assertThatThrownBy(() -> roleMappingService.createDeptMapping(
                new DeptRoleMappingSaveRequest("내부채널", "ROLE_SUPER_ADMIN")))
                .isInstanceOf(InvalidRoleMappingException.class);

        // 지원자는 로그인 시 하드코딩 부여되는 role이라 매핑 대상이 아니다.
        assertThatThrownBy(() -> roleMappingService.createDeptMapping(
                new DeptRoleMappingSaveRequest("내부채널", RoleNames.APPLICANT)))
                .isInstanceOf(InvalidRoleMappingException.class);
    }

    @Test
    void 부서_매핑을_수정한다() {
        Long id = roleMappingService.createDeptMapping(
                new DeptRoleMappingSaveRequest("내부채널", RoleNames.RECRUIT_ADMIN));

        roleMappingService.updateDeptMapping(id, new DeptRoleMappingSaveRequest("IT센터", RoleNames.ADMIN));

        assertThat(roleMappingService.getDeptMappings())
                .filteredOn(mapping -> mapping.id().equals(id))
                .singleElement()
                .satisfies(mapping -> {
                    assertThat(mapping.deptName()).isEqualTo("IT센터");
                    assertThat(mapping.roleName()).isEqualTo(RoleNames.ADMIN);
                });
    }

    @Test
    void 부서_매핑_수정시_다른_행과_중복되면_예외() {
        roleMappingService.createDeptMapping(new DeptRoleMappingSaveRequest("내부채널", RoleNames.RECRUIT_ADMIN));
        Long id = roleMappingService.createDeptMapping(
                new DeptRoleMappingSaveRequest("IT센터", RoleNames.ADMIN));

        assertThatThrownBy(() -> roleMappingService.updateDeptMapping(
                id, new DeptRoleMappingSaveRequest("내부채널", RoleNames.RECRUIT_ADMIN)))
                .isInstanceOf(InvalidRoleMappingException.class);
    }

    @Test
    void 부서_매핑을_삭제한다() {
        Long id = roleMappingService.createDeptMapping(
                new DeptRoleMappingSaveRequest("내부채널", RoleNames.RECRUIT_ADMIN));

        roleMappingService.deleteDeptMapping(id);

        assertThat(roleMappingService.getDeptMappings())
                .filteredOn(mapping -> mapping.id().equals(id))
                .isEmpty();
    }

    @Test
    void 존재하지_않는_부서_매핑을_수정하면_NotFound_예외() {
        assertThatThrownBy(() -> roleMappingService.updateDeptMapping(
                -1L, new DeptRoleMappingSaveRequest("내부채널", RoleNames.RECRUIT_ADMIN)))
                .isInstanceOf(RoleMappingNotFoundException.class);
    }

    @Test
    void 사용자_매핑은_users에_없는_loginId도_등록할_수_있고_이름은_null이다() {
        Long id = roleMappingService.createUserMapping(
                new UserRoleMappingSaveRequest("not-logged-in-yet", RoleNames.INTERVIEWER));

        assertThat(roleMappingService.getUserMappings())
                .filteredOn(mapping -> mapping.id().equals(id))
                .singleElement()
                .satisfies(mapping -> {
                    assertThat(mapping.loginId()).isEqualTo("not-logged-in-yet");
                    assertThat(mapping.roleName()).isEqualTo(RoleNames.INTERVIEWER);
                    assertThat(mapping.userName()).isNull();
                    assertThat(mapping.userDeptName()).isNull();
                });
    }

    @Test
    void 사용자_매핑은_users에_있으면_이름과_부서가_참고로_채워진다() {
        Employee employee = new Employee();
        employee.setLoginId("emp01");
        employee.setName("김직원");
        employee.setDeptName("IT센터");
        employeeRepository.save(employee);

        Long id = roleMappingService.createUserMapping(
                new UserRoleMappingSaveRequest("emp01", RoleNames.INTERVIEWER));

        assertThat(roleMappingService.getUserMappings())
                .filteredOn(mapping -> mapping.id().equals(id))
                .singleElement()
                .satisfies(mapping -> {
                    assertThat(mapping.userName()).isEqualTo("김직원");
                    assertThat(mapping.userDeptName()).isEqualTo("IT센터");
                });
    }

    @Test
    void 같은_사용자_같은_role은_중복_등록할_수_없다() {
        roleMappingService.createUserMapping(new UserRoleMappingSaveRequest("emp01", RoleNames.INTERVIEWER));

        assertThatThrownBy(() -> roleMappingService.createUserMapping(
                new UserRoleMappingSaveRequest("emp01", RoleNames.INTERVIEWER)))
                .isInstanceOf(InvalidRoleMappingException.class);
    }

    @Test
    void 사용자_매핑을_수정하고_삭제한다() {
        Long id = roleMappingService.createUserMapping(
                new UserRoleMappingSaveRequest("emp01", RoleNames.INTERVIEWER));

        roleMappingService.updateUserMapping(id, new UserRoleMappingSaveRequest("emp02", RoleNames.PRIVACY_ADMIN));

        assertThat(roleMappingService.getUserMappings())
                .filteredOn(mapping -> mapping.id().equals(id))
                .singleElement()
                .satisfies(mapping -> {
                    assertThat(mapping.loginId()).isEqualTo("emp02");
                    assertThat(mapping.roleName()).isEqualTo(RoleNames.PRIVACY_ADMIN);
                });

        roleMappingService.deleteUserMapping(id);

        assertThat(roleMappingService.getUserMappings())
                .filteredOn(mapping -> mapping.id().equals(id))
                .isEmpty();
    }

    @Test
    void 사용자_매핑의_loginId가_공백이면_예외() {
        assertThatThrownBy(() -> roleMappingService.createUserMapping(
                new UserRoleMappingSaveRequest("   ", RoleNames.INTERVIEWER)))
                .isInstanceOf(InvalidRoleMappingException.class);
    }
}
