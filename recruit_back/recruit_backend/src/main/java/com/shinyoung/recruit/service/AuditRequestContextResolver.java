package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.stream.Collectors;

/**
 * 서비스 계층 감사 계측(in-tx)용 행위자/요청 컨텍스트 리졸버(Phase 09b).
 *
 * <p>커밋된 변경의 성공 증적은 비즈니스 트랜잭션 안(서비스 계층)에서 남겨야 하는데(ADR-0006), 서비스는
 * ip/ua/권한 스냅샷을 모른다. 시그니처 오염 없이 현재 요청의 SecurityContext/RequestContext 에서 해석한다.
 * 비웹/미인증 컨텍스트(직접 서비스 호출 테스트 등)에서는 null-safe 로 동작한다.
 */
@Component
public class AuditRequestContextResolver {

    public AuditActorContext resolve() {
        return resolve(null);
    }

    /**
     * @param knownEmployeeActor 서비스가 이미 검증한 임직원 actor(loginId). SecurityContext 에 principal 이
     *                           없을 때(직접 서비스 호출 등)의 fallback 으로 쓴다.
     */
    public AuditActorContext resolve(String knownEmployeeActor) {
        String actorId = null;
        String roleSnapshot = null;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            actorId = userDetails.getUsername();
            roleSnapshot = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","));
        }
        if ((actorId == null || actorId.isBlank()) && knownEmployeeActor != null && !knownEmployeeActor.isBlank()) {
            actorId = knownEmployeeActor;
        }

        ActorType actorType = (actorId == null || actorId.isBlank()) ? ActorType.ANONYMOUS : ActorType.EMPLOYEE;

        String ipAddress = null;
        String userAgent = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            ipAddress = request.getRemoteAddr();
            userAgent = request.getHeader("User-Agent");
        }

        return new AuditActorContext(actorType, actorId, roleSnapshot, ipAddress, userAgent);
    }
}
