package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.entity.StageResultCorrectionHistory;
import com.shinyoung.recruit.domain.repository.StageResultCorrectionHistoryRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.StageResultCorrectionRequest;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.dto.response.StageResultCorrectionHistoryResponse;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.exception.InvalidStageResultException;
import com.shinyoung.recruit.exception.StageResultNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StageResultCorrectionService {

    private final StageResultRepository stageResultRepository;
    private final StageResultCorrectionHistoryRepository correctionHistoryRepository;
    private final Clock clock;
    private final AdminStageResultEnricher enricher;

    @Transactional
    public AdminStageResultResponse correctResult(
            Long stageId,
            Long resultId,
            StageResultCorrectionRequest request,
            String actor
    ) {
        validateActor(actor);
        validateCorrectionRequest(request);
        StageResult stageResult = findStageResult(stageId, resultId);
        validateCorrectable(stageResult.getStage());

        LocalDateTime correctedAt = LocalDateTime.now(clock);
        StageResultCorrectionHistory history = StageResultCorrectionHistory.create(
                stageResult,
                correctedAt,
                actor,
                request.reason(),
                stageResult.getResultStatus(),
                request.resultStatus(),
                stageResult.getScore(),
                request.score(),
                stageResult.getComment(),
                request.comment(),
                stageResult.getDecidedAt(),
                correctedAt
        );

        stageResult.updateResult(
                request.resultStatus(),
                request.score(),
                request.comment(),
                correctedAt,
                actor
        );
        correctionHistoryRepository.save(history);

        return enricher.toResponse(stageResult);
    }

    public List<StageResultCorrectionHistoryResponse> getHistories(Long stageId, Long resultId) {
        findStageResult(stageId, resultId);
        return correctionHistoryRepository.findByStageResultIdOrderByCorrectedAtDescIdDesc(resultId).stream()
                .map(StageResultCorrectionHistoryResponse::from)
                .toList();
    }

    private StageResult findStageResult(Long stageId, Long resultId) {
        return stageResultRepository.findByIdAndStageId(resultId, stageId)
                .orElseThrow(() -> new StageResultNotFoundException("StageResult not found."));
    }

    private void validateCorrectable(Stage stage) {
        if (stage.getStatus() != StageStatus.RESULT_ANNOUNCED && stage.getStatus() != StageStatus.CLOSED) {
            throw new InvalidStageResultException("StageResult correction is allowed only when stage is RESULT_ANNOUNCED or CLOSED.");
        }
    }

    private void validateCorrectionRequest(StageResultCorrectionRequest request) {
        if (request == null) {
            throw new InvalidStageResultException("StageResult correction request is required.");
        }
        validateResultStatus(request.resultStatus());
        validateComment(request.comment());
        validateReason(request.reason());
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

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidStageResultException("Correction reason is required.");
        }
        if (reason.length() > 1000) {
            throw new InvalidStageResultException("Correction reason must be 1000 characters or less.");
        }
    }

    private void validateActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidStageResultException("StageResult correction actor is required.");
        }
    }
}
