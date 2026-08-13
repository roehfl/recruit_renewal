package com.shinyoung.recruit.security.auth;

import com.shinyoung.recruit.domain.entity.DeptRoleMapping;
import com.shinyoung.recruit.domain.entity.UserRoleMapping;
import com.shinyoung.recruit.domain.repository.DeptRoleMappingRepository;
import com.shinyoung.recruit.domain.repository.UserRoleMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class CustomLdapUserDetailsMapper implements UserDetailsContextMapper {

    private final DeptRoleMappingRepository deptRoleMappingRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;

    @Override
    public UserDetails mapUserFromContext(DirContextOperations ctx, String username, Collection<? extends GrantedAuthority> authorities) {
        String loginId = ctx.getStringAttribute("sAMAccountName");
        String empName = ctx.getStringAttribute("displayName");

        /*
         * AD는 부서를 사용자 속성(department)이 아니라 ou=부서 아래 그룹으로 관리한다.
         * DefaultLdapAuthoritiesPopulator가 찾은 그룹 cn(예: "내부채널_부서_6315")이 authorities로 들어온다.
         * dept_role_mapping의 부서명("내부채널")은 그룹 cn의 일부라 완전일치로는 찾을 수 없어 부분일치로 조회한다.
         */
        List<String> groupNames = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(StringUtils::hasText)
                .toList();

        List<DeptRoleMapping> matchedMappings = groupNames.stream()
                .flatMap(groupName -> deptRoleMappingRepository.findByDeptNameContainedIn(groupName).stream())
                .toList();

        /*
         * 최종 권한 = 부서 매핑 role ∪ 개인 매핑 role (추가 부여만, revoke 없음).
         * 개인 매핑(user_role_mapping)은 loginId 완전일치로 조회한다 — 면접관처럼
         * 부서 단위가 아니라 사람 단위로 부여해야 하는 권한의 통로다.
         */
        Stream<String> deptRoleNames = matchedMappings.stream()
                .map(DeptRoleMapping::getRoleName);

        Stream<String> userRoleNames = StringUtils.hasText(loginId)
                ? userRoleMappingRepository.findByLoginId(loginId).stream().map(UserRoleMapping::getRoleName)
                : Stream.empty();

        List<SimpleGrantedAuthority> grantedAuthorities = Stream.concat(deptRoleNames, userRoleNames)
                .filter(StringUtils::hasText)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();

        return CustomUserDetails.fromLdap(
                loginId,
                resolveDeptName(ctx, matchedMappings, groupNames),
                empName,
                grantedAuthorities
        );
    }

    /*
     * 화면에 표시할 부서명. 매핑에 등록된 부서명("내부채널")을 우선하고,
     * 매핑이 없으면 department 속성 → 그룹 cn 순으로 대체한다.
     */
    private String resolveDeptName(
            DirContextOperations ctx,
            List<DeptRoleMapping> matchedMappings,
            List<String> groupNames
    ) {
        return matchedMappings.stream()
                .map(DeptRoleMapping::getDeptName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseGet(() -> {
                    String attributeDeptName = ctx.getStringAttribute("department");

                    if (StringUtils.hasText(attributeDeptName)) {
                        return attributeDeptName;
                    }

                    return groupNames.stream().findFirst().orElse("");
                });
    }

    @Override
    public void mapUserToContext(UserDetails user, DirContextAdapter ctx) {

    }
}
