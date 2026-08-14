package com.shinyoung.recruit.security.auth;

import com.shinyoung.recruit.domain.entity.DeptRoleMapping;
import com.shinyoung.recruit.domain.entity.UserRoleMapping;
import com.shinyoung.recruit.domain.repository.DeptRoleMappingRepository;
import com.shinyoung.recruit.domain.repository.UserRoleMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 로그인 권한 병합 단위 테스트 — 최종 권한 = 부서 매핑 role ∪ 개인 매핑 role.
 * 실제 LDAP에 연결하지 않는다(DirContextAdapter 인메모리 + repository mock).
 */
@ExtendWith(MockitoExtension.class)
class CustomLdapUserDetailsMapperTest {

    private static final String LOGIN_ID = "emp01";
    private static final String DEPT_GROUP_CN = "내부채널_부서_6315";

    @Mock
    private DeptRoleMappingRepository deptRoleMappingRepository;

    @Mock
    private UserRoleMappingRepository userRoleMappingRepository;

    private CustomLdapUserDetailsMapper mapper;

    private DirContextAdapter ctx;

    @BeforeEach
    void setUp() {
        mapper = new CustomLdapUserDetailsMapper(deptRoleMappingRepository, userRoleMappingRepository);

        ctx = new DirContextAdapter();
        ctx.setAttributeValue("sAMAccountName", LOGIN_ID);
        ctx.setAttributeValue("displayName", "임직원");
    }

    private List<GrantedAuthority> deptGroups() {
        return List.of(new SimpleGrantedAuthority(DEPT_GROUP_CN));
    }

    private List<String> authorityNames(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    @Test
    void 부서_매핑_role과_개인_매핑_role이_합집합으로_부여된다() {
        given(deptRoleMappingRepository.findByDeptNameContainedIn(DEPT_GROUP_CN))
                .willReturn(List.of(DeptRoleMapping.create("내부채널", RoleNames.RECRUIT_ADMIN)));
        given(userRoleMappingRepository.findByLoginId(LOGIN_ID))
                .willReturn(List.of(UserRoleMapping.create(LOGIN_ID, RoleNames.INTERVIEWER)));

        UserDetails userDetails = mapper.mapUserFromContext(ctx, LOGIN_ID, deptGroups());

        assertThat(authorityNames(userDetails))
                .containsExactlyInAnyOrder(RoleNames.RECRUIT_ADMIN, RoleNames.INTERVIEWER);
    }

    @Test
    void 부서_매핑과_개인_매핑이_같은_role이면_중복없이_한번만_부여된다() {
        given(deptRoleMappingRepository.findByDeptNameContainedIn(DEPT_GROUP_CN))
                .willReturn(List.of(DeptRoleMapping.create("내부채널", RoleNames.INTERVIEWER)));
        given(userRoleMappingRepository.findByLoginId(LOGIN_ID))
                .willReturn(List.of(UserRoleMapping.create(LOGIN_ID, RoleNames.INTERVIEWER)));

        UserDetails userDetails = mapper.mapUserFromContext(ctx, LOGIN_ID, deptGroups());

        assertThat(authorityNames(userDetails)).containsExactly(RoleNames.INTERVIEWER);
    }

    @Test
    void 부서_매핑이_없어도_개인_매핑만으로_권한이_부여된다() {
        given(deptRoleMappingRepository.findByDeptNameContainedIn(DEPT_GROUP_CN))
                .willReturn(List.of());
        given(userRoleMappingRepository.findByLoginId(LOGIN_ID))
                .willReturn(List.of(UserRoleMapping.create(LOGIN_ID, RoleNames.INTERVIEWER)));

        UserDetails userDetails = mapper.mapUserFromContext(ctx, LOGIN_ID, deptGroups());

        assertThat(authorityNames(userDetails)).containsExactly(RoleNames.INTERVIEWER);
    }

    @Test
    void 개인_매핑이_없으면_기존_부서_매핑_동작이_유지된다() {
        given(deptRoleMappingRepository.findByDeptNameContainedIn(DEPT_GROUP_CN))
                .willReturn(List.of(DeptRoleMapping.create("내부채널", RoleNames.RECRUIT_ADMIN)));
        given(userRoleMappingRepository.findByLoginId(LOGIN_ID))
                .willReturn(List.of());

        UserDetails userDetails = mapper.mapUserFromContext(ctx, LOGIN_ID, deptGroups());

        assertThat(authorityNames(userDetails)).containsExactly(RoleNames.RECRUIT_ADMIN);
    }
}
