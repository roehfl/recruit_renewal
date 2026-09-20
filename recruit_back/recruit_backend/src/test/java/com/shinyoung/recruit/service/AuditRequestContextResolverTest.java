package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuditRequestContextResolver} 의 actorType 판정 단위 검증(Phase 10).
 * SecurityContext 가 비어 있을 때(직접 서비스 호출·스케줄러 등)의 분기만 다룬다.
 */
class AuditRequestContextResolverTest {

    private final AuditRequestContextResolver auditRequestContextResolver = new AuditRequestContextResolver();

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("SYSTEM 예약 actorId 는 ActorType.SYSTEM 으로 기록한다")
    void systemActor() {
        AuditActorContext context = auditRequestContextResolver.resolve(
                AuditRequestContextResolver.SYSTEM_ACTOR_ID);

        assertThat(context.actorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(context.actorId()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("일반 임직원 actorId 는 EMPLOYEE 로 기록한다")
    void employeeActor() {
        assertThat(auditRequestContextResolver.resolve("emp001").actorType()).isEqualTo(ActorType.EMPLOYEE);
    }

    @Test
    @DisplayName("actorId 가 없으면 ANONYMOUS")
    void anonymousActor() {
        assertThat(auditRequestContextResolver.resolve(null).actorType()).isEqualTo(ActorType.ANONYMOUS);
    }
}
