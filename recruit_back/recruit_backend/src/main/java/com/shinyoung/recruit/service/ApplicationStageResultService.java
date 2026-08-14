package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.ApplicantStageResultResponse;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationStageResultService {

    private final JobApplicationRepository jobApplicationRepository;
    private final StageResultRepository stageResultRepository;

    public List<ApplicantStageResultResponse> getApplicantStageResults(Long applicantId, Long applicationId) {
        JobApplication application = jobApplicationRepository.findByIdAndApplicantId(applicationId, applicantId)
                .orElseThrow(() -> new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. id=" + applicationId));
        validateReadable(application);

        return stageResultRepository.findVisibleByJobApplicationIdForApplicant(application.getId()).stream()
                .map(ApplicantStageResultResponse::from)
                .toList();
    }

    private void validateReadable(JobApplication application) {
        if (application.getStatus() == JobApplicationStatus.DRAFT) {
            throw new InvalidJobApplicationException("임시저장 지원서는 전형 결과를 조회할 수 없습니다.");
        }
    }
}
