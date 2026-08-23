package com.shinyoung.recruit.security.auth;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.User;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;


public class CustomUserDetails implements UserDetails {

    public static final String USER_TYPE_APPLICANT = "Applicant";
    public static final String USER_TYPE_EMPLOYEE = "Employee";

    private String loginId;
    @Getter
    private String deptName;
    @Getter
    private String name;
    @Getter
    private String userType;
    private String password;
    private Collection<? extends GrantedAuthority> authorities;
    private CustomUserDetails() {

    }


    public static CustomUserDetails fromUser(User user, Collection<? extends GrantedAuthority> authorities) {
        CustomUserDetails customUserDetails = new CustomUserDetails();
        customUserDetails.loginId = user.getLoginId();
        customUserDetails.authorities = authorities;
        customUserDetails.deptName = "";
        if(user instanceof Applicant a) {
            customUserDetails.password = a.getPassword();
            customUserDetails.userType = USER_TYPE_APPLICANT;
        } else {
            customUserDetails.userType = USER_TYPE_EMPLOYEE;
        }
        customUserDetails.name = user.getName();
        return customUserDetails;
    }

    public static CustomUserDetails fromLdap(String loginId, String deptName, String name, Collection<? extends GrantedAuthority> authorities) {
        CustomUserDetails customUserDetails = new CustomUserDetails();
        customUserDetails.loginId = loginId;
        customUserDetails.deptName = deptName;
        customUserDetails.authorities = authorities;
        customUserDetails.password = null;
        customUserDetails.name = name;
        customUserDetails.userType = USER_TYPE_EMPLOYEE;
        return customUserDetails;
    }

    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public @NonNull String getUsername() {
        return loginId;
    }

}
