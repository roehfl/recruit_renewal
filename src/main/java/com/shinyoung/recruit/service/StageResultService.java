package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.dto.response.StageResultInitializeResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.exception.InvalidStageResultException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StageResultService {

    private final StageRepository stageRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final StageResultRepository stageResultRepository;

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

    private Stage findStage(Long stageId) {
        return stageRepository.findById(stageId)
                .orElseThrow(() -> new StageNotFoundException("Stage not found."));
    }

    private void validateInitializable(Stage stage) {
        if (stage.getStatus() != StageStatus.READY && stage.getStatus() != StageStatus.IN_PROGRESS) {
            throw new InvalidStageResultException("StageResult can be initialized only when stage is READY or IN_PROGRESS.");
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
