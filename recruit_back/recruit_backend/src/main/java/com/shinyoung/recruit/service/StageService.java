package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.StageCreateRequest;
import com.shinyoung.recruit.dto.request.StageOrderRequest;
import com.shinyoung.recruit.dto.request.StageReorderRequest;
import com.shinyoung.recruit.dto.request.StageUpdateRequest;
import com.shinyoung.recruit.dto.response.StageDetailResponse;
import com.shinyoung.recruit.dto.response.StageListResponse;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InvalidStageException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StageService {

    private final StageRepository stageRepository;
    private final JobPostingRepository jobPostingRepository;
    private final StageResultRepository stageResultRepository;
    private final ActivityLogService activityLogService;
    private final AuditRequestContextResolver auditRequestContextResolver;

    public List<StageListResponse> getStages(Long jobPostingId) {
        ensureJobPostingExists(jobPostingId);
        return stageRepository.findByJobPostingIdOrderByStageOrderAscIdAsc(jobPostingId).stream()
                .map(StageListResponse::from)
                .toList();
    }

    public StageDetailResponse getStage(Long jobPostingId, Long stageId) {
        ensureJobPostingExists(jobPostingId);
        Stage stage = findStage(jobPostingId, stageId);
        return StageDetailResponse.from(stage);
    }

    @Transactional
    public Long create(Long jobPostingId, StageCreateRequest request) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingEditable(jobPosting);
        validateStageRequest(
                request.stageName(),
                request.stageType(),
                request.stageOrder()
        );
        validateStageOrderForCreate(jobPostingId, request.stageOrder());
        validateFinalStageForCreate(jobPostingId, request.finalStage());

        Stage saved = stageRepository.save(Stage.create(
                jobPosting,
                request.stageName(),
                request.stageType(),
                request.stageOrder(),
                request.resultAnnouncementDateTime(),
                request.finalStage()
        ));
        return saved.getId();
    }

    @Transactional
    public Long update(Long jobPostingId, Long stageId, StageUpdateRequest request) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingEditable(jobPosting);

        Stage stage = findStage(jobPostingId, stageId);
        validateStageRequest(
                request.stageName(),
                request.stageType(),
                request.stageOrder()
        );

        if (stage.getStatus() == StageStatus.IN_PROGRESS) {
            // 진행 중 단계는 발표일시만 조정할 수 있다(발표일 연기 등 운영 필요). 나머지 필드는 현재 값과 같아야 한다.
            validateOnlyAnnouncementDateTimeChanged(stage, request);
            stage.updateResultAnnouncementDateTime(request.resultAnnouncementDateTime());
            return stage.getId();
        }

        validateStageEditable(stage);
        validateStageOrderForUpdate(jobPostingId, request.stageOrder(), stageId);
        validateFinalStageForUpdate(jobPostingId, request.finalStage(), stageId);

        stage.update(
                request.stageName(),
                request.stageType(),
                request.stageOrder(),
                request.resultAnnouncementDateTime(),
                request.finalStage()
        );
        return stage.getId();
    }

    @Transactional
    public List<StageListResponse> reorder(Long jobPostingId, StageReorderRequest request) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingEditable(jobPosting);

        List<Stage> stages = stageRepository.findByJobPostingIdOrderByStageOrderAscIdAsc(jobPostingId);
        validateReorderRequest(stages, request);

        Map<Long, Stage> stageMap = stages.stream()
                .collect(Collectors.toMap(Stage::getId, stage -> stage));
        for (StageOrderRequest item : request.items()) {
            stageMap.get(item.stageId()).reorder(item.stageOrder());
        }

        return stageRepository.findByJobPostingIdOrderByStageOrderAscIdAsc(jobPostingId).stream()
                .map(StageListResponse::from)
                .toList();
    }

    @Transactional
    public Long start(Long jobPostingId, Long stageId) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingPublishedForCommand(jobPosting);

        Stage stage = findStage(jobPostingId, stageId);
        validateStageStatus(stage, StageStatus.READY, "Only READY stage can be started.");
        stage.start();
        return stage.getId();
    }

    @Transactional
    public Long announce(Long jobPostingId, Long stageId) {
        return announce(jobPostingId, stageId, null);
    }

    /** @param actor 컨트롤러가 검증한 임직원 loginId(9b 리뷰 Low 1). null 이면 SecurityContext 에서 해석. */
    @Transactional
    public Long announce(Long jobPostingId, Long stageId, String actor) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingPublishedForCommand(jobPosting);

        Stage stage = findStage(jobPostingId, stageId);
        validateStageStatus(stage, StageStatus.IN_PROGRESS, "Only IN_PROGRESS stage can be announced.");
        validateStageResultsReadyForAnnounce(stageId);
        stage.announce();
        // 발표 = 커밋된 변경의 성공 증적(in-tx, ADR-0006 / Phase 09b).
        recordStageAudit(AuditActionType.STAGE_RESULT_ANNOUNCE, jobPostingId, stageId, actor);
        return stage.getId();
    }

    @Transactional
    public Long close(Long jobPostingId, Long stageId) {
        return close(jobPostingId, stageId, null);
    }

    /** @param actor 컨트롤러가 검증한 임직원 loginId(9b 리뷰 Low 1). null 이면 SecurityContext 에서 해석. */
    @Transactional
    public Long close(Long jobPostingId, Long stageId, String actor) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingPublishedForCommand(jobPosting);

        Stage stage = findStage(jobPostingId, stageId);
        validateStageStatus(stage, StageStatus.RESULT_ANNOUNCED, "Only RESULT_ANNOUNCED stage can be closed.");
        stage.close();
        // 확정(close) = 커밋된 변경의 성공 증적(in-tx, ADR-0006 / Phase 09b).
        recordStageAudit(AuditActionType.STAGE_RESULT_CONFIRM, jobPostingId, stageId, actor);
        return stage.getId();
    }

    @Transactional
    public Long delete(Long jobPostingId, Long stageId) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingEditable(jobPosting);

        Stage stage = findStage(jobPostingId, stageId);
        validateStageStatus(stage, StageStatus.READY, "Only READY stage can be deleted.");
        // READY 단계에 붙은 StageResult 는 "대상자 불러오기"가 만든 PENDING placeholder 뿐이다. 판정 write 경로
        // (updateResult/bulkUpdateResults)는 IN_PROGRESS 를 요구하고 상태는 되돌릴 수 없으므로 판정 데이터가 섞일 수
        // 없다. 대상자 단건 삭제 API 가 없어 차단하면 관리자가 빠져나갈 수 없으므로 단계와 함께 정리한다.
        // 삭제한 placeholder 는 단계를 다시 만든 뒤 initialize 로 동일하게 복원된다.
        stageResultRepository.deleteByStageId(stageId);
        stageRepository.delete(stage);
        return stageId;
    }

    private void recordStageAudit(AuditActionType actionType, Long jobPostingId, Long stageId, String actor) {
        AuditActorContext context = auditRequestContextResolver.resolve(actor);
        activityLogService.recordInCurrentTx(AuditEvent.builder()
                .actorType(context.actorType())
                .actorId(context.actorId())
                .actorRoleSnapshot(context.actorRoleSnapshot())
                .actionType(actionType)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.STAGE_RESULT)
                .targetId(String.valueOf(stageId))
                .jobPostingId(jobPostingId)
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }

    private void ensureJobPostingExists(Long jobPostingId) {
        if (!jobPostingRepository.existsById(jobPostingId)) {
            throw new JobPostingNotFoundException("JobPosting not found. id=" + jobPostingId);
        }
    }

    private JobPosting findJobPosting(Long jobPostingId) {
        return jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("JobPosting not found. id=" + jobPostingId));
    }

    private Stage findStage(Long jobPostingId, Long stageId) {
        return stageRepository.findByIdAndJobPostingId(stageId, jobPostingId)
                .orElseThrow(() -> new StageNotFoundException("Stage not found. id=" + stageId));
    }

    private void validateJobPostingEditable(JobPosting jobPosting) {
        if (jobPosting.getStatus() == JobPostingStatus.CLOSED) {
            throw new InvalidStageException("Closed JobPosting cannot be changed.");
        }
    }

    private void validateJobPostingPublishedForCommand(JobPosting jobPosting) {
        if (jobPosting.getStatus() != JobPostingStatus.PUBLISHED) {
            throw new InvalidStageException("Stage status command is allowed only for PUBLISHED JobPosting.");
        }
    }

    private void validateStageEditable(Stage stage) {
        if (stage.getStatus() != StageStatus.READY) {
            throw new InvalidStageException("Only READY stage can be changed.");
        }
    }

    private void validateOnlyAnnouncementDateTimeChanged(Stage stage, StageUpdateRequest request) {
        boolean lockedFieldChanged = !stage.getStageName().equals(request.stageName())
                || stage.getStageType() != request.stageType()
                || !stage.getStageOrder().equals(request.stageOrder())
                || stage.isFinalStage() != request.finalStage();
        if (lockedFieldChanged) {
            throw new InvalidStageException(
                    "In progress stage allows changing resultAnnouncementDateTime only.");
        }
    }

    private void validateStageStatus(Stage stage, StageStatus expectedStatus, String message) {
        if (stage.getStatus() != expectedStatus) {
            throw new InvalidStageException(message);
        }
    }

    private void validateStageResultsReadyForAnnounce(Long stageId) {
        if (stageResultRepository.countByStageId(stageId) == 0) {
            throw new InvalidStageException("StageResult must be initialized before announce.");
        }
        if (stageResultRepository.existsByStageIdAndResultStatus(stageId, StageResultStatus.PENDING)) {
            throw new InvalidStageException("StageResult has pending results.");
        }
    }

    private void validateStageRequest(String stageName, StageType stageType, Integer stageOrder) {
        if (stageName == null || stageName.isBlank()) {
            throw new InvalidStageException("Stage name is required.");
        }
        if (stageType == null) {
            throw new InvalidStageException("Stage type is required.");
        }
        if (stageOrder == null || stageOrder < 0) {
            throw new InvalidStageException("Stage order must be greater than or equal to 0.");
        }
    }

    private void validateStageOrderForCreate(Long jobPostingId, Integer stageOrder) {
        if (stageRepository.existsByJobPostingIdAndStageOrder(jobPostingId, stageOrder)) {
            throw new InvalidStageException("Stage order already exists.");
        }
    }

    private void validateStageOrderForUpdate(Long jobPostingId, Integer stageOrder, Long stageId) {
        if (stageRepository.existsByJobPostingIdAndStageOrderAndIdNot(jobPostingId, stageOrder, stageId)) {
            throw new InvalidStageException("Stage order already exists.");
        }
    }

    private void validateFinalStageForCreate(Long jobPostingId, boolean finalStage) {
        if (finalStage && stageRepository.existsByJobPostingIdAndFinalStageTrue(jobPostingId)) {
            throw new InvalidStageException("Final stage already exists.");
        }
    }

    private void validateFinalStageForUpdate(Long jobPostingId, boolean finalStage, Long stageId) {
        if (finalStage && stageRepository.existsByJobPostingIdAndFinalStageTrueAndIdNot(jobPostingId, stageId)) {
            throw new InvalidStageException("Final stage already exists.");
        }
    }

    private void validateReorderRequest(List<Stage> stages, StageReorderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new InvalidStageException("Stage reorder items are required.");
        }

        Map<Long, Stage> stageMap = stages.stream()
                .collect(Collectors.toMap(Stage::getId, stage -> stage));
        Set<Long> requestedIds = new java.util.HashSet<>();
        Set<Integer> requestedOrders = new java.util.HashSet<>();
        Map<Long, Integer> idCounts = new HashMap<>();

        for (StageOrderRequest item : request.items()) {
            if (item == null || item.stageId() == null) {
                throw new InvalidStageException("Stage id is required.");
            }
            if (item.stageOrder() == null || item.stageOrder() < 0) {
                throw new InvalidStageException("Stage order must be greater than or equal to 0.");
            }
            idCounts.merge(item.stageId(), 1, Integer::sum);
            if (!requestedOrders.add(item.stageOrder())) {
                throw new InvalidStageException("Stage order is duplicated.");
            }
            if (!stageMap.containsKey(item.stageId())) {
                throw new StageNotFoundException("Stage not found. id=" + item.stageId());
            }
            requestedIds.add(item.stageId());
        }

        boolean hasDuplicatedId = idCounts.values().stream().anyMatch(count -> count > 1);
        if (hasDuplicatedId) {
            throw new InvalidStageException("Stage id is duplicated.");
        }
        if (requestedIds.size() != stages.size()) {
            throw new InvalidStageException("Reorder request must include all stages.");
        }
        boolean hasNotReadyStage = stages.stream().anyMatch(stage -> stage.getStatus() != StageStatus.READY);
        if (hasNotReadyStage) {
            throw new InvalidStageException("Only READY stages can be reordered.");
        }
    }
}
