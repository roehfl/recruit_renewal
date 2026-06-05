package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.PurgeBatch;
import com.shinyoung.recruit.domain.entity.PurgeJobItem;
import com.shinyoung.recruit.domain.entity.RetentionHold;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.PurgeBatchRepository;
import com.shinyoung.recruit.domain.repository.PurgeJobItemRepository;
import com.shinyoung.recruit.domain.repository.RetentionHoldRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.PurgeBatchDetailResponse;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditReasonCode;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 파기 dry-run scan(Phase 09c, 설계 §5.4). eligibility 산정 결과를 {@code PurgeBatch}(mode=DRY_RUN)
 * + {@code PurgeJobItem} 으로만 기록한다 — <b>도메인 무변경(preview)</b>. execute 는 09d-1 별도 batch 이며
 * dry-run 을 믿지 않고 실행 시 eligibility 를 재검증한다.
 *
 * <p>ActivityLog 는 batch 단위 coarse index 만(PURGE_SCAN + 집계 metadata) — item 중복 기록 금지.
 */
@Service
@RequiredArgsConstructor
public class RetentionDryRunService {

    private final JobApplicationRepository jobApplicationRepository;
    private final StageRepository stageRepository;
    private final StageResultRepository stageResultRepository;
    private final RetentionHoldRepository retentionHoldRepository;
    private final RetentionPolicyService retentionPolicyService;
    private final RetentionEligibilityService retentionEligibilityService;
    private final PurgeBatchRepository purgeBatchRepository;
    private final PurgeJobItemRepository purgeJobItemRepository;
    private final ActivityLogService activityLogService;
    private final AuditRequestContextResolver auditRequestContextResolver;
    private final Clock clock;

    @Transactional
    public PurgeBatchDetailResponse dryRun(String actor) {
        LocalDateTime now = LocalDateTime.now(clock);
        PurgeBatch batch = purgeBatchRepository.save(PurgeBatch.startDryRun(now, now, actor));

        List<JobApplication> applications = jobApplicationRepository.findAll();
        Set<Long> heldApplicationIds = retentionHoldRepository.findByReleasedAtIsNull().stream()
                .map(RetentionHold::getApplicationId)
                .collect(Collectors.toSet());

        Map<Long, List<JobApplication>> byPosting = applications.stream()
                .collect(Collectors.groupingBy(
                        application -> application.getJobPosting().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        long eligibleCount = 0;
        long skippedCount = 0;
        long policyConflictCount = 0;
        List<PurgeJobItem> items = new ArrayList<>();

        for (Map.Entry<Long, List<JobApplication>> entry : byPosting.entrySet()) {
            Long jobPostingId = entry.getKey();
            List<JobApplication> postingApplications = entry.getValue();
            JobPosting jobPosting = postingApplications.get(0).getJobPosting();

            RetentionPolicySelection selection = retentionPolicyService.selectPolicy(jobPostingId, batch.getScanAt());
            List<Stage> finalStages = stageRepository
                    .findByJobPostingIdOrderByStageOrderAscIdAsc(jobPostingId).stream()
                    .filter(Stage::isFinalStage)
                    .toList();
            Map<Long, StageResult> finalResultByApplicationId = loadFinalStageResults(finalStages, postingApplications);

            for (JobApplication application : postingApplications) {
                RetentionEligibilityService.EligibilityDecision decision = retentionEligibilityService.evaluate(
                        application,
                        jobPosting,
                        selection,
                        heldApplicationIds.contains(application.getId()),
                        finalStages,
                        finalResultByApplicationId.get(application.getId()),
                        batch.getScanAt()
                );

                if (decision.eligible()) {
                    eligibleCount++;
                    items.add(PurgeJobItem.eligible(batch, application.getId(), jobPostingId));
                } else {
                    skippedCount++;
                    if (decision.reasonCode() == AuditReasonCode.POLICY_CONFLICT) {
                        policyConflictCount++;
                    }
                    items.add(PurgeJobItem.skipped(batch, application.getId(), jobPostingId, decision.reasonCode()));
                }
            }
        }

        purgeJobItemRepository.saveAll(items);
        batch.complete(applications.size(), eligibleCount, skippedCount, policyConflictCount, LocalDateTime.now(clock));

        // batch 단위 coarse index — 커밋된 변경의 성공 증적(in-tx, ADR-0006).
        AuditActorContext context = auditRequestContextResolver.resolve(actor);
        activityLogService.recordInCurrentTx(AuditEvent.builder()
                .actorType(context.actorType())
                .actorId(context.actorId())
                .actorRoleSnapshot(context.actorRoleSnapshot())
                .actionType(AuditActionType.PURGE_SCAN)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.PURGE_BATCH)
                .targetId(String.valueOf(batch.getId()))
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .metadata(new PurgeBatchMetadata(
                        batch.getId(),
                        batch.getMode().name(),
                        batch.getTotalCount(),
                        batch.getEligibleCount(),
                        batch.getSkippedCount(),
                        batch.getPolicyConflictCount()))
                .build());

        return PurgeBatchDetailResponse.of(batch, items);
    }

    /** finalStage 가 정확히 1개일 때만 결과를 조회한다(0/2+ 는 INVALID_STAGE_CONFIGURATION 으로 SKIP 될 것). */
    private Map<Long, StageResult> loadFinalStageResults(List<Stage> finalStages, List<JobApplication> applications) {
        if (finalStages.size() != 1) {
            return new HashMap<>();
        }
        List<Long> applicationIds = applications.stream().map(JobApplication::getId).toList();
        return stageResultRepository
                .findByStageIdAndJobApplicationIdIn(finalStages.get(0).getId(), applicationIds).stream()
                .collect(Collectors.toMap(
                        result -> result.getJobApplication().getId(),
                        Function.identity()));
    }
}
