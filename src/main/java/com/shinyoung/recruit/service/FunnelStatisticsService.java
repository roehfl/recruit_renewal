package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.School;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.SchoolRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.DimensionFunnelResponse;
import com.shinyoung.recruit.dto.response.FunnelCertificateRow;
import com.shinyoung.recruit.dto.response.FunnelCohortRow;
import com.shinyoung.recruit.dto.response.FunnelPopulationResponse;
import com.shinyoung.recruit.dto.response.FunnelResponse;
import com.shinyoung.recruit.dto.response.FunnelSchoolEducationRow;
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

    private static final int DEFAULT_DIMENSION_TOP_N = 10;
    private static final int MAX_DIMENSION_TOP_N = 100;
    private static final String OTHER_GROUP_NAME = "기타";

    private final JobPostingRepository jobPostingRepository;
    private final StageRepository stageRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final StageResultRepository stageResultRepository;
    private final ApplicationEducationRepository educationRepository;
    private final SchoolRepository schoolRepository;
    private final ApplicationCertificateRepository certificateRepository;

    public FunnelResponse getFunnel(Long jobPostingId, String dimensionParam, Integer topN) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + jobPostingId));
        FunnelDimension dimension = parseSupportedDimension(dimensionParam);

        List<Stage> stages = stageRepository.findByJobPostingIdOrderByStageOrderAscIdAsc(jobPostingId);
        List<FunnelCohortRow> cohort = jobApplicationRepository.findFunnelCohort(jobPostingId);
        Map<Long, Map<Long, StageResultStatus>> resultsByStage = indexResults(
                stageResultRepository.findFunnelStageResults(jobPostingId));

        CohortFunnel overall = computeCohort(stages, cohort, resultsByStage);

        // switch expression으로 exhaustiveness를 강제한다 — FunnelDimension에 새 값이 추가되면 컴파일 에러로 dispatch 누락을 막는다.
        List<DimensionFunnelResponse> dimensions = dimension == null
                ? List.of()
                : switch (dimension) {
            case POSITION -> computePositionDimension(stages, cohort, resultsByStage);
            case SCHOOL -> computeSchoolDimension(stages, cohort, resultsByStage, jobPostingId, topN);
            case CERTIFICATE -> computeCertificateDimension(stages, cohort, resultsByStage, jobPostingId, topN);
        };

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
     * 학교별 dimension: 지원자별 "최종학력(가장 높은 EducationLevel) 1교"의 {@code schoolId}로 코호트를 분할한다.
     * 미매칭(최종학력에 schoolId 없음/학력 없음) 및 dangling schoolId(School 테이블에 없음)는 '기타'로 모으고,
     * 학교 그룹은 인원 desc·schoolId asc로 정렬해 topN(기본 10)만 개별 노출하며, 초과 학교 + 미매칭은 '기타' 한
     * 그룹으로 합산한다(application 단위 distinct). 개별 그룹은 항상 실재 학교라 groupName 이 null 이 되지 않는다.
     */
    private List<DimensionFunnelResponse> computeSchoolDimension(
            List<Stage> stages,
            List<FunnelCohortRow> cohort,
            Map<Long, Map<Long, StageResultStatus>> resultsByStage,
            Long jobPostingId,
            Integer topN
    ) {
        Map<Long, Long> schoolByApplication = finalSchoolByApplication(jobPostingId);

        // 실재 School 만 그룹 키로 사용한다. dangling schoolId(삭제/오타 등 School 미존재)는 '기타'로 합산한다.
        Set<Long> candidateSchoolIds = schoolByApplication.values().stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nameById = candidateSchoolIds.isEmpty()
                ? Map.of()
                : schoolRepository.findAllById(candidateSchoolIds).stream()
                        .collect(Collectors.toMap(School::getId, School::getSchoolName));

        Map<Long, List<FunnelCohortRow>> bySchool = new LinkedHashMap<>();
        List<FunnelCohortRow> unmatched = new ArrayList<>();
        for (FunnelCohortRow row : cohort) {
            Long schoolId = schoolByApplication.get(row.applicationId());
            if (schoolId == null || !nameById.containsKey(schoolId)) {
                unmatched.add(row); // 미매칭 또는 dangling → 기타
            } else {
                bySchool.computeIfAbsent(schoolId, key -> new ArrayList<>()).add(row);
            }
        }

        List<Map.Entry<Long, List<FunnelCohortRow>>> ranked = bySchool.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<Long, List<FunnelCohortRow>>>comparingInt(entry -> entry.getValue().size())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();

        int limit = (topN == null || topN <= 0) ? DEFAULT_DIMENSION_TOP_N : Math.min(topN, MAX_DIMENSION_TOP_N);
        List<Map.Entry<Long, List<FunnelCohortRow>>> top = ranked.stream().limit(limit).toList();
        List<Map.Entry<Long, List<FunnelCohortRow>>> overflow = ranked.stream().skip(limit).toList();

        List<DimensionFunnelResponse> dimensions = new ArrayList<>();
        for (Map.Entry<Long, List<FunnelCohortRow>> entry : top) {
            CohortFunnel funnel = computeCohort(stages, entry.getValue(), resultsByStage);
            dimensions.add(new DimensionFunnelResponse(
                    entry.getKey(), nameById.get(entry.getKey()), funnel.population(), funnel.stages()));
        }

        List<FunnelCohortRow> other = new ArrayList<>(unmatched);
        overflow.forEach(entry -> other.addAll(entry.getValue()));
        if (!other.isEmpty()) {
            CohortFunnel funnel = computeCohort(stages, other, resultsByStage);
            dimensions.add(new DimensionFunnelResponse(
                    null, OTHER_GROUP_NAME, funnel.population(), funnel.stages()));
        }
        return dimensions;
    }

    /**
     * 자격별 dimension: 자격명(정규화: trim + 공백 압축)별 <strong>보유 지원자 distinct</strong> 그룹. SCHOOL/POSITION 과
     * 달리 그룹이 P 의 분할이 아니라 <strong>중복 가능</strong>하다(한 지원자가 여러 자격을 보유하면 여러 그룹에 집계).
     * 자격 보유자 수 desc·자격명 asc로 정렬해 topN(기본 10)만 개별 노출하고, 초과 자격 보유자(distinct)는 '기타'로
     * 합산한다. 빈 자격명/무보유 지원자는 그룹에 포함하지 않는다(보유 의미). 자격명은 free-text master 가 없어 정규화로만 묶는다.
     */
    private List<DimensionFunnelResponse> computeCertificateDimension(
            List<Stage> stages,
            List<FunnelCohortRow> cohort,
            Map<Long, Map<Long, StageResultStatus>> resultsByStage,
            Long jobPostingId,
            Integer topN
    ) {
        Map<Long, FunnelCohortRow> cohortById = cohort.stream()
                .collect(Collectors.toMap(FunnelCohortRow::applicationId, java.util.function.Function.identity(),
                        (existing, ignored) -> existing));

        Map<String, Set<Long>> holdersByName = new LinkedHashMap<>();
        for (FunnelCertificateRow row : certificateRepository.findFunnelCertificates(jobPostingId)) {
            String name = normalizeCertificateName(row.certificateName());
            if (name == null || !cohortById.containsKey(row.applicationId())) {
                continue;
            }
            holdersByName.computeIfAbsent(name, key -> new HashSet<>()).add(row.applicationId());
        }

        List<Map.Entry<String, Set<Long>>> ranked = holdersByName.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, Set<Long>>>comparingInt(entry -> entry.getValue().size())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();

        int limit = (topN == null || topN <= 0) ? DEFAULT_DIMENSION_TOP_N : Math.min(topN, MAX_DIMENSION_TOP_N);
        List<Map.Entry<String, Set<Long>>> top = ranked.stream().limit(limit).toList();
        List<Map.Entry<String, Set<Long>>> overflow = ranked.stream().skip(limit).toList();

        List<DimensionFunnelResponse> dimensions = new ArrayList<>();
        for (Map.Entry<String, Set<Long>> entry : top) {
            CohortFunnel funnel = computeCohort(stages, rowsOf(entry.getValue(), cohortById), resultsByStage);
            dimensions.add(new DimensionFunnelResponse(null, entry.getKey(), funnel.population(), funnel.stages()));
        }

        if (!overflow.isEmpty()) {
            Set<Long> otherApplicationIds = new HashSet<>();
            overflow.forEach(entry -> otherApplicationIds.addAll(entry.getValue()));
            CohortFunnel funnel = computeCohort(stages, rowsOf(otherApplicationIds, cohortById), resultsByStage);
            dimensions.add(new DimensionFunnelResponse(null, OTHER_GROUP_NAME, funnel.population(), funnel.stages()));
        }
        return dimensions;
    }

    private List<FunnelCohortRow> rowsOf(Set<Long> applicationIds, Map<Long, FunnelCohortRow> cohortById) {
        return applicationIds.stream()
                .map(cohortById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** free-text 자격명을 그룹 키로 정규화: trim + 내부 공백 압축. 빈 값은 null(그룹 제외). */
    private static String normalizeCertificateName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    /** 지원자별 최종학력 1교의 schoolId. 학력 없음/최종학력에 schoolId 없음이면 매핑 없음(미매칭). */
    private Map<Long, Long> finalSchoolByApplication(Long jobPostingId) {
        Map<Long, FunnelSchoolEducationRow> best = new HashMap<>();
        for (FunnelSchoolEducationRow row : educationRepository.findFunnelSchoolEducations(jobPostingId)) {
            best.merge(row.applicationId(), row, this::pickFinalEducation);
        }
        Map<Long, Long> schoolByApplication = new HashMap<>();
        best.forEach((applicationId, row) -> schoolByApplication.put(applicationId, row.schoolId()));
        return schoolByApplication;
    }

    /** 최종학력 선택: 더 높은 educationLevel 우선, 동률이면 schoolId 있는 쪽 우선. */
    private FunnelSchoolEducationRow pickFinalEducation(FunnelSchoolEducationRow a, FunnelSchoolEducationRow b) {
        int levelCompare = Integer.compare(levelRank(a), levelRank(b));
        if (levelCompare != 0) {
            return levelCompare > 0 ? a : b;
        }
        boolean aHasSchool = a.schoolId() != null;
        boolean bHasSchool = b.schoolId() != null;
        if (aHasSchool != bHasSchool) {
            return aHasSchool ? a : b;
        }
        return a;
    }

    private int levelRank(FunnelSchoolEducationRow row) {
        return row.educationLevel() == null ? -1 : row.educationLevel().ordinal();
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
