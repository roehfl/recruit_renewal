package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.repository.InterviewEvaluationRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.dto.response.AdminInterviewSummaryResponse;
import com.shinyoung.recruit.dto.response.AdminStageResultResponse;
import com.shinyoung.recruit.dto.response.InterviewEvaluationExportRow;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.exception.StageNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 운영자 list-parity Excel export(stage results / interviews / interview evaluations).
 *
 * <p>각 dataset은 대응 admin list 엔드포인트의 기존 쿼리/서비스를 그대로 재사용해 필터·정렬·파생값(면접 인원
 * 카운트)을 동일하게 맞춘다(list-parity). materialize된 parity list에 {@link ExcelExportService}가 row cap을
 * 적용하고 writer를 호출한다. 모두 read-only이며 도메인 상태를 변경하지 않는다.
 */
@Service
public class AdminDatasetExportService {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final ExcelExportSpec<AdminStageResultResponse> STAGE_RESULTS_SPEC = new ExcelExportSpec<>(
            "stage-results",
            List.of(
                    new ExportColumn<>("stageResultId", row -> text(row.stageResultId())),
                    new ExportColumn<>("stageId", row -> text(row.stageId())),
                    new ExportColumn<>("applicationId", row -> text(row.applicationId())),
                    new ExportColumn<>("applicantName", row -> text(row.applicantName())),
                    new ExportColumn<>("jobPositionId", row -> text(row.jobPositionId())),
                    new ExportColumn<>("jobPositionName", row -> text(row.jobPositionName())),
                    new ExportColumn<>("applicationStatus", row -> text(row.applicationStatus())),
                    new ExportColumn<>("resultStatus", row -> text(row.resultStatus())),
                    new ExportColumn<>("score", row -> text(row.score())),
                    new ExportColumn<>("comment", row -> text(row.comment())),
                    new ExportColumn<>("submittedAt", row -> text(row.submittedAt())),
                    new ExportColumn<>("decidedAt", row -> text(row.decidedAt()))
            )
    );

    private static final ExcelExportSpec<AdminInterviewSummaryResponse> INTERVIEWS_SPEC = new ExcelExportSpec<>(
            "interviews",
            List.of(
                    new ExportColumn<>("interviewId", row -> text(row.interviewId())),
                    new ExportColumn<>("jobPostingTitle", row -> text(row.jobPostingTitle())),
                    new ExportColumn<>("stageName", row -> text(row.stageName())),
                    new ExportColumn<>("stageType", row -> text(row.stageType())),
                    new ExportColumn<>("groupName", row -> text(row.groupName())),
                    new ExportColumn<>("startDateTime", row -> text(row.startDateTime())),
                    new ExportColumn<>("endDateTime", row -> text(row.endDateTime())),
                    new ExportColumn<>("method", row -> text(row.method())),
                    new ExportColumn<>("locationName", row -> text(row.locationName())),
                    new ExportColumn<>("roomName", row -> text(row.roomName())),
                    new ExportColumn<>("onlineMeetingUrl", row -> text(row.onlineMeetingUrl())),
                    new ExportColumn<>("status", row -> text(row.status())),
                    new ExportColumn<>("candidateCount", row -> text(row.candidateCount())),
                    new ExportColumn<>("interviewerCount", row -> text(row.interviewerCount()))
            )
    );

    private static final ExcelExportSpec<InterviewEvaluationExportRow> EVALUATIONS_SPEC = new ExcelExportSpec<>(
            "interview-evaluations",
            List.of(
                    new ExportColumn<>("interviewId", row -> text(row.interviewId())),
                    new ExportColumn<>("groupName", row -> text(row.groupName())),
                    new ExportColumn<>("applicantName", row -> text(row.applicantName())),
                    new ExportColumn<>("positionName", row -> text(row.positionName())),
                    new ExportColumn<>("interviewerName", row -> text(row.interviewerName())),
                    new ExportColumn<>("status", row -> text(row.status())),
                    new ExportColumn<>("grade", row -> text(row.grade())),
                    new ExportColumn<>("recommendation", row -> text(row.recommendation())),
                    new ExportColumn<>("comment", row -> text(row.comment())),
                    new ExportColumn<>("submittedAt", row -> text(row.submittedAt()))
            )
    );

    private final StageResultService stageResultService;
    private final InterviewService interviewService;
    private final InterviewEvaluationRepository interviewEvaluationRepository;
    private final StageRepository stageRepository;
    private final ExcelExportService excelExportService;
    private final TransactionTemplate readOnlyTransactionTemplate;

    public AdminDatasetExportService(
            StageResultService stageResultService,
            InterviewService interviewService,
            InterviewEvaluationRepository interviewEvaluationRepository,
            StageRepository stageRepository,
            ExcelExportService excelExportService,
            PlatformTransactionManager transactionManager
    ) {
        this.stageResultService = stageResultService;
        this.interviewService = interviewService;
        this.interviewEvaluationRepository = interviewEvaluationRepository;
        this.stageRepository = stageRepository;
        this.excelExportService = excelExportService;
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    public ExcelExportFile exportStageResults(Long stageId) {
        List<AdminStageResultResponse> rows = stageResultService.getResults(stageId);
        return excelExportService.generate(STAGE_RESULTS_SPEC, rows, "stage-results-export-stage-" + stageId + ".xlsx");
    }

    public ExcelExportFile exportInterviews(
            Long jobPostingId,
            Long stageId,
            InterviewStatus status,
            LocalDateTime from,
            LocalDateTime to
    ) {
        List<AdminInterviewSummaryResponse> rows =
                interviewService.getAdminInterviews(jobPostingId, stageId, status, from, to);
        return excelExportService.generate(
                INTERVIEWS_SPEC, rows, "interviews-export-job-posting-" + jobPostingId + ".xlsx");
    }

    public ExcelExportFile exportStageEvaluations(Long stageId) {
        // DB 조회/매핑만 read-only 트랜잭션 안에서 수행하고, xlsx 생성(generate)은 트랜잭션 밖에서 한다.
        // (같은 클래스 self-invocation은 proxy가 안 먹으므로 TransactionTemplate으로 경계를 명시한다.)
        List<InterviewEvaluationExportRow> rows = readOnlyTransactionTemplate.execute(status -> {
            if (!stageRepository.existsById(stageId)) {
                throw new StageNotFoundException("Stage not found.");
            }
            return interviewEvaluationRepository.findByStageIdForAdmin(stageId).stream()
                    .map(InterviewEvaluationExportRow::from)
                    .toList();
        });
        return excelExportService.generate(
                EVALUATIONS_SPEC, rows, "interview-evaluations-export-stage-" + stageId + ".xlsx");
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(TIMESTAMP);
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return String.valueOf(value);
    }
}
