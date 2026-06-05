package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.RetentionPolicy;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.RetentionPolicyRepository;
import com.shinyoung.recruit.dto.request.RetentionPolicyRequest;
import com.shinyoung.recruit.dto.response.RetentionPolicyResponse;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.exception.InvalidRetentionPolicyException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import com.shinyoung.recruit.exception.RetentionPolicyNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * RetentionPolicy 관리 + scanAt 기준 적용 정책 선택(Phase 09c, 설계 §5.3).
 *
 * <p>선택 규칙(9c 계약): ① override 우선 ② 없으면 global ③ effective window 는 scanAt 기준
 * ④/⑤ 같은 scope 의 enabled 정책 overlap 금지(본 서비스가 CUD 시 검증) ⑥ 부재 = POLICY_NOT_FOUND
 * ⑦ fail-safe — 검증을 우회한 데이터로 active 후보가 2개 이상이면 아무것도 선택하지 않고 POLICY_CONFLICT.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetentionPolicyService {

    private final RetentionPolicyRepository retentionPolicyRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ActivityLogService activityLogService;
    private final AuditRequestContextResolver auditRequestContextResolver;

    public List<RetentionPolicyResponse> getPolicies() {
        return retentionPolicyRepository.findAllByOrderByIdAsc().stream()
                .map(RetentionPolicyResponse::from)
                .toList();
    }

    @Transactional
    public RetentionPolicyResponse create(RetentionPolicyRequest request, String actor) {
        validateRequest(request);
        validateNoOverlap(request, null);

        RetentionPolicy policy = retentionPolicyRepository.save(RetentionPolicy.create(
                request.jobPostingId(),
                request.retentionPeriodDays(),
                request.baselineType(),
                request.enabled(),
                request.effectiveFrom(),
                request.effectiveTo()
        ));
        recordPolicyAudit("CREATE", policy, actor);
        return RetentionPolicyResponse.from(policy);
    }

    @Transactional
    public RetentionPolicyResponse update(Long policyId, RetentionPolicyRequest request, String actor) {
        RetentionPolicy policy = findPolicy(policyId);
        if (!Objects.equals(policy.getJobPostingId(), request.jobPostingId())) {
            throw new InvalidRetentionPolicyException("정책의 적용 대상(jobPostingId)은 변경할 수 없습니다. 새 정책을 생성하세요.");
        }
        validateRequest(request);
        validateNoOverlap(request, policyId);

        policy.update(
                request.retentionPeriodDays(),
                request.baselineType(),
                request.enabled(),
                request.effectiveFrom(),
                request.effectiveTo()
        );
        recordPolicyAudit("UPDATE", policy, actor);
        return RetentionPolicyResponse.from(policy);
    }

    @Transactional
    public void delete(Long policyId, String actor) {
        RetentionPolicy policy = findPolicy(policyId);
        recordPolicyAudit("DELETE", policy, actor);
        retentionPolicyRepository.delete(policy);
    }

    /**
     * scanAt 시점의 적용 정책 선택(설계 §5.3 규칙 1~7). 호출자는 결과의 {@code reasonCode}
     * (POLICY_NOT_FOUND/POLICY_CONFLICT)를 그대로 SKIP 사유로 쓴다.
     */
    public RetentionPolicySelection selectPolicy(Long jobPostingId, LocalDateTime scanAt) {
        List<RetentionPolicy> overrides = retentionPolicyRepository
                .findByJobPostingIdAndEnabledTrue(jobPostingId).stream()
                .filter(policy -> policy.isEffectiveAt(scanAt))
                .toList();
        if (overrides.size() > 1) {
            return RetentionPolicySelection.conflict();
        }
        if (overrides.size() == 1) {
            return RetentionPolicySelection.found(overrides.get(0));
        }

        List<RetentionPolicy> globals = retentionPolicyRepository
                .findByJobPostingIdIsNullAndEnabledTrue().stream()
                .filter(policy -> policy.isEffectiveAt(scanAt))
                .toList();
        if (globals.size() > 1) {
            return RetentionPolicySelection.conflict();
        }
        if (globals.size() == 1) {
            return RetentionPolicySelection.found(globals.get(0));
        }
        return RetentionPolicySelection.notFound();
    }

    private void validateRequest(RetentionPolicyRequest request) {
        if (request.jobPostingId() != null && !jobPostingRepository.existsById(request.jobPostingId())) {
            throw new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + request.jobPostingId());
        }
        if (request.effectiveFrom() != null && request.effectiveTo() != null
                && request.effectiveFrom().isAfter(request.effectiveTo())) {
            throw new InvalidRetentionPolicyException("effectiveFrom은 effectiveTo보다 늦을 수 없습니다.");
        }
    }

    /** 같은 scope(override jobPostingId / global) 의 enabled 정책 간 effective window overlap 금지(규칙 4/5). */
    private void validateNoOverlap(RetentionPolicyRequest request, Long excludePolicyId) {
        if (!request.enabled()) {
            return; // disabled 정책은 선택 후보가 아니므로 overlap 제약 대상이 아니다.
        }
        List<RetentionPolicy> sameScope = request.jobPostingId() == null
                ? retentionPolicyRepository.findByJobPostingIdIsNullAndEnabledTrue()
                : retentionPolicyRepository.findByJobPostingIdAndEnabledTrue(request.jobPostingId());

        boolean overlapped = sameScope.stream()
                // create(excludePolicyId=null) 경로에서는 자기 자신 제외가 없다 — null id 끼리의 오인 제외 방지.
                .filter(policy -> excludePolicyId == null || !Objects.equals(policy.getId(), excludePolicyId))
                .anyMatch(policy -> policy.overlapsWith(request.effectiveFrom(), request.effectiveTo()));
        if (overlapped) {
            throw new InvalidRetentionPolicyException(
                    "같은 적용 대상의 enabled 정책과 effective 기간이 겹칩니다(overlap 금지).");
        }
    }

    private RetentionPolicy findPolicy(Long policyId) {
        return retentionPolicyRepository.findById(policyId)
                .orElseThrow(() -> new RetentionPolicyNotFoundException("RetentionPolicy를 찾을 수 없습니다."));
    }

    /** 정책 CUD = 커밋된 변경의 성공 증적(in-tx, ADR-0006). */
    private void recordPolicyAudit(String operation, RetentionPolicy policy, String actor) {
        AuditActorContext context = auditRequestContextResolver.resolve(actor);
        activityLogService.recordInCurrentTx(AuditEvent.builder()
                .actorType(context.actorType())
                .actorId(context.actorId())
                .actorRoleSnapshot(context.actorRoleSnapshot())
                .actionType(AuditActionType.RETENTION_POLICY_UPDATE)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.RETENTION_POLICY)
                .targetId(String.valueOf(policy.getId()))
                .jobPostingId(policy.getJobPostingId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .metadata(new RetentionPolicyChangeMetadata(
                        operation,
                        policy.getId(),
                        policy.getJobPostingId(),
                        policy.getRetentionPeriodDays(),
                        policy.getBaselineType().name(),
                        policy.isEnabled()))
                .build());
    }
}
