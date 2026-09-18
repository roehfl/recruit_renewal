package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.ExportProperties;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.condition.AdminApplicationSearchCondition;
import com.shinyoung.recruit.dto.request.AdminApplicationSearchRequest;
import com.shinyoung.recruit.dto.response.ApplicationExportRow;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.exception.ExportGenerationException;
import com.shinyoung.recruit.exception.ExportRowLimitExceededException;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Applications 목록을 Excel(xlsx)로 내보내는 서비스.
 *
 * <p>대응 list 엔드포인트와 <b>동일한 검색 조건</b>을 쓰되 page/size는 무시한다. 조건 생성은
 * {@link AdminApplicationSearchConditionFactory}를 공유하고 조회 절은 repository 의 {@code ADMIN_SEARCH_WHERE}
 * 상수를 공유하므로, 목록에서 보이는 결과와 내려받은 파일의 대상이 어긋나지 않는다.
 * 컬럼은 호출자가 고른 {@link ApplicationExportColumn} 목록(카탈로그 순)이고, 셀 값은
 * {@link ApplicationExportRowAssembler} 가 페이지 단위로 만든다.
 * 생성 전 count 선검증으로 row cap을 강제하고, 초과 시 workbook을 만들지 않는다.
 * projection DTO를 page 스트림으로 읽어 SXSSF로 temp 파일을 생성하며, JPA entity/lazy를 writer에
 * 넘기지 않는다. temp 파일은 controller가 전송 후 삭제한다(service는 삭제하지 않는다).
 * 페이지마다 영속성 컨텍스트를 비운다(assembler) — 같은 요청에서 호출 전에 로드한 entity 를 호출 후
 * 재사용하지 말 것(OSIV 포함).
 */
@Service
@RequiredArgsConstructor
public class ApplicationExportService {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ExcelExportWriter excelExportWriter;
    private final ExportProperties exportProperties;
    private final AdminApplicationSearchConditionFactory searchConditionFactory;
    private final ApplicationExportRowAssembler rowAssembler;

    @Transactional(readOnly = true)
    public ExcelExportFile exportApplications(
            Long jobPostingId,
            AdminApplicationSearchRequest request,
            List<ApplicationExportColumn> columns
    ) {
        if (jobPostingId != null && !jobPostingRepository.existsById(jobPostingId)) {
            throw new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + jobPostingId);
        }
        AdminApplicationSearchCondition condition = searchConditionFactory.create(jobPostingId, request);

        long total = countExportApplications(condition);
        long maxRows = exportProperties.getMaxRows();
        if (total > maxRows) {
            throw new ExportRowLimitExceededException(total, maxRows);
        }

        ExcelExportSpec<Map<ApplicationExportColumn, String>> spec = buildSpec(columns);
        CommonCodeNames codeNames = rowAssembler.newCodeNames();
        // try 범위는 writer 호출 이후로 좁힌다. 위의 검증 예외(row cap/not found/invalid status)는
        // export generation 실패로 감싸지 않고 그대로 전파한다.
        try {
            Path tempFile = excelExportWriter.writeToTempFile(
                    spec,
                    (page, size) -> rowAssembler.assemble(
                            findExportApplications(condition, PageRequest.of(page, size)), columns, codeNames)
            );
            return new ExcelExportFile(tempFile, buildFileName(jobPostingId), total);
        } catch (IOException | RuntimeException e) {
            throw new ExportGenerationException("applications export 파일 생성 실패", e);
        }
    }

    /** 선택 컬럼(카탈로그 순)으로 시트를 정의한다. 헤더 = 카탈로그 라벨, 1:N 요약 컬럼은 셀 줄바꿈. */
    private static ExcelExportSpec<Map<ApplicationExportColumn, String>> buildSpec(List<ApplicationExportColumn> columns) {
        return new ExcelExportSpec<>(
                "applications",
                columns.stream()
                        .map(column -> new ExportColumn<Map<ApplicationExportColumn, String>>(
                                column.label(), values -> values.get(column), false, column.wrapText()))
                        .toList());
    }

    private long countExportApplications(AdminApplicationSearchCondition condition) {
        return jobApplicationRepository.countExportApplications(
                condition.jobPostingId(),
                condition.jobPositionId(),
                condition.status(),
                condition.applicationType(),
                condition.workLocation(),
                condition.name(),
                condition.phoneNumber(),
                condition.birthDateFrom(),
                condition.birthDateTo(),
                condition.finalEducationRank(),
                condition.schoolName(),
                condition.graduationStatus(),
                condition.finalSchoolConditionName(),
                condition.certificateName(),
                condition.languageName(),
                condition.languageLevel(),
                condition.stageType(),
                condition.stageResultStatus());
    }

    private List<ApplicationExportRow> findExportApplications(
            AdminApplicationSearchCondition condition,
            PageRequest pageRequest
    ) {
        return jobApplicationRepository.findExportApplications(
                condition.jobPostingId(),
                condition.jobPositionId(),
                condition.status(),
                condition.applicationType(),
                condition.workLocation(),
                condition.name(),
                condition.phoneNumber(),
                condition.birthDateFrom(),
                condition.birthDateTo(),
                condition.finalEducationRank(),
                condition.schoolName(),
                condition.graduationStatus(),
                condition.finalSchoolConditionName(),
                condition.certificateName(),
                condition.languageName(),
                condition.languageLevel(),
                condition.stageType(),
                condition.stageResultStatus(),
                pageRequest);
    }

    private String buildFileName(Long jobPostingId) {
        if (jobPostingId != null) {
            return "applications-export-job-posting-" + jobPostingId + ".xlsx";
        }
        return "applications-export.xlsx";
    }

    /**
     * status 입력의 canonical 파싱(trim + uppercase + enum 검증). audit filter 에는 raw 입력이 아니라
     * 이 canonical 값을 남긴다(9b 리뷰 Medium 2 — raw string 의 제어문자/변조 문자열이 audit 에 새는 것 방지).
     */
    public JobApplicationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return JobApplicationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidJobApplicationException("지원서 상태 값이 올바르지 않습니다. status=" + status);
        }
    }
}
