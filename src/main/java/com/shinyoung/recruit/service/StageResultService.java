package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.StageResultBulkUpdateItemRequest;
import com.shinyoung.recruit.dto.request.StageResultBulkUpdateRequest;
import com.shinyoung.recruit.dto.request.StageResultUpdateRequest;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.dto.response.StageResultBulkUpdateResponse;
import com.shinyoung.recruit.dto.response.StageResultInitializeResponse;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.exception.InvalidStageResultException;
import com.shinyoung.recruit.exception.StageResultNotFoundException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StageResultService {

    private final StageRepository stageRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final StageResultRepository stageResultRepository;
    private final Clock clock;
    private final ActivityLogService activityLogService;
    private final AuditRequestContextResolver auditRequestContextResolver;

    @Transactional
    public StageResultInitializeResponse initialize(Long stageId) {
        Stage stage = findStage(stageId);
        validateInitializable(stage);

        Long jobPostingId = stage.getJobPosting().getId();
        List<JobApplication> applications = jobApplicationRepository.findByJobPostingId(jobPostingId);
        List<JobApplication> submittedApplications = applications.stream()
                .filter(application -> application.getStatus() == JobApplicationStatus.SUBMITTED)
                .toList();
        int skippedCount = applications.size() - submittedApplications.size();

        List<Long> submittedApplicationIds = submittedApplications.stream()
                .map(JobApplication::getId)
                .toList();
        Set<Long> existingApplicationIds = submittedApplicationIds.isEmpty()
                ? Set.of()
                : findExistingApplicationIds(stageId, submittedApplicationIds);

        List<StageResult> newResults = submittedApplications.stream()
                .filter(application -> !existingApplicationIds.contains(application.getId()))
                .map(application -> StageResult.initialize(stage, application))
                .toList();
        stageResultRepository.saveAll(newResults);

        return new StageResultInitializeResponse(
                stageId,
                newResults.size(),
                existingApplicationIds.size(),
                skippedCount,
                getResults(stageId)
        );
    }

    public List<AdminStageResultResponse> getResults(Long stageId) {
        findStage(stageId);
        return stageResultRepository.findByStageIdForAdminList(stageId).stream()
                .map(AdminStageResultResponse::from)
                .toList();
    }

    @Transactional
    public AdminStageResultResponse updateResult(
            Long stageId,
            Long resultId,
            StageResultUpdateRequest request,
            String actor
    ) {
        validateActor(actor);
        Stage stage = findStage(stageId);
        validateEditable(stage);
        validateUpdateRequest(request);

        StageResult stageResult = stageResultRepository.findByIdAndStageId(resultId, stageId)
                .orElseThrow(() -> new StageResultNotFoundException("StageResult not found."));
        stageResult.updateResult(
                request.resultStatus(),
                request.score(),
                request.comment(),
                LocalDateTime.now(clock),
                actor
        );
        // 커밋된 변경의 성공 증적 — 비즈니스 tx 에 join(in-tx, ADR-0006). 전후값은 CorrectionHistory 가 보유.
        recordCorrectAudit(stage, actor, String.valueOf(resultId),
                stageResult.getJobApplication().getId(), new StageResultChangeMetadata(stageId, 1));
        return AdminStageResultResponse.from(stageResult);
    }

    @Transactional
    public StageResultBulkUpdateResponse bulkUpdateResults(
            Long stageId,
            StageResultBulkUpdateRequest request,
            String actor
    ) {
        return bulkUpdateResults(stageId, request, actor, true);
    }

    /**
     * @param recordAudit 수동 정정 경로는 true(STAGE_RESULT_CORRECT in-tx 감사). Excel upload commit 은
     *                    STAGE_RESULT_UPLOAD 한 건으로 따로 감사하므로 false 로 이중 기록을 막는다(Phase 09b).
     */
    @Transactional
    public StageResultBulkUpdateResponse bulkUpdateResults(
            Long stageId,
            StageResultBulkUpdateRequest request,
            String actor,
            boolean recordAudit
    ) {
        validateActor(actor);
        Stage stage = findStage(stageId);
        validateEditable(stage);
        validateBulkRequest(request);

        List<Long> requestedIds = request.results().stream()
                .map(StageResultBulkUpdateItemRequest::stageResultId)
                .toList();
        List<StageResult> stageResults = stageResultRepository.findByStageIdAndIdIn(stageId, requestedIds);
        if (stageResults.size() != requestedIds.size()) {
            throw new StageResultNotFoundException("StageResult not found.");
        }

        Map<Long, StageResult> resultMap = stageResults.stream()
                .collect(Collectors.toMap(StageResult::getId, Function.identity()));
        LocalDateTime decidedAt = LocalDateTime.now(clock);
        for (StageResultBulkUpdateItemRequest item : request.results()) {
            StageResult stageResult = resultMap.get(item.stageResultId());
            stageResult.updateResult(
                    item.resultStatus(),
                    item.score(),
                    item.comment(),
                    decidedAt,
                    actor
            );
        }

        if (recordAudit) {
            recordCorrectAudit(stage, actor, String.valueOf(stageId), null,
                    new StageResultChangeMetadata(stageId, request.results().size()));
        }
        return new StageResultBulkUpdateResponse(stageId, request.results().size(), getResults(stageId));
    }

    private void recordCorrectAudit(
            Stage stage,
            String actor,
            String targetId,
            Long applicationId,
            StageResultChangeMetadata metadata
    ) {
        AuditActorContext context = auditRequestContextResolver.resolve(actor);
        activityLogService.recordInCurrentTx(AuditEvent.builder()
                .actorType(context.actorType())
                .actorId(context.actorId())
                .actorRoleSnapshot(context.actorRoleSnapshot())
                .actionType(AuditActionType.STAGE_RESULT_CORRECT)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.STAGE_RESULT)
                .targetId(targetId)
                .jobPostingId(stage.getJobPosting().getId())
                .applicationId(applicationId)
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .metadata(metadata)
                .build());
    }

    /**
     * bulk update가 가능한 상태인지(actor 필수 + Stage IN_PROGRESS)만 선검증한다. Excel upload commit이 변경 행이
     * 0건이어도 stage/actor guard를 우회하지 않도록, 실제 적용 호출 여부와 무관하게 먼저 호출하기 위한 공개 진입점이다.
     */
    public void validateBulkUpdatable(Long stageId, String actor) {
        validateActor(actor);
        validateEditable(findStage(stageId));
    }

    private Stage findStage(Long stageId) {
        return stageRepository.findById(stageId)
                .orElseThrow(() -> new StageNotFoundException("Stage not found."));
    }

    private void validateInitializable(Stage stage) {
        if (stage.getStatus() != StageStatus.READY && stage.getStatus() != StageStatus.IN_PROGRESS) {
            throw new InvalidStageResultException("StageResult can be initialized only when stage is READY or IN_PROGRESS.");
        }
    }

    private void validateEditable(Stage stage) {
        if (stage.getStatus() != StageStatus.IN_PROGRESS) {
            throw new InvalidStageResultException("StageResult can be updated only when stage is IN_PROGRESS.");
        }
    }

    private void validateUpdateRequest(StageResultUpdateRequest request) {
        if (request == null) {
            throw new InvalidStageResultException("StageResult update request is required.");
        }
        validateResultStatus(request.resultStatus());
        validateComment(request.comment());
    }

    private void validateBulkRequest(StageResultBulkUpdateRequest request) {
        if (request == null || request.results() == null || request.results().isEmpty()) {
            throw new InvalidStageResultException("StageResult bulk update items are required.");
        }
        Set<Long> requestedIds = new HashSet<>();
        for (StageResultBulkUpdateItemRequest item : request.results()) {
            if (item == null || item.stageResultId() == null) {
                throw new InvalidStageResultException("StageResult id is required.");
            }
            if (!requestedIds.add(item.stageResultId())) {
                throw new InvalidStageResultException("StageResult id is duplicated.");
            }
            validateResultStatus(item.resultStatus());
            validateComment(item.comment());
        }
    }

    private void validateResultStatus(StageResultStatus resultStatus) {
        if (resultStatus == null) {
            throw new InvalidStageResultException("StageResult status is required.");
        }
        if (resultStatus == StageResultStatus.PENDING) {
            throw new InvalidStageResultException("StageResult cannot be changed back to PENDING.");
        }
    }

    private void validateComment(String comment) {
        if (comment != null && comment.length() > 2000) {
            throw new InvalidStageResultException("StageResult comment must be 2000 characters or less.");
        }
    }

    private void validateActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidStageResultException("StageResult actor is required.");
        }
    }

    private Set<Long> findExistingApplicationIds(Long stageId, List<Long> applicationIds) {
        List<StageResult> existingResults = stageResultRepository.findByStageIdAndJobApplicationIdIn(stageId, applicationIds);
        Set<Long> existingApplicationIds = new HashSet<>();
        for (StageResult existingResult : existingResults) {
            existingApplicationIds.add(existingResult.getJobApplication().getId());
        }
        return existingApplicationIds;
    }
}
