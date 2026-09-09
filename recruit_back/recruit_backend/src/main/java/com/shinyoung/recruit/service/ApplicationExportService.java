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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Applications 목록을 Excel(xlsx)로 내보내는 서비스.
 *
 * <p>대응 list 엔드포인트와 <b>동일한 검색 조건</b>을 쓰되 page/size는 무시한다. 조건 생성은
 * {@link AdminApplicationSearchConditionFactory}를 공유하고 조회 절은 repository 의 {@code ADMIN_SEARCH_WHERE}
 * 상수를 공유하므로, 목록에서 보이는 결과와 내려받은 파일의 대상이 어긋나지 않는다.
 * 생성 전 count 선검증으로 row cap을 강제하고, 초과 시 workbook을 만들지 않는다.
 * projection DTO를 page 스트림으로 읽어 SXSSF로 temp 파일을 생성하며, JPA entity/lazy를 writer에
 * 넘기지 않는다. temp 파일은 controller가 전송 후 삭제한다(service는 삭제하지 않는다).
 */
@Service
@RequiredArgsConstructor
public class ApplicationExportService {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final ExcelExportSpec<ApplicationExportRow> APPLICATIONS_SPEC = new ExcelExportSpec<>(
            "applications",
            List.of(
                    new ExportColumn<>("applicationId", row -> toText(row.applicationId())),
                    new ExportColumn<>("applicantName", ApplicationExportRow::applicantName),
                    new ExportColumn<>("phoneNumber", ApplicationExportRow::phoneNumber),
                    new ExportColumn<>("email", ApplicationExportRow::email),
                    new ExportColumn<>("jobPostingTitle", ApplicationExportRow::jobPostingTitle),
                    new ExportColumn<>("jobPositionName", ApplicationExportRow::jobPositionName),
                    new ExportColumn<>("status", row -> row.status() == null ? "" : row.status().name()),
                    new ExportColumn<>("submittedAt", row -> toText(row.submittedAt())),
                    new ExportColumn<>("withdrawnAt", row -> toText(row.withdrawnAt())),
                    new ExportColumn<>("createdAt", row -> toText(row.createdAt())),
                    new ExportColumn<>("updatedAt", row -> toText(row.updatedAt()))
            )
    );

    private final JobApplicationRepository jobApplicationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ExcelExportWriter excelExportWriter;
    private final ExportProperties exportProperties;
    private final AdminApplicationSearchConditionFactory searchConditionFactory;

    @Transactional(readOnly = true)
    public ExcelExportFile exportApplications(Long jobPostingId, AdminApplicationSearchRequest request) {
        if (jobPostingId != null && !jobPostingRepository.existsById(jobPostingId)) {
            throw new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + jobPostingId);
        }
        AdminApplicationSearchCondition condition = searchConditionFactory.create(jobPostingId, request);

        long total = countExportApplications(condition);
        long maxRows = exportProperties.getMaxRows();
        if (total > maxRows) {
            throw new ExportRowLimitExceededException(total, maxRows);
        }

        // try 범위는 writer 호출 이후로 좁힌다. 위의 검증 예외(row cap/not found/invalid status)는
        // export generation 실패로 감싸지 않고 그대로 전파한다.
        try {
            Path tempFile = excelExportWriter.writeToTempFile(
                    APPLICATIONS_SPEC,
                    (page, size) -> findExportApplications(condition, PageRequest.of(page, size))
            );
            return new ExcelExportFile(tempFile, buildFileName(jobPostingId), total);
        } catch (IOException | RuntimeException e) {
            throw new ExportGenerationException("applications export 파일 생성 실패", e);
        }
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

    private static String toText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(TIMESTAMP);
        }
        return String.valueOf(value);
    }
}
