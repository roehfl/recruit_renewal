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
        validateStageEditable(stage);
        validateStageRequest(
                request.stageName(),
                request.stageType(),
                request.stageOrder()
        );
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
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingPublishedForCommand(jobPosting);

        Stage stage = findStage(jobPostingId, stageId);
        validateStageStatus(stage, StageStatus.IN_PROGRESS, "Only IN_PROGRESS stage can be announced.");
        validateStageResultsReadyForAnnounce(stageId);
        stage.announce();
        return stage.getId();
    }

    @Transactional
    public Long close(Long jobPostingId, Long stageId) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingPublishedForCommand(jobPosting);

        Stage stage = findStage(jobPostingId, stageId);
        validateStageStatus(stage, StageStatus.RESULT_ANNOUNCED, "Only RESULT_ANNOUNCED stage can be closed.");
        stage.close();
        return stage.getId();
    }

    @Transactional
    public Long delete(Long jobPostingId, Long stageId) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateJobPostingEditable(jobPosting);

        Stage stage = findStage(jobPostingId, stageId);
        validateStageStatus(stage, StageStatus.READY, "Only READY stage can be deleted.");
        stageRepository.delete(stage);
        return stageId;
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
