package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관리자 전형 결과 응답의 파생 필드(최종학력·직전 단계 결과·앞선 단계 코멘트)를 <b>단계 단위 배치 2회</b>로 채운다(N+1 없음).
 *
 * <p>한 번에 넘기는 결과들은 모두 같은 단계여야 한다(직전 단계를 첫 행의 단계로 판정한다). 호출부는
 * 단계 결과 목록·단건 판정·정정 응답이라 이 전제를 만족한다.
 *
 * <p>최종학력 판정(최고 EducationLevel, 동률이면 id 큰 행)은 {@code JobApplicationService.loadAdminSummaryEnrichments}와
 * 같은 규칙이다 — 지원현황 조회와 그리드가 다른 학교를 보여주면 안 된다.
 *
 * <p>단계 전체 결과를 한 번에 로드한다(그리드가 무페이징인 전제). 페이징을 도입하면 이 전제를 다시 본다.
 */
@Component
@RequiredArgsConstructor
public class AdminStageResultEnricher {

    private final StageRepository stageRepository;
    private final StageResultRepository stageResultRepository;
    private final ApplicationEducationRepository applicationEducationRepository;

    public AdminStageResultResponse toResponse(StageResult result) {
        return toResponses(List.of(result)).get(0);
    }

    public List<AdminStageResultResponse> toResponses(List<StageResult> results) {
        if (results.isEmpty()) {
            return List.of();
        }
        List<Long> applicationIds = results.stream()
                .map(result -> result.getJobApplication().getId())
                .distinct()
                .toList();
        Map<Long, ApplicationEducation> finalEducations = loadFinalEducations(applicationIds);
        PrecedingStageResults preceding = loadPrecedingStageResults(results.get(0).getStage(), applicationIds);

        return results.stream()
                .map(result -> {
                    Long applicationId = result.getJobApplication().getId();
                    ApplicationEducation education = finalEducations.get(applicationId);
                    return AdminStageResultResponse.from(result, new AdminStageResultResponse.Enrichment(
                            education == null ? null : education.getEducationLevel(),
                            education == null ? null : education.getSchoolName(),
                            preceding.previousStatuses().get(applicationId),
                            preceding.comments().getOrDefault(applicationId, List.of())));
                })
                .toList();
    }

    private Map<Long, ApplicationEducation> loadFinalEducations(List<Long> applicationIds) {
        Comparator<ApplicationEducation> finalEducationComparator = Comparator
                .comparingInt((ApplicationEducation education) -> education.getEducationLevel().ordinal())
                .thenComparing(ApplicationEducation::getId);
        return applicationEducationRepository.findByJobApplicationIdIn(applicationIds).stream()
                .collect(Collectors.toMap(
                        education -> education.getJobApplication().getId(),
                        education -> education,
                        (left, right) -> finalEducationComparator.compare(left, right) >= 0 ? left : right
                ));
    }

    /**
     * 같은 공고의 단계를 stageOrder 순으로 훑어 현재 단계보다 앞선 단계들을 찾고, 그 결과를 한 번에 조회한다.
     * 바로 앞 단계의 결과 상태와, 앞선 모든 단계의 코멘트(stageOrder 순, 빈 코멘트 제외)를 함께 만든다.
     */
    private PrecedingStageResults loadPrecedingStageResults(Stage stage, List<Long> applicationIds) {
        List<Stage> stages = stageRepository.findByJobPostingIdOrderByStageOrderAscIdAsc(stage.getJobPosting().getId());
        List<Stage> precedingStages = new ArrayList<>();
        boolean found = false;
        for (Stage candidate : stages) {
            if (candidate.getId().equals(stage.getId())) {
                found = true;
                break;
            }
            precedingStages.add(candidate);
        }
        // 현재 단계를 못 찾으면 모든 단계가 앞선 단계로 잡혀 엉뚱한 값을 내므로, 못 찾은 경우도 앞선 단계 없음으로 본다.
        if (!found || precedingStages.isEmpty()) {
            return new PrecedingStageResults(Map.of(), Map.of());
        }
        Map<Long, Integer> stageIndexes = new HashMap<>();
        for (int i = 0; i < precedingStages.size(); i++) {
            stageIndexes.put(precedingStages.get(i).getId(), i);
        }
        Long previousStageId = precedingStages.get(precedingStages.size() - 1).getId();

        List<StageResult> precedingResults = stageResultRepository
                .findByStageIdInAndJobApplicationIdIn(stageIndexes.keySet(), applicationIds).stream()
                .sorted(Comparator.comparing(result -> stageIndexes.get(result.getStage().getId())))
                .toList();
        Map<Long, StageResultStatus> previousStatuses = new HashMap<>();
        Map<Long, List<AdminStageResultResponse.PreviousStageComment>> comments = new HashMap<>();
        for (StageResult result : precedingResults) {
            Long applicationId = result.getJobApplication().getId();
            Long stageId = result.getStage().getId();
            if (stageId.equals(previousStageId)) {
                previousStatuses.put(applicationId, result.getResultStatus());
            }
            if (result.getComment() != null && !result.getComment().isBlank()) {
                comments.computeIfAbsent(applicationId, key -> new ArrayList<>())
                        .add(new AdminStageResultResponse.PreviousStageComment(
                                precedingStages.get(stageIndexes.get(stageId)).getStageName(),
                                result.getComment()));
            }
        }
        return new PrecedingStageResults(previousStatuses, comments);
    }

    private record PrecedingStageResults(
            Map<Long, StageResultStatus> previousStatuses,
            Map<Long, List<AdminStageResultResponse.PreviousStageComment>> comments
    ) {
    }
}
