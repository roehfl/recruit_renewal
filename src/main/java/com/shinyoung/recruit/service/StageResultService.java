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
    public AdminStageResultResponse updateResult(Long stageId, Long resultId, StageResultUpdateRequest request) {
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
                "SYSTEM"
        );
        return AdminStageResultResponse.from(stageResult);
    }

    @Transactional
    public StageResultBulkUpdateResponse bulkUpdateResults(Long stageId, StageResultBulkUpdateRequest request) {
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
                    "SYSTEM"
            );
        }

        return new StageResultBulkUpdateResponse(stageId, request.results().size(), getResults(stageId));
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

    private Set<Long> findExistingApplicationIds(Long stageId, List<Long> applicationIds) {
        List<StageResult> existingResults = stageResultRepository.findByStageIdAndJobApplicationIdIn(stageId, applicationIds);
        Set<Long> existingApplicationIds = new HashSet<>();
        for (StageResult existingResult : existingResults) {
            existingApplicationIds.add(existingResult.getJobApplication().getId());
        }
        return existingApplicationIds;
    }
}
