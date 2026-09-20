package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.RetentionScheduleSetting;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.RetentionScheduleSettingRepository;
import com.shinyoung.recruit.dto.response.RetentionScheduleResponse;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.enumeration.RetentionScheduleRunResult;
import com.shinyoung.recruit.exception.InvalidRetentionRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 자동 파기 스케줄 설정과 다음 파기 예정일(Phase 10).
 *
 * <p>예정일은 <b>보존기간만</b> 계산한 하한이다 — 적격성 9단계(전형 종료 여부 등)를 반영하지 않으므로
 * 그날 실제 파기 건수가 0일 수 있다. 목적은 "이 날짜 전에는 파기 대상이 절대 없다"를 보장해
 * 불필요한 전량 스캔과 {@code purge_job_item} 누적을 막는 것이다.
 *
 * <p><b>알려진 한계(후속 과제)</b>: 적격성 판정을 영원히 통과하지 못하는 지원서 — 제출하지 않은
 * {@code DRAFT}, 최종 전형이 미확정인 {@code APPLICATION_NOT_TERMINAL}, 최종 전형이 1개가 아닌 공고의
 * {@code INVALID_STAGE_CONFIGURATION} — 는 {@code purgeResult} 가 계속 {@code null} 이라 이 하한 계산에
 * 계속 잡힌다. 그 공고의 {@code closedAt + 보존기간}이 지나면 게이트가 매일 열려 전량 스캔이 재발한다.
 */
@Service
@RequiredArgsConstructor
public class RetentionScheduleService {

    /** 파기 예정이 없음을 뜻하는 약속된 값. */
    public static final LocalDate NO_SCHEDULE_DATE = LocalDate.of(9999, 12, 31);

    private final RetentionScheduleSettingRepository retentionScheduleSettingRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final RetentionPolicyService retentionPolicyService;
    private final ActivityLogService activityLogService;
    private final AuditRequestContextResolver auditRequestContextResolver;
    private final Clock clock;

    /**
     * 다음 파기 예정일. 공고별 override 정책은 보지 않는다 — 예정일은 하한이면 충분하고,
     * override 가 있으면 그 공고는 더 늦게 만료되므로 하한이 깨지지 않는다.
     */
    @Transactional(readOnly = true)
    public LocalDate nextPurgeDate() {
        RetentionPolicySelection selection =
                retentionPolicyService.selectPolicy(null, LocalDateTime.now(clock));
        if (!selection.selected()) {
            return NO_SCHEDULE_DATE;
        }
        return jobApplicationRepository.findEarliestUnpurgedClosedAt()
                .map(closedAt -> closedAt.plusDays(selection.policy().getRetentionPeriodDays()).toLocalDate())
                .orElse(NO_SCHEDULE_DATE);
    }

    /** 자동 파기 설정 조회(화면 표시용) — 행이 없으면 꺼짐 기본값으로 본다. */
    @Transactional(readOnly = true)
    public RetentionScheduleResponse getSchedule() {
        RetentionScheduleSetting setting = retentionScheduleSettingRepository
                .findById(RetentionScheduleSetting.SINGLETON_ID)
                .orElseGet(RetentionScheduleSetting::initial);
        RetentionPolicySelection selection =
                retentionPolicyService.selectPolicy(null, LocalDateTime.now(clock));

        return new RetentionScheduleResponse(
                setting.isEnabled(),
                nextPurgeDate(),
                selection.selected(),
                selection.selected() ? selection.policy().getRetentionPeriodDays() : null,
                setting.getLastRunAt(),
                setting.getLastRunResult(),
                setting.getLastRunBatchId());
    }

    /**
     * 자동 파기 on/off. 정책이 없으면 켜도 전건 스킵되므로 켜기 자체를 막는다.
     * 감사는 보존 설정 변경이라는 성격이 같아 {@code RETENTION_POLICY_UPDATE} 를 재사용한다.
     */
    @Transactional
    public RetentionScheduleResponse updateEnabled(boolean enabled, String actor) {
        String resolvedActor = requireActor(actor);
        RetentionPolicySelection selection =
                retentionPolicyService.selectPolicy(null, LocalDateTime.now(clock));
        if (enabled && !selection.selected()) {
            throw new InvalidRetentionRequestException("보존 정책을 먼저 등록해야 자동 파기를 켤 수 있습니다.");
        }

        RetentionScheduleSetting setting = retentionScheduleSettingRepository
                .findById(RetentionScheduleSetting.SINGLETON_ID)
                .orElseGet(RetentionScheduleSetting::initial);
        setting.updateEnabled(enabled, resolvedActor, LocalDateTime.now(clock));
        retentionScheduleSettingRepository.save(setting);

        recordScheduleAudit(enabled, selection, resolvedActor);

        return getSchedule();
    }

    /** 스케줄 1회 기동 결과 기록(화면 표시용). 감사는 dry-run·execute 가 각자 남긴다. */
    @Transactional
    public void recordRun(RetentionScheduleRunResult result, Long batchId) {
        RetentionScheduleSetting setting = retentionScheduleSettingRepository
                .findById(RetentionScheduleSetting.SINGLETON_ID)
                .orElseGet(RetentionScheduleSetting::initial);
        setting.recordRun(result, batchId, LocalDateTime.now(clock));
        retentionScheduleSettingRepository.save(setting);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return retentionScheduleSettingRepository.findById(RetentionScheduleSetting.SINGLETON_ID)
                .map(RetentionScheduleSetting::isEnabled)
                .orElse(false);
    }

    /** 보존 설정 변경 감사(in-tx) — {@link RetentionPolicyService} 의 정책 CUD 감사 기록 패턴을 그대로 따른다. */
    private void recordScheduleAudit(boolean enabled, RetentionPolicySelection selection, String actor) {
        AuditActorContext context = auditRequestContextResolver.resolve(actor);
        activityLogService.recordInCurrentTx(AuditEvent.builder()
                .actorType(context.actorType())
                .actorId(context.actorId())
                .actorRoleSnapshot(context.actorRoleSnapshot())
                .actionType(AuditActionType.RETENTION_POLICY_UPDATE)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.RETENTION_SCHEDULE)
                .targetId(String.valueOf(RetentionScheduleSetting.SINGLETON_ID))
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .metadata(new RetentionPolicyChangeMetadata(
                        enabled ? "SCHEDULE_ENABLE" : "SCHEDULE_DISABLE",
                        selection.selected() ? selection.policy().getId() : null,
                        selection.selected() ? selection.policy().getJobPostingId() : null,
                        selection.selected() ? selection.policy().getRetentionPeriodDays() : null,
                        selection.selected() ? selection.policy().getBaselineType().name() : null,
                        enabled))
                .build());
    }

    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidRetentionRequestException("Retention actor is required.");
        }
        return actor.trim();
    }
}
