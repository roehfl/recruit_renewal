package com.shinyoung.recruit.security.auth;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.User;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Objects;


/**
 * 세션 principal.
 *
 * <p>{@link CredentialsContainer} — 인증이 끝나면 {@code ProviderManager}가 비밀번호 해시를 지운다(세션에 남기지 않는다).
 * {@code equals}/{@code hashCode}는 loginId 기준이다 — {@code SessionRegistry}가 principal로 한 계정의 세션을 모은다.
 */
public class CustomUserDetails implements UserDetails, CredentialsContainer {

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

    @Override
    public void eraseCredentials() {
        password = null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CustomUserDetails other && Objects.equals(loginId, other.loginId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(loginId);
    }

}
