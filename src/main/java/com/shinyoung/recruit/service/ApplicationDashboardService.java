package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.ApplicationDashboardResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationDashboardService {

    private final JobApplicationRepository jobApplicationRepository;
    private final StageResultRepository stageResultRepository;
    private final ApplicationCompletionReadChecker completionReadChecker;
    private final Clock clock;

    public ApplicationDashboardResponse getDashboard(Long applicantId, Long applicationId) {
        JobApplication application = jobApplicationRepository.findDashboardByIdAndApplicantId(applicationId, applicantId)
                .orElseThrow(() -> new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. id=" + applicationId));

        boolean accepting = isAccepting(application.getJobPosting());
        ApplicationCompletionReadChecker.CompletionReadinessResult completion = completionReadChecker.check(application);
        ApplicationResultSummary resultSummary = loadResultSummary(applicationId);

        boolean editable = application.getStatus() == JobApplicationStatus.DRAFT && accepting;
        boolean submittable = application.getStatus() == JobApplicationStatus.DRAFT
                && accepting
                && completion.summary().submitBlockingIssueCount() == 0;
        boolean withdrawable = application.getStatus() == JobApplicationStatus.SUBMITTED && accepting;

        return ApplicationDashboardResponse.from(
                application,
                accepting,
                editable,
                submittable,
                withdrawable,
                completion.summary(),
                completion.requiredMissingSections(),
                completion.optionalIncompleteSections(),
                resultSummary.latestAnnouncedStageName(),
                resultSummary.latestResultStatus()
        );
    }

    private boolean isAccepting(JobPosting jobPosting) {
        if (jobPosting.getStatus() != JobPostingStatus.PUBLISHED) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        return !now.isBefore(jobPosting.getReceptionStartDateTime())
                && !now.isAfter(jobPosting.getReceptionEndDateTime());
    }

    private ApplicationResultSummary loadResultSummary(Long applicationId) {
        List<StageResult> results = stageResultRepository.findVisibleByJobApplicationIdForApplicant(applicationId);
        return ApplicationResultSummary.from(results);
    }

    private record ApplicationResultSummary(
            String latestAnnouncedStageName,
            StageResultStatus latestResultStatus
    ) {
        private static final Comparator<StageResult> LATEST_RESULT_COMPARATOR =
                Comparator
                        .comparing((StageResult result) -> result.getStage().getStageOrder())
                        .thenComparing(result -> result.getStage().getId());

        static ApplicationResultSummary empty() {
            return new ApplicationResultSummary(null, null);
        }

        static ApplicationResultSummary from(List<StageResult> results) {
            return results.stream()
                    .max(LATEST_RESULT_COMPARATOR)
                    .map(result -> new ApplicationResultSummary(
                            result.getStage().getStageName(),
                            result.getResultStatus()
                    ))
                    .orElseGet(ApplicationResultSummary::empty);
        }
    }
}
