package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.RetentionPolicy;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.RetentionPolicyRepository;
import com.shinyoung.recruit.dto.request.RetentionPolicyRequest;
import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.RetentionBaselineType;
import com.shinyoung.recruit.exception.InvalidRetentionPolicyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/** RetentionPolicy 선택 규칙(설계 §5.3 — 9c 계약)과 overlap 검증을 고정한다. */
@ExtendWith(MockitoExtension.class)
class RetentionPolicyServiceTest {

    private static final LocalDateTime SCAN_AT = LocalDateTime.of(2026, 6, 5, 12, 0);

    @Mock
    private RetentionPolicyRepository retentionPolicyRepository;

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private AuditRequestContextResolver auditRequestContextResolver;

    private RetentionPolicyService retentionPolicyService;

    @BeforeEach
    void setUp() {
        retentionPolicyService = new RetentionPolicyService(
                retentionPolicyRepository, jobPostingRepository, activityLogService, auditRequestContextResolver);
        lenient().when(auditRequestContextResolver.resolve(anyString()))
                .thenReturn(new AuditActorContext(ActorType.EMPLOYEE, "privacy01", "ROLE_PRIVACY_ADMIN", null, null));
    }

    private RetentionPolicy global(LocalDateTime from, LocalDateTime to) {
        return RetentionPolicy.create(null, 30, RetentionBaselineType.HIRING_ENDED_AT, true, from, to);
    }

    private RetentionPolicy override(Long jobPostingId) {
        return RetentionPolicy.create(jobPostingId, 90, RetentionBaselineType.HIRING_ENDED_AT, true, null, null);
    }

    @Test
    void override가_있으면_global보다_우선한다() {
        RetentionPolicy overridePolicy = override(7L);
        given(retentionPolicyRepository.findByJobPostingIdAndEnabledTrue(7L))
                .willReturn(List.of(overridePolicy));

        RetentionPolicySelection selection = retentionPolicyService.selectPolicy(7L, SCAN_AT);

        assertThat(selection.selected()).isTrue();
        assertThat(selection.policy()).isSameAs(overridePolicy);
    }

    @Test
    void override가_없으면_global을_쓴다() {
        RetentionPolicy globalPolicy = global(null, null);
        given(retentionPolicyRepository.findByJobPostingIdAndEnabledTrue(7L)).willReturn(List.of());
        given(retentionPolicyRepository.findByJobPostingIdIsNullAndEnabledTrue()).willReturn(List.of(globalPolicy));

        RetentionPolicySelection selection = retentionPolicyService.selectPolicy(7L, SCAN_AT);

        assertThat(selection.policy()).isSameAs(globalPolicy);
    }

    @Test
    void effective_window는_scanAt_기준으로_평가한다() {
        // scanAt 이전에 종료된 정책은 후보가 아니다 → NOT_FOUND.
        given(retentionPolicyRepository.findByJobPostingIdAndEnabledTrue(7L)).willReturn(List.of());
        given(retentionPolicyRepository.findByJobPostingIdIsNullAndEnabledTrue())
                .willReturn(List.of(global(null, SCAN_AT.minusDays(1))));

        RetentionPolicySelection selection = retentionPolicyService.selectPolicy(7L, SCAN_AT);

        assertThat(selection.reasonCode()).isEqualTo(AuditReasonCode.POLICY_NOT_FOUND);
    }

    @Test
    void 적용_정책이_없으면_POLICY_NOT_FOUND() {
        given(retentionPolicyRepository.findByJobPostingIdAndEnabledTrue(7L)).willReturn(List.of());
        given(retentionPolicyRepository.findByJobPostingIdIsNullAndEnabledTrue()).willReturn(List.of());

        assertThat(retentionPolicyService.selectPolicy(7L, SCAN_AT).reasonCode())
                .isEqualTo(AuditReasonCode.POLICY_NOT_FOUND);
    }

    @Test
    void active_후보가_2개_이상이면_fail_safe_POLICY_CONFLICT() {
        // override 2개.
        given(retentionPolicyRepository.findByJobPostingIdAndEnabledTrue(7L))
                .willReturn(List.of(override(7L), override(7L)));
        assertThat(retentionPolicyService.selectPolicy(7L, SCAN_AT).reasonCode())
                .isEqualTo(AuditReasonCode.POLICY_CONFLICT);

        // global 2개.
        given(retentionPolicyRepository.findByJobPostingIdAndEnabledTrue(8L)).willReturn(List.of());
        given(retentionPolicyRepository.findByJobPostingIdIsNullAndEnabledTrue())
                .willReturn(List.of(global(null, null), global(null, null)));
        assertThat(retentionPolicyService.selectPolicy(8L, SCAN_AT).reasonCode())
                .isEqualTo(AuditReasonCode.POLICY_CONFLICT);
    }

    @Test
    void 같은_scope의_enabled_정책과_기간이_겹치면_생성_거부() {
        given(retentionPolicyRepository.findByJobPostingIdIsNullAndEnabledTrue())
                .willReturn(List.of(global(null, null))); // 개방 구간 — 모든 기간과 overlap.

        RetentionPolicyRequest request = new RetentionPolicyRequest(
                null, 30, RetentionBaselineType.HIRING_ENDED_AT, true,
                SCAN_AT, SCAN_AT.plusDays(10));

        assertThatThrownBy(() -> retentionPolicyService.create(request, "privacy01"))
                .isInstanceOf(InvalidRetentionPolicyException.class)
                .hasMessageContaining("겹칩");
    }

    @Test
    void 기간이_분리된_정책은_생성된다_감사도_남는다() {
        given(retentionPolicyRepository.findByJobPostingIdIsNullAndEnabledTrue())
                .willReturn(List.of(global(null, SCAN_AT.minusDays(10))));
        given(retentionPolicyRepository.save(any(RetentionPolicy.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        RetentionPolicyRequest request = new RetentionPolicyRequest(
                null, 30, RetentionBaselineType.HIRING_ENDED_AT, true,
                SCAN_AT.minusDays(9), null);

        retentionPolicyService.create(request, "privacy01");

        verify(activityLogService).recordInCurrentTx(any(AuditEvent.class));
    }

    @Test
    void disabled_정책은_overlap_제약_대상이_아니다() {
        given(retentionPolicyRepository.save(any(RetentionPolicy.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        RetentionPolicyRequest request = new RetentionPolicyRequest(
                null, 30, RetentionBaselineType.HIRING_ENDED_AT, false, null, null);

        retentionPolicyService.create(request, "privacy01");

        verify(activityLogService).recordInCurrentTx(any(AuditEvent.class));
    }

    @Test
    void effectiveFrom이_effectiveTo보다_늦으면_거부() {
        RetentionPolicyRequest request = new RetentionPolicyRequest(
                null, 30, RetentionBaselineType.HIRING_ENDED_AT, true,
                SCAN_AT, SCAN_AT.minusDays(1));

        assertThatThrownBy(() -> retentionPolicyService.create(request, "privacy01"))
                .isInstanceOf(InvalidRetentionPolicyException.class);
    }
}
