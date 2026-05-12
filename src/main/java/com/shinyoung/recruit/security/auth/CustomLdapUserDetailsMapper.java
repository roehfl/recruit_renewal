package com.shinyoung.recruit.security.auth;

import com.shinyoung.recruit.domain.entity.DeptRoleMapping;
import com.shinyoung.recruit.domain.repository.DeptRoleMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.userdetails.LdapUserDetailsImpl;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
public class CustomLdapUserDetailsMapper implements UserDetailsContextMapper {

    private final DeptRoleMappingRepository deptRoleMappingRepository;

    @Override
    public UserDetails mapUserFromContext(DirContextOperations ctx, String username, Collection<? extends GrantedAuthority> authorities) {
        String loginId = ctx.getStringAttribute("sAMAccountName");
        String deptName = ctx.getStringAttribute("department");
        String empName = ctx.getStringAttribute("displayName");

        List<DeptRoleMapping> roles = deptRoleMappingRepository.findDeptRoleMappingsByDeptName(deptName);
        List<SimpleGrantedAuthority> grantedAuthorities = roles.stream()
                .map(DeptRoleMapping::getRoleName)
                .map(SimpleGrantedAuthority::new).toList();
        return CustomUserDetails.fromLdap(loginId, deptName, empName, grantedAuthorities);
    }

    @Override
    public void mapUserToContext(UserDetails user, DirContextAdapter ctx) {

    }
}
