package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationAward;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.ApplicationCareer;
import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationGapPeriod;
import com.shinyoung.recruit.domain.entity.ApplicationLanguage;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.ApplicationExportRow;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 지원현황 엑셀 한 페이지의 셀 값을 만든다. base projection 행 + 선택 컬럼이 요구하는 섹션을 지원서 id 로
 * 배치 조회(N+1 없음)해 {@code Map<ApplicationExportColumn, String>} 으로 조립한다.
 *
 * <p>표기는 PDF({@link ApplicationPdfService})와 같다 — enum 은 {@link ApplicationPdfLabels}, 공통코드는
 * {@link CommonCodeNames}, 날짜는 같은 포맷. 연락처는 기존 엑셀처럼 저장값 그대로 둔다.
 * 화면·PDF 에서 마스킹되는 값(면제 사유, 자격증번호)은 넣지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ApplicationExportRowAssembler {

    /**
     * 셀 최대 글자 수. Excel 한도(32,767)보다 1 작다 — writer 의 formula-injection escape 가 앞에 {@code '} 를
     * 붙여도 한도를 넘지 않게 한다.
     */
    static final int MAX_CELL_LENGTH = 32_766;
    static final String TRUNCATED_SUFFIX = "…(이하 생략)";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String FIELD_SEPARATOR = " / ";
    private static final String LINE_SEPARATOR = "\n";

    /** 화면(ApplicationStatus.vue statusLabelMap)과 같은 표기. */
    private static final Map<JobApplicationStatus, String> STATUS_LABELS = new EnumMap<>(Map.of(
            JobApplicationStatus.DRAFT, "임시저장",
            JobApplicationStatus.SUBMITTED, "제출 완료",
            JobApplicationStatus.WITHDRAWN, "지원 철회"));

    // 최종학력 행 = 최고 EducationLevel(선언 순서 = 서열), 동률이면 id 가 큰 행 — 목록(JobApplicationService)·
    // 전형결과 그리드(AdminStageResultEnricher)와 같은 규칙. 한쪽만 바꾸면 화면과 엑셀의 최종학력이 어긋난다.
    private static final Comparator<ApplicationEducation> FINAL_EDUCATION = Comparator
            .comparingInt((ApplicationEducation education) -> education.getEducationLevel().ordinal())
            .thenComparing(ApplicationEducation::getId);

    // 전형 진행 순서 = stageOrder, 동률이면 stage id — 목록의 "최신 전형 결과" 판정과 같은 규칙(마지막 원소가 최신).
    private static final Comparator<StageResult> STAGE_ORDER = Comparator
            .comparing((StageResult result) -> result.getStage().getStageOrder())
            .thenComparing(result -> result.getStage().getId());

    private final ApplicationBasicInfoRepository basicInfoRepository;
    private final ApplicationMilitaryRepository militaryRepository;
    private final ApplicationEducationRepository educationRepository;
    private final ApplicationCareerRepository careerRepository;
    private final ApplicationCertificateRepository certificateRepository;
    private final ApplicationLanguageRepository languageRepository;
    private final ApplicationAwardRepository awardRepository;
    private final ApplicationGapPeriodRepository gapPeriodRepository;
    private final StageResultRepository stageResultRepository;
    private final CommonCodeService commonCodeService;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    /** export 1회용 공통코드 캐시. 서비스가 export 시작 시 한 번 만들어 모든 페이지에 넘긴다. */
    CommonCodeNames newCodeNames() {
        return new CommonCodeNames(commonCodeService);
    }

    /**
     * 한 페이지 base 행에 선택 컬럼 값을 채운다. 조립이 끝나면 영속성 컨텍스트를 비운다 — 값은 이미 문자열이고,
     * 비우지 않으면 5만 행 export 동안 섹션 entity 가 한 트랜잭션에 계속 쌓인다(export 트랜잭션은 read-only 라
     * 비워도 잃는 변경이 없다).
     *
     * <p>호출자는 assemble 이전에 로드한 entity 를 이후에 다시 쓰지 않아야 한다(OSIV 로 요청 전체가 같은
     * EntityManager 를 공유하므로 clear 가 그 entity 들도 준영속으로 만든다).
     */
    List<Map<ApplicationExportColumn, String>> assemble(
            List<ApplicationExportRow> rows,
            List<ApplicationExportColumn> columns,
            CommonCodeNames codeNames
    ) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> applicationIds = rows.stream().map(ApplicationExportRow::applicationId).toList();
        Sections sections = load(applicationIds, ApplicationExportColumn.requiredSections(columns));
        LocalDate today = LocalDate.now(clock);

        List<Map<ApplicationExportColumn, String>> page = new ArrayList<>(rows.size());
        for (ApplicationExportRow row : rows) {
            Source source = source(row, sections);
            Map<ApplicationExportColumn, String> values = new EnumMap<>(ApplicationExportColumn.class);
            for (ApplicationExportColumn column : columns) {
                values.put(column, truncate(value(column, source, codeNames, today)));
            }
            page.add(values);
        }
        entityManager.clear();
        return page;
    }

    private Sections load(List<Long> ids, Set<ApplicationExportSection> required) {
        return new Sections(
                required.contains(ApplicationExportSection.BASIC_INFO)
                        ? single(basicInfoRepository.findByJobApplicationIdIn(ids), ApplicationBasicInfo::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.MILITARY)
                        ? single(militaryRepository.findByJobApplicationIdIn(ids), ApplicationMilitary::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.EDUCATION)
                        ? grouped(educationRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                                ApplicationEducation::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.CAREER)
                        ? grouped(careerRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                                ApplicationCareer::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.CERTIFICATE)
                        ? grouped(certificateRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                                ApplicationCertificate::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.LANGUAGE)
                        ? grouped(languageRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                                ApplicationLanguage::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.AWARD)
                        ? grouped(awardRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                                ApplicationAward::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.GAP_PERIOD)
                        ? grouped(gapPeriodRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                                ApplicationGapPeriod::getJobApplication)
                        : Map.of(),
                required.contains(ApplicationExportSection.STAGE_RESULT)
                        ? grouped(stageResultRepository.findWithStageByJobApplicationIdIn(ids),
                                StageResult::getJobApplication)
                        : Map.of());
    }

    private Source source(ApplicationExportRow row, Sections sections) {
        Long id = row.applicationId();
        List<ApplicationEducation> educations = sections.educations().getOrDefault(id, List.of());
        return new Source(
                row,
                sections.basicInfos().get(id),
                sections.militaries().get(id),
                educations,
                educations.stream().max(FINAL_EDUCATION).orElse(null),
                sections.careers().getOrDefault(id, List.of()),
                sections.certificates().getOrDefault(id, List.of()),
                sections.languages().getOrDefault(id, List.of()),
                sections.awards().getOrDefault(id, List.of()),
                sections.gapPeriods().getOrDefault(id, List.of()),
                sections.stageResults().getOrDefault(id, List.of()).stream().sorted(STAGE_ORDER).toList());
    }

    private String value(ApplicationExportColumn column, Source s, CommonCodeNames codes, LocalDate today) {
        ApplicationExportRow row = s.row();
        ApplicationBasicInfo info = s.basicInfo();
        ApplicationEducation fin = s.finalEducation();
        return switch (column) {
            case APPLICATION_ID -> String.valueOf(row.applicationId());
            case JOB_POSTING_TITLE -> text(row.jobPostingTitle());
            case APPLICATION_TYPE -> ApplicationPdfLabels.label(row.applicationType());
            case JOB_POSITION_NAME -> text(row.jobPositionName());
            case JOB_TITLE -> text(row.jobTitle());
            case WORK_LOCATION -> text(row.workLocationName());
            case STATUS -> row.status() == null ? "" : STATUS_LABELS.getOrDefault(row.status(), row.status().name());
            case SUBMITTED_AT -> dateTime(row.submittedAt());
            case CREATED_AT -> dateTime(row.createdAt());
            case UPDATED_AT -> dateTime(row.updatedAt());
            case WITHDRAWN_AT -> dateTime(row.withdrawnAt());

            case LATEST_STAGE_RESULT -> s.stageResults().isEmpty()
                    ? ""
                    : stageResult(s.stageResults().get(s.stageResults().size() - 1), " ");
            case STAGE_RESULTS -> lines(s.stageResults(), result -> stageResult(result, ": "));

            // 이름·연락처: 기본정보 행이 있으면 그 값(파기로 null 이어도 fallback 없음), 없으면 snapshot/계정 값 — PDF 와 같은 규칙.
            case NAME -> info != null ? text(info.getNameKorean()) : text(row.applicantName());
            case MOBILE_PHONE -> info != null ? text(info.getMobilePhone()) : text(row.phoneNumber());
            case EMAIL -> info != null ? text(info.getEmail()) : text(row.email());
            case NAME_ENGLISH -> info == null ? "" : text(info.getNameEnglish());
            case NATIONALITY -> info == null ? "" : nationality(info, codes);
            case BIRTH_DATE -> info == null ? "" : date(info.getBirthDate());
            case AGE -> info == null || info.getBirthDate() == null
                    ? ""
                    : String.valueOf(Period.between(info.getBirthDate(), today).getYears());
            case EMERGENCY_PHONE -> info == null ? "" : text(info.getEmergencyPhone());
            case ADDRESS -> info == null ? "" : address(info);
            case VETERAN -> info == null ? "" : veteran(info);
            case DISABILITY -> info == null ? "" : disability(info, codes);
            case APPLICATION_ROUTE -> info == null ? "" : codes.name("APPLICATION_ROUTE", info.getApplicationRouteCode());

            case MILITARY -> s.military() == null ? "" : military(s.military());

            case FINAL_EDUCATION_LEVEL -> fin == null ? "" : ApplicationPdfLabels.label(fin.getEducationLevel());
            case FINAL_SCHOOL_NAME -> fin == null ? "" : text(fin.getSchoolName());
            case FINAL_MAJOR -> fin == null ? "" : text(fin.getMajorName());
            case FINAL_ADDITIONAL_MAJOR -> fin == null ? "" : additionalMajor(fin, codes);
            case FINAL_GRADUATION_STATUS -> fin == null ? "" : ApplicationPdfLabels.label(fin.getGraduationStatus());
            case FINAL_ADMISSION_DATE -> fin == null ? "" : yearMonth(fin.getAdmissionDate());
            case FINAL_GRADUATION_DATE -> fin == null ? "" : yearMonth(fin.getGraduationDate());
            case FINAL_GPA -> fin == null ? "" : gradePoint(fin.getOverallGradePoint(), fin.getOverallMaxGradePoint());
            case FINAL_MAJOR_GPA -> fin == null
                    ? ""
                    : gradePoint(fin.getOverallMajorGradePoint(), fin.getOverallMajorMaxGradePoint());
            case FINAL_SCHOOL_LOCATION -> fin == null ? "" : schoolLocation(fin, codes);
            case FINAL_SCHOOL_TYPE -> fin == null ? "" : schoolType(fin);

            case EDUCATIONS -> lines(s.educations(), this::education);
            case CAREERS -> lines(s.careers(), this::career);
            case CURRENT_SALARY -> lines(
                    s.careers().stream()
                            .filter(c -> Boolean.TRUE.equals(c.getCurrentlyEmployed()) && c.getCurrentSalary() != null)
                            .toList(),
                    c -> text(c.getCompanyName()) + ": " + String.format(Locale.ROOT, "%,d", c.getCurrentSalary()));
            case CERTIFICATES -> lines(s.certificates(), this::certificate);
            case LANGUAGES -> lines(s.languages(), this::language);
            case AWARDS -> lines(s.awards(), this::award);
            case GAP_PERIODS -> lines(s.gapPeriods(), this::gapPeriod);
        };
    }

    private String stageResult(StageResult result, String separator) {
        return text(result.getStage().getStageName()) + separator + StageResultStatusLabels.label(result.getResultStatus());
    }

    /** PDF 와 같이 "외국인 (국가명)". */
    private String nationality(ApplicationBasicInfo info, CommonCodeNames codes) {
        NationalityType type = info.getNationalityType();
        if (type == null) {
            return "";
        }
        String label = ApplicationPdfLabels.label(type);
        if (type == NationalityType.DOMESTIC) {
            return label;
        }
        String country = codes.name("NATIONALITY", info.getCountryCode());
        return country.isBlank() ? label : label + " (" + country + ")";
    }

    private String address(ApplicationBasicInfo info) {
        StringBuilder builder = new StringBuilder();
        if (hasText(info.getZipCode())) {
            builder.append('(').append(info.getZipCode()).append(") ");
        }
        if (hasText(info.getAddressBasic())) {
            builder.append(info.getAddressBasic());
        }
        if (hasText(info.getAddressDetail())) {
            builder.append(", ").append(info.getAddressDetail());
        }
        return builder.toString().trim();
    }

    private String veteran(ApplicationBasicInfo info) {
        VeteranStatus status = info.getVeteranStatus();
        if (status == null) {
            return "";
        }
        String label = ApplicationPdfLabels.label(status);
        if (status == VeteranStatus.NOT_SUBJECT || !hasText(info.getVeteranType())) {
            return label;
        }
        return label + " (" + info.getVeteranType() + ")";
    }

    private String disability(ApplicationBasicInfo info, CommonCodeNames codes) {
        DisabilityStatus status = info.getDisabilityStatus();
        if (status == null) {
            return "";
        }
        String label = ApplicationPdfLabels.label(status);
        if (status == DisabilityStatus.NOT_SUBJECT) {
            return label;
        }
        return label + " (등급: " + codes.name("DISABILITY_GRADE", info.getDisabilityGradeCode())
                + " / 유형: " + codes.name("DISABILITY_TYPE", info.getDisabilityTypeCode()) + ")";
    }

    /** 군필만 군별·계급·기간을 쓴다. 미필·면제 사유는 화면·PDF 에서도 마스킹되는 민감정보라 넣지 않는다. */
    private String military(ApplicationMilitary military) {
        MilitarySubjectType type = military.getMilitarySubjectType();
        String status = ApplicationPdfLabels.label(type);
        if (type != MilitarySubjectType.COMPLETED) {
            return status;
        }
        return fields(
                status,
                (ApplicationPdfLabels.label(military.getMilitaryBranch()) + " "
                        + ApplicationPdfLabels.label(military.getServiceType())).trim(),
                ApplicationPdfLabels.label(military.getRank()),
                range(military.getServiceStartDate(), military.getServiceEndDate(), DATE));
    }

    private String additionalMajor(ApplicationEducation education, CommonCodeNames codes) {
        String type = codes.name("MAJOR_TYPE", education.getAdditionalMajorType());
        String name = text(education.getAdditionalMajorName());
        if (type.isBlank()) {
            return name;
        }
        return name.isBlank() ? type : type + ": " + name;
    }

    private String schoolLocation(ApplicationEducation education, CommonCodeNames codes) {
        if (!hasText(education.getCountryCode())) {
            return "국내";
        }
        return "해외 (" + codes.name("NATIONALITY", education.getCountryCode()) + ")";
    }

    private String schoolType(ApplicationEducation education) {
        List<String> types = new ArrayList<>();
        if (Boolean.TRUE.equals(education.getTransfer())) {
            types.add("편입");
        }
        if (education.getCampusType() == CampusType.BRANCH) {
            types.add("분교");
        }
        if (education.getDayNightType() == DayNightType.NIGHT) {
            types.add("야간");
        }
        return String.join(", ", types);
    }

    private String education(ApplicationEducation e) {
        return fields(
                ApplicationPdfLabels.label(e.getEducationLevel()),
                text(e.getSchoolName()),
                text(e.getMajorName()),
                range(e.getAdmissionDate(), e.getGraduationDate(), DATE),
                ApplicationPdfLabels.label(e.getGraduationStatus()),
                gradePoint(e.getOverallGradePoint(), e.getOverallMaxGradePoint()));
    }

    private String career(ApplicationCareer c) {
        String period = c.getEndDate() == null
                ? (c.getStartDate() == null ? "" : date(c.getStartDate()) + " ~ 재직중")
                : range(c.getStartDate(), c.getEndDate(), DATE);
        return fields(
                text(c.getCompanyName()),
                text(c.getDepartmentName()),
                text(c.getPositionTitle()),
                ApplicationPdfLabels.label(c.getEmploymentType()),
                period,
                text(c.getResignationReason()));
    }

    private String certificate(ApplicationCertificate c) {
        return fields(text(c.getCertificateName()), text(c.getIssuingOrganization()),
                date(c.getAcquiredDate()), text(c.getScoreOrGrade()));
    }

    private String language(ApplicationLanguage l) {
        return fields(text(l.getLanguageName()), text(l.getTestName()), text(l.getScoreOrGrade()),
                text(l.getConversationalAbility()), date(l.getExamDate()));
    }

    private String award(ApplicationAward a) {
        return fields(text(a.getAwardName()), text(a.getAwardingOrganization()), yearMonth(a.getAwardDate()));
    }

    private String gapPeriod(ApplicationGapPeriod g) {
        return fields(range(g.getStartDate(), g.getEndDate(), YEAR_MONTH),
                ApplicationPdfLabels.label(g.getGapType()), text(g.getReason()));
    }

    static String truncate(String value) {
        if (value.length() <= MAX_CELL_LENGTH) {
            return value;
        }
        int end = MAX_CELL_LENGTH - TRUNCATED_SUFFIX.length();
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end) + TRUNCATED_SUFFIX;
    }

    /** 빈 값은 빼고 " / " 로 잇는다. */
    private static String fields(String... values) {
        return Arrays.stream(values)
                .filter(ApplicationExportRowAssembler::hasText)
                .collect(Collectors.joining(FIELD_SEPARATOR));
    }

    /** 1건 = 1줄. 빈 줄은 뺀다. */
    private static <E> String lines(List<E> items, Function<E, String> line) {
        return items.stream()
                .map(line)
                .filter(ApplicationExportRowAssembler::hasText)
                .collect(Collectors.joining(LINE_SEPARATOR));
    }

    private static String range(LocalDate from, LocalDate to, DateTimeFormatter formatter) {
        if (from == null && to == null) {
            return "";
        }
        return format(from, formatter) + " ~ " + format(to, formatter);
    }

    private static String gradePoint(BigDecimal point, BigDecimal max) {
        if (point == null && max == null) {
            return "";
        }
        return plain(point) + " / " + plain(max);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String date(LocalDate value) {
        return format(value, DATE);
    }

    private static String yearMonth(LocalDate value) {
        return format(value, YEAR_MONTH);
    }

    private static String format(LocalDate value, DateTimeFormatter formatter) {
        return value == null ? "" : value.format(formatter);
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <E> Map<Long, E> single(List<E> entities, Function<E, JobApplication> owner) {
        return entities.stream().collect(Collectors.toMap(e -> owner.apply(e).getId(), e -> e, (a, b) -> a));
    }

    /** repository 정렬 순서를 그대로 유지한 채 지원서별로 묶는다. */
    private static <E> Map<Long, List<E>> grouped(List<E> entities, Function<E, JobApplication> owner) {
        return entities.stream().collect(Collectors.groupingBy(
                e -> owner.apply(e).getId(), LinkedHashMap::new, Collectors.toList()));
    }

    /** 한 페이지 분량의 섹션 조회 결과(지원서 id → 값). 선택되지 않은 섹션은 빈 map. */
    private record Sections(
            Map<Long, ApplicationBasicInfo> basicInfos,
            Map<Long, ApplicationMilitary> militaries,
            Map<Long, List<ApplicationEducation>> educations,
            Map<Long, List<ApplicationCareer>> careers,
            Map<Long, List<ApplicationCertificate>> certificates,
            Map<Long, List<ApplicationLanguage>> languages,
            Map<Long, List<ApplicationAward>> awards,
            Map<Long, List<ApplicationGapPeriod>> gapPeriods,
            Map<Long, List<StageResult>> stageResults
    ) {
    }

    /** 지원서 1건의 값 원천. {@code stageResults} 는 진행 순서(마지막 = 최신). */
    private record Source(
            ApplicationExportRow row,
            ApplicationBasicInfo basicInfo,
            ApplicationMilitary military,
            List<ApplicationEducation> educations,
            ApplicationEducation finalEducation,
            List<ApplicationCareer> careers,
            List<ApplicationCertificate> certificates,
            List<ApplicationLanguage> languages,
            List<ApplicationAward> awards,
            List<ApplicationGapPeriod> gapPeriods,
            List<StageResult> stageResults
    ) {
    }
}
