package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.DimensionFunnelResponse;
import com.shinyoung.recruit.dto.response.FunnelCohortRow;
import com.shinyoung.recruit.dto.response.FunnelPopulationResponse;
import com.shinyoung.recruit.dto.response.FunnelResponse;
import com.shinyoung.recruit.dto.response.FunnelStageResultRow;
import com.shinyoung.recruit.dto.response.StageDistributionResponse;
import com.shinyoung.recruit.dto.response.StageFunnelResponse;
import com.shinyoung.recruit.enumeration.FunnelDimension;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.exception.InvalidStatisticsRequestException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 공고 단위 전형 funnel 통계 산출(read-only, 집계값만).
 *
 * <p>모집단 P = 제출 이력(submittedAt != null) 보유 지원서 코호트. 각 stage에서 P 전체를 raw 7-bucket
 * (PASSED/FAILED/ABSENT/HOLD/PENDING/WITHDRAWN + synthetic NO_RESULT)으로 분류(합=|P|)하고, 순차 통과
 * 집합 S_k = S_(k-1) ∩ {stage k PASSED}로 funnelPassedCount·두 비율을 계산한다. overall은 항상,
 * dimension=POSITION이면 분야별 그룹 funnel을 추가로 산출한다. statistics는 audit를 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FunnelStatisticsService {

    private final JobPostingRepository jobPostingRepository;
    private final StageRepository stageRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final StageResultRepository stageResultRepository;

    public FunnelResponse getFunnel(Long jobPostingId, String dimensionParam, Integer topN) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + jobPostingId));
        FunnelDimension dimension = parseSupportedDimension(dimensionParam);

        List<Stage> stages = stageRepository.findByJobPostingIdOrderByStageOrderAscIdAsc(jobPostingId);
        List<FunnelCohortRow> cohort = jobApplicationRepository.findFunnelCohort(jobPostingId);
        Map<Long, Map<Long, StageResultStatus>> resultsByStage = indexResults(
                stageResultRepository.findFunnelStageResults(jobPostingId));

        CohortFunnel overall = computeCohort(stages, cohort, resultsByStage);

        List<DimensionFunnelResponse> dimensions = List.of();
        if (dimension == FunnelDimension.POSITION) {
            dimensions = computePositionDimension(stages, cohort, resultsByStage);
        }

        return new FunnelResponse(
                jobPostingId,
                resolveTitle(jobPosting),
                dimension,
                overall.population(),
                overall.stages(),
                dimensions
        );
    }

    private List<DimensionFunnelResponse> computePositionDimension(
            List<Stage> stages,
            List<FunnelCohortRow> cohort,
            Map<Long, Map<Long, StageResultStatus>> resultsByStage
    ) {
        Map<Long, List<FunnelCohortRow>> byPosition = cohort.stream()
                .collect(Collectors.groupingBy(FunnelCohortRow::jobPositionId, LinkedHashMap::new, Collectors.toList()));

        // 운영 "분야별 통계"이므로 공고에 등록된 모집분야 순서(JobPosition.sortOrder)대로, 동률은 id로 정렬한다.
        return byPosition.values().stream()
                .sorted(Comparator
                        .comparing((List<FunnelCohortRow> group) -> group.get(0).jobPositionSortOrder(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(group -> group.get(0).jobPositionId()))
                .map(group -> {
                    CohortFunnel funnel = computeCohort(stages, group, resultsByStage);
                    return new DimensionFunnelResponse(
                            group.get(0).jobPositionId(),
                            group.get(0).jobPositionName(),
                            funnel.population(),
                            funnel.stages()
                    );
                })
                .toList();
    }

    /**
     * 코호트(전체 P 또는 dimension 그룹) 하나의 funnel을 산출한다. distribution은 코호트 전체에 대한 raw
     * 분포이고, funnelPassedCount는 순차 통과 집합 기준이라 서로 다를 수 있다.
     */
    private CohortFunnel computeCohort(
            List<Stage> stages,
            List<FunnelCohortRow> cohort,
            Map<Long, Map<Long, StageResultStatus>> resultsByStage
    ) {
        long p = cohort.size();
        long currentlySubmittedCount = cohort.stream()
                .filter(row -> row.status() == JobApplicationStatus.SUBMITTED)
                .count();
        long withdrawnCount = cohort.stream()
                .filter(row -> row.status() == JobApplicationStatus.WITHDRAWN)
                .count();
        List<Long> applicationIds = cohort.stream().map(FunnelCohortRow::applicationId).toList();

        Set<Long> survivors = new HashSet<>(applicationIds); // S0 = P
        long previousSurvivorCount = p;

        List<StageFunnelResponse> stageResponses = new ArrayList<>();
        for (Stage stage : stages) {
            Map<Long, StageResultStatus> resultMap = resultsByStage.getOrDefault(stage.getId(), Map.of());

            StageDistributionResponse distribution = distribution(applicationIds, resultMap);

            Set<Long> nextSurvivors = new HashSet<>();
            for (Long applicationId : survivors) {
                if (resultMap.get(applicationId) == StageResultStatus.PASSED) {
                    nextSurvivors.add(applicationId);
                }
            }
            long funnelPassedCount = nextSurvivors.size();
            double cumulativeRate = p == 0 ? 0.0 : (double) funnelPassedCount / p;
            double stepConversionRate = previousSurvivorCount == 0
                    ? 0.0
                    : (double) funnelPassedCount / previousSurvivorCount;

            stageResponses.add(new StageFunnelResponse(
                    stage.getStageOrder(),
                    stage.getId(),
                    stage.getStageName(),
                    stage.getStageType(),
                    distribution,
                    funnelPassedCount,
                    cumulativeRate,
                    stepConversionRate
            ));

            survivors = nextSurvivors;
            previousSurvivorCount = funnelPassedCount;
        }

        return new CohortFunnel(
                new FunnelPopulationResponse(p, currentlySubmittedCount, withdrawnCount),
                stageResponses
        );
    }

    private StageDistributionResponse distribution(List<Long> applicationIds, Map<Long, StageResultStatus> resultMap) {
        long passed = 0;
        long failed = 0;
        long absent = 0;
        long hold = 0;
        long pending = 0;
        long withdrawn = 0;
        long noResult = 0;
        for (Long applicationId : applicationIds) {
            StageResultStatus status = resultMap.get(applicationId);
            if (status == null) {
                noResult++;
                continue;
            }
            switch (status) {
                case PASSED -> passed++;
                case FAILED -> failed++;
                case ABSENT -> absent++;
                case HOLD -> hold++;
                case PENDING -> pending++;
                case WITHDRAWN -> withdrawn++;
            }
        }
        return new StageDistributionResponse(passed, failed, absent, hold, pending, withdrawn, noResult);
    }

    private Map<Long, Map<Long, StageResultStatus>> indexResults(List<FunnelStageResultRow> rows) {
        Map<Long, Map<Long, StageResultStatus>> byStage = new HashMap<>();
        for (FunnelStageResultRow row : rows) {
            byStage.computeIfAbsent(row.stageId(), key -> new HashMap<>())
                    .put(row.applicationId(), row.resultStatus());
        }
        return byStage;
    }

    private FunnelDimension parseSupportedDimension(String dimensionParam) {
        if (dimensionParam == null || dimensionParam.isBlank()) {
            return null;
        }
        FunnelDimension dimension;
        try {
            dimension = FunnelDimension.valueOf(dimensionParam.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidStatisticsRequestException("지원하지 않는 dimension 값입니다. dimension=" + dimensionParam);
        }
        if (dimension != FunnelDimension.POSITION) {
            throw new InvalidStatisticsRequestException(
                    "dimension=" + dimension + "은(는) 아직 지원하지 않습니다. 현재 POSITION만 지원합니다.");
        }
        return dimension;
    }

    private String resolveTitle(JobPosting jobPosting) {
        return jobPosting.getTitle();
    }

    private record CohortFunnel(
            FunnelPopulationResponse population,
            List<StageFunnelResponse> stages
    ) {
    }
}
