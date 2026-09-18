package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.InterviewEvaluationRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.InterviewScheduleSearchRequest;
import com.shinyoung.recruit.dto.request.InterviewScheduleUploadRowRequest;
import com.shinyoung.recruit.dto.response.AdminInterviewScheduleInterviewerResponse;
import com.shinyoung.recruit.dto.response.AdminInterviewScheduleRowResponse;
import com.shinyoung.recruit.dto.response.InterviewScheduleUploadResponse;
import com.shinyoung.recruit.dto.response.InterviewScheduleUploadRowError;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InvalidInterviewException;
import com.shinyoung.recruit.exception.InvalidInterviewScheduleUploadException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import com.shinyoung.recruit.exception.StageNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 관리자 면접 스케줄링. 입력은 엑셀 업로드로만 받는다.
 *
 * <p>한 행 = 지원자 1명, 조 = 면접 1건. 같은 조의 행은 일자·장소·도착시간·면접시간·면접관이 같아야 하고,
 * 조와 면접순서는 1부터 빠짐없이 이어진다. 업로드는 검증을 모두 통과해야만(all-or-nothing) 단계의 기존 면접을
 * 전부 지우고 파일 내용으로 새로 만들어 즉시 확정한다. 확정 검증(직전 단계 합격자, 같은 시각 중복)도 업로드에서 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewScheduleService {

    private static final Set<StageType> INTERVIEW_STAGE_TYPES = EnumSet.of(
            StageType.FIRST_INTERVIEW,
            StageType.SECOND_INTERVIEW,
            StageType.FINAL_INTERVIEW
    );
    private static final Set<StageStatus> MUTABLE_STAGE_STATUSES = EnumSet.of(StageStatus.READY, StageStatus.IN_PROGRESS);
    private static final Set<StageStatus> PREVIOUS_RESULT_VISIBLE_STATUSES = EnumSet.of(
            StageStatus.RESULT_ANNOUNCED,
            StageStatus.CLOSED
    );
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_INPUT_FORMAT = DateTimeFormatter.ofPattern("H:mm[:ss]");
    /** 면접관 칸의 한 사람: {@code 이름(로그인ID)}. */
    private static final Pattern INTERVIEWER_TOKEN = Pattern.compile("^(.+?)\\s*\\(\\s*([^()\\s]+)\\s*\\)$");
    private static final int LOCATION_MAX_LENGTH = 200;
    /** 오류 문구에 되비추는 사용자 입력 길이 상한(셀 길이에 비례해 응답이 커지지 않게). */
    private static final int ECHO_MAX_LENGTH = 50;
    private static final int[] COLUMN_WIDTHS = {12, 24, 10, 10, 10, 6, 36, 12, 12};

    private static final Comparator<AdminInterviewScheduleRowResponse> ROW_ORDER = Comparator
            .comparingLong((AdminInterviewScheduleRowResponse row) -> groupSortKey(row.groupName()))
            .thenComparing(AdminInterviewScheduleRowResponse::groupName, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AdminInterviewScheduleRowResponse::candidateOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AdminInterviewScheduleRowResponse::applicationId);

    /** 조회 표와 같은 9열. 템플릿·다운로드가 같은 정의를 쓰고, 업로드 파서가 같은 헤더로 대조한다. */
    private static final ExcelExportSpec<AdminInterviewScheduleRowResponse> SPEC = new ExcelExportSpec<>(
            "면접스케줄",
            List.of(
                    column(0, row -> row.interviewDateTime().format(DATE_FORMAT)),
                    column(1, AdminInterviewScheduleRowResponse::locationName),
                    column(2, row -> row.arrivalDateTime() == null ? null : row.arrivalDateTime().format(TIME_FORMAT)),
                    column(3, row -> row.interviewDateTime().format(TIME_FORMAT)),
                    column(4, row -> row.candidateOrder() == null ? null : String.valueOf(row.candidateOrder())),
                    column(5, AdminInterviewScheduleRowResponse::groupName),
                    column(6, row -> interviewersText(row.interviewers())),
                    column(7, row -> String.valueOf(row.applicationId())),
                    column(8, AdminInterviewScheduleRowResponse::applicantName)
            ),
            InterviewScheduleService::decorateSheet,
            true
    );

    private final JobPostingRepository jobPostingRepository;
    private final StageRepository stageRepository;
    private final StageResultRepository stageResultRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewParticipantRepository interviewParticipantRepository;
    private final InterviewEvaluationRepository interviewEvaluationRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final EmployeeRepository employeeRepository;
    private final InterviewScheduleUploadParser parser;
    private final ExcelExportService excelExportService;

    /** 단계의 취소되지 않은 면접에 배정된 지원자 행. 조 → 면접순서 → 수험번호 순. */
    public List<AdminInterviewScheduleRowResponse> getSchedules(Long jobPostingId, InterviewScheduleSearchRequest request) {
        findJobPosting(jobPostingId);
        if (request.stageId() == null) {
            throw new InvalidInterviewException("면접단계를 선택하세요.");
        }
        Long stageId = findStage(jobPostingId, request.stageId()).getId();

        Map<Long, List<AdminInterviewScheduleInterviewerResponse>> interviewersByInterview =
                interviewParticipantRepository.findScheduleInterviewersByStageId(stageId).stream()
                        .collect(Collectors.groupingBy(
                                participant -> participant.getInterview().getId(),
                                Collectors.mapping(
                                        participant -> AdminInterviewScheduleInterviewerResponse.from(participant.getEmployee()),
                                        Collectors.toList()
                                )
                        ));
        return interviewParticipantRepository.findScheduleCandidatesByStageId(stageId).stream()
                .filter(candidate -> matches(candidate, request))
                .map(candidate -> AdminInterviewScheduleRowResponse.from(
                        candidate,
                        interviewersByInterview.getOrDefault(candidate.getInterview().getId(), List.of())
                ))
                .sorted(ROW_ORDER)
                .toList();
    }

    /** 조회 결과를 그대로 채운 xlsx(0행이면 헤더만). 다시 업로드하는 원본이라 값을 변형하지 않는다. */
    public ExcelExportFile exportSchedules(Long jobPostingId, InterviewScheduleSearchRequest request) {
        List<AdminInterviewScheduleRowResponse> rows = getSchedules(jobPostingId, request);
        return excelExportService.generate(
                SPEC, rows, "interview-schedules-job-posting-" + jobPostingId + "-stage-" + request.stageId() + ".xlsx", false);
    }

    /** 헤더만 있는 업로드 양식. */
    public ExcelExportFile generateTemplate(Long jobPostingId) {
        findJobPosting(jobPostingId);
        return excelExportService.generate(SPEC, List.of(), "interview-schedule-template.xlsx", false);
    }

    /**
     * 단계 스케줄 전체 교체 + 즉시 확정. 검증 오류가 1건이라도 있으면 아무것도 바꾸지 않고 오류를 담아 돌려준다.
     * 파일 자체를 받을 수 없거나 단계가 업로드할 수 없는 상태면 {@link InvalidInterviewScheduleUploadException}.
     */
    @Transactional
    public InterviewScheduleUploadResponse upload(Long jobPostingId, Long stageId, MultipartFile file) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        if (stageId == null) {
            throw new InvalidInterviewScheduleUploadException("면접단계를 선택하세요.");
        }
        Stage stage = findStage(jobPostingId, stageId);
        validateUploadableStage(stage);
        Set<Long> previousPassedIds = findPreviousStagePassedApplicationIds(stage);

        List<InterviewScheduleUploadRowRequest> requests = parser.parse(file);
        if (requests.isEmpty()) {
            throw new InvalidInterviewScheduleUploadException("업로드할 행이 없습니다.");
        }

        List<ScheduleRow> rows = parseRows(jobPostingId, previousPassedIds, requests);
        Map<Integer, List<ScheduleRow>> groups = groupRows(rows);
        List<String> errors = new ArrayList<>();
        validateGroupConsistency(groups);
        if (rows.stream().allMatch(row -> row.groupNo != null && row.candidateOrder != null)) {
            validateNumbering(groups, errors);
        }
        validateInterviewerDoubleBooking(groups, errors);
        if (errors.isEmpty() && rows.stream().allMatch(row -> row.messages.isEmpty())) {
            validateConfirmedCollisions(stage.getId(), groups, errors);
        }

        List<InterviewScheduleUploadRowError> rowErrors = rows.stream()
                .filter(row -> !row.messages.isEmpty())
                .map(row -> new InterviewScheduleUploadRowError(row.rowNumber, List.copyOf(row.messages)))
                .toList();
        if (!errors.isEmpty() || !rowErrors.isEmpty()) {
            return InterviewScheduleUploadResponse.rejected(stageId, errors, rowErrors);
        }
        return replaceSchedules(jobPosting, stage, groups);
    }

    private InterviewScheduleUploadResponse replaceSchedules(
            JobPosting jobPosting,
            Stage stage,
            Map<Integer, List<ScheduleRow>> groups
    ) {
        List<Interview> existing = interviewRepository.findByStageId(stage.getId());
        // 참가자는 Interview 의 cascade + orphanRemoval 로 함께 지워진다. 새 행 insert 전에 삭제를 먼저 내보낸다.
        interviewRepository.deleteAll(existing);
        interviewRepository.flush();

        int candidateCount = 0;
        for (Map.Entry<Integer, List<ScheduleRow>> group : groups.entrySet()) {
            ScheduleRow reference = group.getValue().get(0);
            Interview interview = Interview.createDraft(
                    jobPosting,
                    stage,
                    String.valueOf(group.getKey()),
                    reference.date.atTime(reference.interviewTime),
                    reference.date.atTime(reference.arrivalTime),
                    InterviewMethod.IN_PERSON,
                    reference.location,
                    null,
                    null,
                    null
            );
            for (ScheduleRow row : group.getValue()) {
                InterviewParticipant.candidate(interview, row.application, row.candidateOrder);
                candidateCount++;
            }
            int interviewerOrder = 1;
            for (Employee employee : reference.interviewers) {
                InterviewParticipant.interviewer(interview, employee, interviewerOrder++);
            }
            interview.confirm();
            interviewRepository.save(interview);
        }
        return new InterviewScheduleUploadResponse(
                stage.getId(), groups.size(), candidateCount, existing.size(), List.of(), List.of());
    }

    private boolean matches(InterviewParticipant candidate, InterviewScheduleSearchRequest request) {
        JobApplication application = candidate.getJobApplication();
        if (request.applicationType() != null
                && application.getJobPosition().getApplicationType() != request.applicationType()) {
            return false;
        }
        if (request.jobPositionId() != null && !request.jobPositionId().equals(application.getJobPosition().getId())) {
            return false;
        }
        if (!isBlank(request.workLocation()) && !request.workLocation().trim().equals(application.getWorkLocationCode())) {
            return false;
        }
        return isBlank(request.groupName()) || request.groupName().trim().equals(candidate.getInterview().getGroupName());
    }

    private void validateUploadableStage(Stage stage) {
        if (!INTERVIEW_STAGE_TYPES.contains(stage.getStageType())) {
            throw new InvalidInterviewScheduleUploadException("면접 유형 단계에만 스케줄을 업로드할 수 있습니다.");
        }
        if (!MUTABLE_STAGE_STATUSES.contains(stage.getStatus())) {
            throw new InvalidInterviewScheduleUploadException("준비 중이거나 진행 중인 단계에만 스케줄을 업로드할 수 있습니다.");
        }
        if (interviewEvaluationRepository.existsByInterviewStageId(stage.getId())) {
            throw new InvalidInterviewScheduleUploadException("면접 평가가 시작된 면접이 있어 스케줄을 교체할 수 없습니다.");
        }
    }

    /** 면접 확정과 같은 기준: 직전 단계(stageOrder 바로 앞)가 발표·마감이어야 하고, 그 단계 합격자만 배정할 수 있다. */
    private Set<Long> findPreviousStagePassedApplicationIds(Stage stage) {
        Stage previous = stageRepository.findByJobPostingIdOrderByStageOrderAscIdAsc(stage.getJobPosting().getId()).stream()
                .filter(candidate -> candidate.getStageOrder() < stage.getStageOrder())
                .reduce((first, second) -> second)
                .orElseThrow(() -> new InvalidInterviewScheduleUploadException(
                        "직전 전형 단계가 없어 면접을 확정할 수 없습니다."));
        if (!PREVIOUS_RESULT_VISIBLE_STATUSES.contains(previous.getStatus())) {
            throw new InvalidInterviewScheduleUploadException(
                    "직전 단계(" + previous.getStageName() + ") 결과를 발표한 뒤 업로드할 수 있습니다.");
        }
        return stageResultRepository.findByStageId(previous.getId()).stream()
                .filter(result -> result.getResultStatus() == StageResultStatus.PASSED)
                .map(result -> result.getJobApplication().getId())
                .collect(Collectors.toSet());
    }

    // ---------------------------------------------------------------- 행 검증

    private List<ScheduleRow> parseRows(
            Long jobPostingId,
            Set<Long> previousPassedIds,
            List<InterviewScheduleUploadRowRequest> requests
    ) {
        Map<Long, JobApplication> applications = jobApplicationRepository.findAllById(requests.stream()
                        .map(request -> parsePositiveLong(request.applicationId()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(JobApplication::getId, Function.identity()));
        Map<String, Employee> employees = employeeRepository.findByLoginIdIn(requests.stream()
                        .flatMap(request -> interviewerTokens(request.interviewers()).stream())
                        .map(INTERVIEWER_TOKEN::matcher)
                        .filter(Matcher::matches)
                        .map(matcher -> matcher.group(2))
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Employee::getLoginId, Function.identity()));

        Set<Long> seenApplicationIds = new HashSet<>();
        List<ScheduleRow> rows = new ArrayList<>();
        for (InterviewScheduleUploadRowRequest request : requests) {
            ScheduleRow row = new ScheduleRow(request.rowNumber());
            if (request.formula()) {
                row.messages.add("수식(formula) 셀은 허용되지 않습니다.");
            }
            row.date = parseDate(request.date(), row.messages);
            row.location = parseLocation(request.location(), row.messages);
            row.arrivalTime = parseTime("도착시간", request.arrivalTime(), row.messages);
            row.interviewTime = parseTime("면접시간", request.interviewTime(), row.messages);
            if (row.arrivalTime != null && row.interviewTime != null && row.arrivalTime.isAfter(row.interviewTime)) {
                row.messages.add("도착시간은 면접시간보다 늦을 수 없습니다.");
            }
            row.candidateOrder = parsePositiveInt("면접순서", request.candidateOrder(), row.messages);
            row.groupNo = parsePositiveInt("조", request.groupName(), row.messages);
            row.interviewers = parseInterviewers(request.interviewers(), employees, row.messages);
            row.application = parseApplication(
                    request, jobPostingId, previousPassedIds, applications, seenApplicationIds, row.messages);
            rows.add(row);
        }
        return rows;
    }

    private LocalDate parseDate(String raw, List<String> messages) {
        if (isBlank(raw)) {
            messages.add("일자는 필수입니다.");
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            messages.add("일자 형식이 올바르지 않습니다(yyyy-MM-dd): " + echo(raw));
            return null;
        }
    }

    private String parseLocation(String raw, List<String> messages) {
        if (isBlank(raw)) {
            messages.add("장소는 필수입니다.");
            return null;
        }
        if (raw.length() > LOCATION_MAX_LENGTH) {
            messages.add("장소는 " + LOCATION_MAX_LENGTH + "자 이하여야 합니다.");
            return null;
        }
        return raw;
    }

    /** @param label 받침으로 끝나는 열 이름(도착시간·면접시간) */
    private LocalTime parseTime(String label, String raw, List<String> messages) {
        if (isBlank(raw)) {
            messages.add(label + "은 필수입니다.");
            return null;
        }
        try {
            return LocalTime.parse(raw, TIME_INPUT_FORMAT);
        } catch (DateTimeParseException e) {
            messages.add(label + " 형식이 올바르지 않습니다(HH:mm): " + echo(raw));
            return null;
        }
    }

    /** @param label 받침 없이 끝나는 열 이름(면접순서·조) */
    private Integer parsePositiveInt(String label, String raw, List<String> messages) {
        if (isBlank(raw)) {
            messages.add(label + "는 필수입니다.");
            return null;
        }
        Long value = parsePositiveLong(raw);
        if (value == null || value > Integer.MAX_VALUE) {
            messages.add(label + "는 1 이상의 정수여야 합니다: " + echo(raw));
            return null;
        }
        return value.intValue();
    }

    private List<Employee> parseInterviewers(String raw, Map<String, Employee> employees, List<String> messages) {
        List<String> tokens = interviewerTokens(raw);
        if (tokens.isEmpty()) {
            messages.add("면접관은 필수입니다.");
            return null;
        }
        List<Employee> interviewers = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        boolean valid = true;
        for (String token : tokens) {
            Matcher matcher = INTERVIEWER_TOKEN.matcher(token);
            if (!matcher.matches()) {
                messages.add("면접관은 이름(로그인ID) 형식이어야 합니다: " + echo(token));
                valid = false;
                continue;
            }
            Employee employee = employees.get(matcher.group(2));
            if (employee == null) {
                messages.add("면접관을 찾을 수 없습니다(시스템에 로그인한 적 있는 임직원만 지정할 수 있습니다): " + echo(token));
                valid = false;
            } else if (!matcher.group(1).trim().equals(employee.getName())) {
                messages.add("면접관 이름이 로그인ID와 일치하지 않습니다: " + echo(token));
                valid = false;
            } else if (!seen.add(employee.getId())) {
                messages.add("면접관이 중복되었습니다: " + echo(token));
                valid = false;
            } else {
                interviewers.add(employee);
            }
        }
        return valid ? interviewers : null;
    }

    private JobApplication parseApplication(
            InterviewScheduleUploadRowRequest request,
            Long jobPostingId,
            Set<Long> previousPassedIds,
            Map<Long, JobApplication> applications,
            Set<Long> seenApplicationIds,
            List<String> messages
    ) {
        boolean nameMissing = isBlank(request.applicantName());
        if (nameMissing) {
            messages.add("성명은 필수입니다.");
        }
        if (isBlank(request.applicationId())) {
            messages.add("수험번호는 필수입니다.");
            return null;
        }
        Long applicationId = parsePositiveLong(request.applicationId());
        if (applicationId == null) {
            messages.add("수험번호는 숫자여야 합니다: " + echo(request.applicationId()));
            return null;
        }
        if (!seenApplicationIds.add(applicationId)) {
            messages.add("수험번호가 파일 내에서 중복되었습니다: " + applicationId);
            return null;
        }
        JobApplication application = applications.get(applicationId);
        if (application == null || !jobPostingId.equals(application.getJobPosting().getId())) {
            messages.add("이 공고의 지원서가 아닙니다: " + applicationId);
            return null;
        }
        if (application.getStatus() != JobApplicationStatus.SUBMITTED) {
            messages.add("제출 완료 지원서가 아닙니다: " + applicationId);
            return null;
        }
        if (!previousPassedIds.contains(applicationId)) {
            messages.add("직전 단계 합격자가 아닙니다: " + applicationId);
            return null;
        }
        if (!nameMissing && !request.applicantName().equals(applicantName(application))) {
            messages.add("성명이 수험번호의 지원자와 일치하지 않습니다: " + echo(request.applicantName()));
        }
        return application;
    }

    // ---------------------------------------------------------------- 조 검증

    /** 조 번호로 묶는다. 조가 오류인 행은 빠진다(그 행은 이미 행 오류가 있다). 조 안 순서는 파일 순서. */
    private Map<Integer, List<ScheduleRow>> groupRows(List<ScheduleRow> rows) {
        Map<Integer, List<ScheduleRow>> groups = new TreeMap<>();
        for (ScheduleRow row : rows) {
            if (row.groupNo != null) {
                groups.computeIfAbsent(row.groupNo, key -> new ArrayList<>()).add(row);
            }
        }
        return groups;
    }

    /** 같은 조의 행은 첫 행과 일자·장소·도착시간·면접시간·면접관이 같아야 하고, 면접순서가 겹치면 안 된다. */
    private void validateGroupConsistency(Map<Integer, List<ScheduleRow>> groups) {
        for (Map.Entry<Integer, List<ScheduleRow>> group : groups.entrySet()) {
            ScheduleRow reference = null;
            Set<Integer> orders = new HashSet<>();
            for (ScheduleRow row : group.getValue()) {
                if (row.candidateOrder != null && !orders.add(row.candidateOrder)) {
                    row.messages.add(group.getKey() + "조에서 면접순서가 중복되었습니다: " + row.candidateOrder);
                }
                if (!row.groupComparable()) {
                    continue;
                }
                if (reference == null) {
                    reference = row;
                    continue;
                }
                List<String> differences = new ArrayList<>();
                if (!row.date.equals(reference.date)) {
                    differences.add("일자");
                }
                if (!row.location.equals(reference.location)) {
                    differences.add("장소");
                }
                if (!row.arrivalTime.equals(reference.arrivalTime)) {
                    differences.add("도착시간");
                }
                if (!row.interviewTime.equals(reference.interviewTime)) {
                    differences.add("면접시간");
                }
                if (!employeeIds(row.interviewers).equals(employeeIds(reference.interviewers))) {
                    differences.add("면접관");
                }
                if (!differences.isEmpty()) {
                    row.messages.add(group.getKey() + "조의 첫 행과 " + String.join("·", differences) + "이(가) 다릅니다.");
                }
            }
        }
    }

    /** 조는 1부터, 조별 면접순서도 1부터 빠짐없이 이어져야 한다. */
    private void validateNumbering(Map<Integer, List<ScheduleRow>> groups, List<String> errors) {
        List<Integer> missingGroups = missingNumbers(groups.keySet());
        if (!missingGroups.isEmpty()) {
            errors.add("조는 1부터 빠짐없이 이어져야 합니다(누락: " + joinNumbers(missingGroups) + ").");
        }
        for (Map.Entry<Integer, List<ScheduleRow>> group : groups.entrySet()) {
            Set<Integer> orders = group.getValue().stream().map(row -> row.candidateOrder).collect(Collectors.toSet());
            List<Integer> missingOrders = missingNumbers(orders);
            if (!missingOrders.isEmpty()) {
                errors.add(group.getKey() + "조의 면접순서는 1부터 빠짐없이 이어져야 합니다(누락: "
                        + joinNumbers(missingOrders) + ").");
            }
        }
    }

    /** 같은 면접관이 같은 일자·면접시간에 두 조 이상 들어가면 안 된다. */
    private void validateInterviewerDoubleBooking(Map<Integer, List<ScheduleRow>> groups, List<String> errors) {
        Map<String, List<Integer>> groupsBySlot = new LinkedHashMap<>();
        Map<String, String> labelsBySlot = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<ScheduleRow>> group : groups.entrySet()) {
            ScheduleRow reference = firstComparable(group.getValue());
            if (reference == null) {
                continue;
            }
            LocalDateTime startDateTime = reference.date.atTime(reference.interviewTime);
            for (Employee employee : reference.interviewers) {
                String slot = employee.getId() + "@" + startDateTime;
                groupsBySlot.computeIfAbsent(slot, key -> new ArrayList<>()).add(group.getKey());
                labelsBySlot.putIfAbsent(slot, interviewerLabel(employee) + "이(가) " + formatDateTime(startDateTime));
            }
        }
        groupsBySlot.forEach((slot, groupNos) -> {
            if (groupNos.size() > 1) {
                errors.add("면접관 " + labelsBySlot.get(slot) + "에 여러 조("
                        + groupNos.stream().map(groupNo -> groupNo + "조").collect(Collectors.joining(", "))
                        + ")에 배정되었습니다.");
            }
        });
    }

    /** 다른 단계·공고의 확정 면접과 같은 시각에 겹치는지(면접 확정 검증과 같은 기준). 교체 대상 단계는 뺀다. */
    private void validateConfirmedCollisions(Long stageId, Map<Integer, List<ScheduleRow>> groups, List<String> errors) {
        for (Map.Entry<Integer, List<ScheduleRow>> group : groups.entrySet()) {
            ScheduleRow reference = group.getValue().get(0);
            LocalDateTime startDateTime = reference.date.atTime(reference.interviewTime);
            for (Employee employee : reference.interviewers) {
                if (interviewParticipantRepository.existsInterviewerConfirmedAtStartOutsideStage(
                        employee.getId(), stageId, startDateTime)) {
                    errors.add("면접관 " + interviewerLabel(employee) + "이(가) 같은 시각(" + formatDateTime(startDateTime)
                            + ")에 다른 전형의 확정 면접에 배정되어 있습니다.");
                }
            }
            for (ScheduleRow row : group.getValue()) {
                if (interviewParticipantRepository.existsCandidateConfirmedAtStartOutsideStage(
                        row.application.getId(), stageId, startDateTime)) {
                    row.messages.add("같은 시각(" + formatDateTime(startDateTime) + ")에 다른 전형의 확정 면접이 있습니다.");
                }
            }
        }
    }

    // ---------------------------------------------------------------- 공통

    private JobPosting findJobPosting(Long jobPostingId) {
        return jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("JobPosting not found. id=" + jobPostingId));
    }

    private Stage findStage(Long jobPostingId, Long stageId) {
        return stageRepository.findByIdAndJobPostingId(stageId, jobPostingId)
                .orElseThrow(() -> new StageNotFoundException("Stage not found. id=" + stageId));
    }

    private static ExportColumn<AdminInterviewScheduleRowResponse> column(
            int index,
            Function<AdminInterviewScheduleRowResponse, String> extractor
    ) {
        return new ExportColumn<>(InterviewScheduleUploadParser.HEADERS.get(index), extractor);
    }

    /** 헤더 틀고정 + 열 너비. 셀 값·스타일은 writer가 이미 기록했다. */
    private static void decorateSheet(Sheet sheet, int dataRowCount) {
        sheet.createFreezePane(0, 1);
        for (int index = 0; index < COLUMN_WIDTHS.length; index++) {
            sheet.setColumnWidth(index, COLUMN_WIDTHS[index] * 256);
        }
    }

    private static String interviewersText(List<AdminInterviewScheduleInterviewerResponse> interviewers) {
        return interviewers.stream()
                .map(interviewer -> interviewer.name() + "(" + interviewer.loginId() + ")")
                .collect(Collectors.joining(", "));
    }

    /** 조가 숫자면 숫자 순, 아니면(업로드가 아닌 경로로 만든 면접) 뒤로 보낸다. */
    private static long groupSortKey(String groupName) {
        Long value = parsePositiveLong(groupName);
        return value == null ? Long.MAX_VALUE : value;
    }

    private static List<String> interviewerTokens(String raw) {
        if (isBlank(raw)) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : raw.split(",")) {
            if (!token.isBlank()) {
                tokens.add(token.trim());
            }
        }
        return tokens;
    }

    private static Long parsePositiveLong(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Integer> missingNumbers(Set<Integer> numbers) {
        int max = numbers.stream().mapToInt(Integer::intValue).max().orElse(0);
        return IntStream.rangeClosed(1, max).filter(number -> !numbers.contains(number)).boxed().toList();
    }

    private static String joinNumbers(List<Integer> numbers) {
        return numbers.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static Set<Long> employeeIds(List<Employee> employees) {
        return employees.stream().map(Employee::getId).collect(Collectors.toSet());
    }

    private static ScheduleRow firstComparable(List<ScheduleRow> rows) {
        return rows.stream().filter(ScheduleRow::groupComparable).findFirst().orElse(null);
    }

    private static String interviewerLabel(Employee employee) {
        return employee.getName() + "(" + employee.getLoginId() + ")";
    }

    private static String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DATE_FORMAT) + " " + dateTime.format(TIME_FORMAT);
    }

    /** 지원 시점 성명 스냅샷. 스냅샷이 없는 과거 데이터는 계정 성명으로 대조한다. */
    private static String applicantName(JobApplication application) {
        String snapshot = application.getApplicantNameSnapshot();
        return snapshot != null ? snapshot : application.getApplicant().getName();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String echo(String value) {
        return value.length() > ECHO_MAX_LENGTH ? value.substring(0, ECHO_MAX_LENGTH) + "…" : value;
    }

    /** 형식 검증을 거친 한 행. 칸이 오류면 해당 필드는 null 이고 사유는 messages 에 있다. */
    private static final class ScheduleRow {
        private final int rowNumber;
        private final List<String> messages = new ArrayList<>();
        private LocalDate date;
        private String location;
        private LocalTime arrivalTime;
        private LocalTime interviewTime;
        private Integer candidateOrder;
        private Integer groupNo;
        private List<Employee> interviewers;
        private JobApplication application;

        private ScheduleRow(int rowNumber) {
            this.rowNumber = rowNumber;
        }

        /** 조 안 비교(일자·장소·시각·면접관)에 필요한 칸이 모두 읽혔는지. */
        private boolean groupComparable() {
            return date != null && location != null && arrivalTime != null && interviewTime != null
                    && interviewers != null;
        }
    }
}
