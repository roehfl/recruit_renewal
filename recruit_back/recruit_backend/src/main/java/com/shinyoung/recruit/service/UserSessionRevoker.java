package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * 한 계정의 로그인 세션을 만료한다. 비밀번호를 바꾸면 탈취된 세션이 계속 살아 있지 않도록 부른다.
 *
 * <p>세션은 로그인할 때 {@link SessionRegistry}에 등록되고(AuthController), 만료 표시된 세션은 다음 요청에서
 * {@code ConcurrentSessionFilter}가 로그아웃시켜 401을 준다(SecurityConfig). 등록되지 않은 세션(배포 전 로그인)은 대상이 아니다.
 */
@Component
@RequiredArgsConstructor
public class UserSessionRevoker {

    private final SessionRegistry sessionRegistry;

    /** loginId의 세션을 모두 만료한다. {@code keepSessionId}(지금 요청의 세션)는 남긴다. null이면 전부. */
    public void expireSessions(String loginId, @Nullable String keepSessionId) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!(principal instanceof UserDetails user) || !user.getUsername().equals(loginId)) {
                continue;
            }
            for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                if (!session.getSessionId().equals(keepSessionId)) {
                    session.expireNow();
                }
            }
        }
    }
}
