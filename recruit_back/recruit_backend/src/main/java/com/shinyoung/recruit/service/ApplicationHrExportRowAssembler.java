package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.ApplicationCareer;
import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationLanguage;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.response.ApplicationHrExportRow;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.Gender;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.NationalityType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 인사팀 양식(지원현황조회 TO-BE, 80열) 지원현황 엑셀 한 페이지의 셀 값을 만든다. 열 순서 = {@link #HEADERS}.
 *
 * <p>판정 규칙(사용자 결정 2026-10-08):
 * <ul>
 *   <li>최초대학 = 전문대·대학교 행 중 입학일이 가장 이른 행, 최종대학(학사) = 가장 늦은 행, 최종대학(석/박) = 석사·박사 행 중
 *       가장 늦은 행. 입학일이 없으면 가장 늦은 것으로 보고, 같으면 id 순.</li>
 *   <li>석/박 세부전공 = 석·박사 행의 {@code additionalMajorName}(지원자 화면이 전공 구분을 MT_003 으로 고정한다).</li>
 *   <li>평점 = 전체 평점을 4.5 만점으로 환산(소수 둘째 자리 반올림)해 {@code "4.2 / 4.5"}.</li>
 *   <li>자격증 = 취득일 순, 경력 = 최신(입사일 늦은 순)이 1. 경력 근무지는 회사명에 입력한 그대로.</li>
 *   <li>번호 칸(외국어 5·자격증 11·경력 9)보다 많으면 마지막 칸에 줄바꿈으로 이어 쓴다(잘라내지 않는다).</li>
 * </ul>
 * 보훈·장애·주소·전형결과 표기는 기존 지원현황 엑셀({@link ApplicationExportRowAssembler})과 같다.
 */
@Component
@RequiredArgsConstructor
public class ApplicationHrExportRowAssembler {

    static final int LANGUAGE_SLOTS = 5;
    static final int CERTIFICATE_SLOTS = 11;
    static final int CAREER_SLOTS = 9;

    static final List<String> HEADERS = List.of(
            "지원구분", "지원분야", "진행결과(전형별결과)", "직무/근무지", "수험번호", "이름", "성별", "생년월일", "내/외국인",
            "최종학력", "고교",
            "최초대학", "최초대학 구분_전문대/대학교", "최초대학 입학년월", "최초대학 졸업년월", "최초대학_주간/야간",
            "최초대학_전공1", "최초대학_전공2", "최초대학_복수/부전공", "최초대학_본교/분교", "최초대학_수동평점(4.5환산)",
            "최종대학(학사)", "최종대학(학사) 구분_전문대/대학교", "입학여부(최종대학(학사))", "졸업여부(최종대학(학사))",
            "최종대학(학사) 입학년월", "최종대학(학사) 졸업년월", "최종대학(학사)_주간/야간", "최종대학(학사)_전공1",
            "최종대학(학사)_전공2", "최종대학(학사)_복수/부전공", "최종대학(학사)_본교/분교",
            "최종대학(학사)_수동평점(4.5환산)",
            "최종대학(석/박)", "최종대학(석/박) 구분_석사/박사", "졸업여부(최종대학(석/박))", "최종대학(석/박) 입학년월",
            "최종대학(석/박) 졸업년월", "최종대학(석/박)_전공", "최종대학(석/박)_세부전공", "최종대학(석/박)_본교/분교",
            "최종대학(석/박)_수동평점(4.5환산)",
            "보훈", "장애",
            "외국어1", "외국어 회화 능력", "외국어2", "외국어2 회화 능력", "외국어3", "외국어3 회화 능력",
            "외국어4", "외국어4 회화 능력", "외국어5", "외국어5 회화 능력",
            "자격증1", "자격증2", "자격증3", "자격증4", "자격증5", "자격증6", "자격증7", "자격증8", "자격증9", "자격증10",
            "자격증 11",
            "경력1", "경력 1_재직여부", "경력2", "경력3", "경력4", "경력5", "경력6", "경력7", "경력8", "경력9",
            "병역사항", "병역기간", "현거주지", "이메일", "연락처");

    /** 셀 줄바꿈이 필요한 열(전형별 결과, 넘친 항목을 받는 마지막 번호 칸). */
    static final Set<String> WRAP_HEADERS = Set.of(
            "진행결과(전형별결과)", "외국어5", "외국어5 회화 능력", "자격증 11", "경력9");

    private static final DateTimeFormatter BIRTH_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy.MM");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final BigDecimal SCALE_4_5 = new BigDecimal("4.5");
    private static final String LINE_SEPARATOR = "\n";

    // 최종학력 = 최고 EducationLevel, 동률이면 id 큰 행 — 기존 엑셀·목록과 같은 규칙.
    private static final Comparator<ApplicationEducation> FINAL_EDUCATION = Comparator
            .comparingInt((ApplicationEducation education) -> education.getEducationLevel().ordinal())
            .thenComparing(ApplicationEducation::getId);

    // 입학일 순(없으면 맨 뒤), 같으면 id 순. min = 최초, max = 최종.
    private static final Comparator<ApplicationEducation> ADMISSION_ORDER = Comparator
            .comparing(ApplicationEducation::getAdmissionDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ApplicationEducation::getId);

    private static final Comparator<StageResult> STAGE_ORDER = Comparator
            .comparing((StageResult result) -> result.getStage().getStageOrder())
            .thenComparing(result -> result.getStage().getId());

    // 취득일 순(없으면 맨 뒤). 같으면 repository 순서(sortOrder, id)를 유지한다(stable sort).
    private static final Comparator<ApplicationCertificate> ACQUIRED_ORDER = Comparator
            .comparing(ApplicationCertificate::getAcquiredDate, Comparator.nullsLast(Comparator.naturalOrder()));

    // 최신 경력이 1. 입사일 늦은 순(없으면 맨 뒤), 같으면 repository 순서를 유지한다.
    private static final Comparator<ApplicationCareer> LATEST_CAREER = Comparator
            .comparing(ApplicationCareer::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()));

    private final ApplicationBasicInfoRepository basicInfoRepository;
    private final ApplicationMilitaryRepository militaryRepository;
    private final ApplicationEducationRepository educationRepository;
    private final ApplicationCareerRepository careerRepository;
    private final ApplicationCertificateRepository certificateRepository;
    private final ApplicationLanguageRepository languageRepository;
    private final StageResultRepository stageResultRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 한 페이지 base 행을 {@link #HEADERS} 순서의 셀 값 목록으로 만든다. 조립이 끝나면 영속성 컨텍스트를 비운다 —
     * 기존 지원현황 엑셀과 같은 이유와 주의사항({@link ApplicationExportRowAssembler#assemble}).
     */
    List<List<String>> assemble(List<ApplicationHrExportRow> rows, CommonCodeNames codes) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(ApplicationHrExportRow::applicationId).toList();
        Map<Long, ApplicationBasicInfo> basicInfos =
                single(basicInfoRepository.findByJobApplicationIdIn(ids), ApplicationBasicInfo::getJobApplication);
        Map<Long, ApplicationMilitary> militaries =
                single(militaryRepository.findByJobApplicationIdIn(ids), ApplicationMilitary::getJobApplication);
        Map<Long, List<ApplicationEducation>> educations = grouped(
                educationRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                ApplicationEducation::getJobApplication);
        Map<Long, List<ApplicationCareer>> careers = grouped(
                careerRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids), ApplicationCareer::getJobApplication);
        Map<Long, List<ApplicationCertificate>> certificates = grouped(
                certificateRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                ApplicationCertificate::getJobApplication);
        Map<Long, List<ApplicationLanguage>> languages = grouped(
                languageRepository.findByJobApplicationIdInOrderBySortOrderAscIdAsc(ids),
                ApplicationLanguage::getJobApplication);
        Map<Long, List<StageResult>> stageResults = grouped(
                stageResultRepository.findWithStageByJobApplicationIdIn(ids), StageResult::getJobApplication);

        List<List<String>> page = new ArrayList<>(rows.size());
        for (ApplicationHrExportRow row : rows) {
            Long id = row.applicationId();
            List<String> cells = row(
                    row,
                    basicInfos.get(id),
                    militaries.get(id),
                    educations.getOrDefault(id, List.of()),
                    careers.getOrDefault(id, List.of()),
                    certificates.getOrDefault(id, List.of()),
                    languages.getOrDefault(id, List.of()),
                    stageResults.getOrDefault(id, List.of()),
                    codes);
            page.add(cells.stream().map(ApplicationExportRowAssembler::truncate).toList());
        }
        entityManager.clear();
        return page;
    }

    private List<String> row(
            ApplicationHrExportRow row,
            ApplicationBasicInfo info,
            ApplicationMilitary military,
            List<ApplicationEducation> educations,
            List<ApplicationCareer> careers,
            List<ApplicationCertificate> certificates,
            List<ApplicationLanguage> languages,
            List<StageResult> stageResults,
            CommonCodeNames codes
    ) {
        List<String> cells = new ArrayList<>(HEADERS.size());
        cells.add(ApplicationPdfLabels.label(row.applicationType()));
        cells.add(text(row.jobPositionName()));
        cells.add(stageResults.stream().sorted(STAGE_ORDER)
                .map(result -> ApplicationExportRowAssembler.stageResult(result, ": "))
                .collect(Collectors.joining(LINE_SEPARATOR)));
        cells.add(joinNonBlank(" / ", row.jobTitle(), row.workLocationName()));
        cells.add(String.valueOf(row.applicationId()));
        // 이름·연락처: 기본정보 행이 있으면 그 값, 없으면 snapshot/계정 값 — 기존 엑셀·PDF 와 같은 규칙.
        cells.add(info != null ? text(info.getNameKorean()) : text(row.applicantName()));
        cells.add(gender(row.gender()));
        cells.add(info == null ? "" : format(info.getBirthDate(), BIRTH_DATE));
        cells.add(info == null ? "" : nationality(info, codes));

        ApplicationEducation finalEducation = educations.stream().max(FINAL_EDUCATION).orElse(null);
        cells.add(finalEducation == null ? "" : educationLevel(finalEducation.getEducationLevel()));
        cells.add(educations.stream()
                .filter(e -> e.getEducationLevel() == EducationLevel.HIGH_SCHOOL)
                .map(e -> text(e.getSchoolName()))
                .findFirst().orElse(""));

        List<ApplicationEducation> colleges = educations.stream()
                .filter(e -> e.getEducationLevel() == EducationLevel.COLLEGE
                        || e.getEducationLevel() == EducationLevel.UNIVERSITY)
                .toList();
        addFirstCollege(cells, colleges.stream().min(ADMISSION_ORDER).orElse(null), codes);
        addLastCollege(cells, colleges.stream().max(ADMISSION_ORDER).orElse(null), codes);
        addGraduate(cells, educations.stream()
                .filter(e -> e.getEducationLevel() == EducationLevel.MASTER
                        || e.getEducationLevel() == EducationLevel.DOCTOR)
                .max(ADMISSION_ORDER).orElse(null));

        cells.add(info == null ? "" : ApplicationExportRowAssembler.veteran(info));
        cells.add(info == null ? "" : ApplicationExportRowAssembler.disability(info, codes));

        for (List<ApplicationLanguage> slot : slots(languages, LANGUAGE_SLOTS)) {
            cells.add(lines(slot, l -> joinNonBlank("/", l.getLanguageName(), l.getTestName(), l.getScoreOrGrade())));
            cells.add(lines(slot, l -> text(l.getConversationalAbility())));
        }

        List<ApplicationCertificate> sortedCertificates = certificates.stream().sorted(ACQUIRED_ORDER).toList();
        for (List<ApplicationCertificate> slot : slots(sortedCertificates, CERTIFICATE_SLOTS)) {
            cells.add(lines(slot, c -> text(c.getCertificateName())));
        }

        List<ApplicationCareer> sortedCareers = careers.stream().sorted(LATEST_CAREER).toList();
        List<List<ApplicationCareer>> careerSlots = slots(sortedCareers, CAREER_SLOTS);
        for (int i = 0; i < careerSlots.size(); i++) {
            cells.add(lines(careerSlots.get(i), c -> text(c.getCompanyName())));
            if (i == 0) {
                cells.add(sortedCareers.isEmpty() ? "" : employment(sortedCareers.get(0)));
            }
        }

        cells.add(military == null ? "" : military(military));
        cells.add(military == null ? "" : militaryPeriod(military));
        cells.add(info == null ? "" : ApplicationExportRowAssembler.address(info));
        cells.add(info != null ? text(info.getEmail()) : text(row.email()));
        cells.add(info != null ? text(info.getMobilePhone()) : text(row.phoneNumber()));
        return cells;
    }

    /** 최초대학 10열: 학교·구분·입학·졸업·주야·전공1·전공2·복수/부전공·본교/분교·평점. */
    private void addFirstCollege(List<String> cells, ApplicationEducation e, CommonCodeNames codes) {
        if (e == null) {
            cells.addAll(blanks(10));
            return;
        }
        cells.add(text(e.getSchoolName()));
        cells.add(educationLevel(e.getEducationLevel()));
        cells.add(format(e.getAdmissionDate(), YEAR_MONTH));
        cells.add(format(e.getGraduationDate(), YEAR_MONTH));
        cells.add(ApplicationPdfLabels.label(e.getDayNightType()));
        cells.add(text(e.getMajorName()));
        cells.add(text(e.getAdditionalMajorName()));
        cells.add(codes.name("MAJOR_TYPE", e.getAdditionalMajorType()));
        cells.add(ApplicationPdfLabels.label(e.getCampusType()));
        cells.add(gradePoint(e));
    }

    /** 최종대학(학사) 12열: 최초대학 열에 입학여부·졸업여부가 더해진다. */
    private void addLastCollege(List<String> cells, ApplicationEducation e, CommonCodeNames codes) {
        if (e == null) {
            cells.addAll(blanks(12));
            return;
        }
        cells.add(text(e.getSchoolName()));
        cells.add(educationLevel(e.getEducationLevel()));
        cells.add(Boolean.TRUE.equals(e.getTransfer()) ? "편입" : "입학");
        cells.add(ApplicationPdfLabels.label(e.getGraduationStatus()));
        cells.add(format(e.getAdmissionDate(), YEAR_MONTH));
        cells.add(format(e.getGraduationDate(), YEAR_MONTH));
        cells.add(ApplicationPdfLabels.label(e.getDayNightType()));
        cells.add(text(e.getMajorName()));
        cells.add(text(e.getAdditionalMajorName()));
        cells.add(codes.name("MAJOR_TYPE", e.getAdditionalMajorType()));
        cells.add(ApplicationPdfLabels.label(e.getCampusType()));
        cells.add(gradePoint(e));
    }

    /** 최종대학(석/박) 9열: 학교·석사/박사·졸업여부·입학·졸업·전공·세부전공·본교/분교·평점. */
    private void addGraduate(List<String> cells, ApplicationEducation e) {
        if (e == null) {
            cells.addAll(blanks(9));
            return;
        }
        cells.add(text(e.getSchoolName()));
        cells.add(e.getEducationLevel() == EducationLevel.DOCTOR ? "박사" : "석사");
        cells.add(ApplicationPdfLabels.label(e.getGraduationStatus()));
        cells.add(format(e.getAdmissionDate(), YEAR_MONTH));
        cells.add(format(e.getGraduationDate(), YEAR_MONTH));
        cells.add(text(e.getMajorName()));
        cells.add(text(e.getAdditionalMajorName()));
        cells.add(ApplicationPdfLabels.label(e.getCampusType()));
        cells.add(gradePoint(e));
    }

    /** 양식 표기: 전문대학은 "전문대", 그 밖은 PDF 라벨(대학교·대학원(석사) 등). */
    private static String educationLevel(EducationLevel level) {
        return level == EducationLevel.COLLEGE ? "전문대" : ApplicationPdfLabels.label(level);
    }

    /** 전체 평점을 4.5 만점으로 환산한다. 값이 없거나 만점이 0 이하면 빈칸. */
    static String gradePoint(ApplicationEducation e) {
        BigDecimal point = e.getOverallGradePoint();
        BigDecimal max = e.getOverallMaxGradePoint();
        if (point == null || max == null || max.signum() <= 0) {
            return "";
        }
        BigDecimal converted = point.multiply(SCALE_4_5).divide(max, 2, RoundingMode.HALF_UP).stripTrailingZeros();
        return converted.toPlainString() + " / 4.5";
    }

    private static String gender(Gender gender) {
        if (gender == null) {
            return "";
        }
        return gender == Gender.MALE ? "남성" : "여성";
    }

    /** 내국인은 "내국인", 외국인은 국가명(없으면 "외국인"). */
    private static String nationality(ApplicationBasicInfo info, CommonCodeNames codes) {
        NationalityType type = info.getNationalityType();
        if (type == null) {
            return "";
        }
        if (type == NationalityType.DOMESTIC) {
            return ApplicationPdfLabels.label(type);
        }
        String country = codes.name("NATIONALITY", info.getCountryCode());
        return country.isBlank() ? ApplicationPdfLabels.label(type) : country;
    }

    private static String employment(ApplicationCareer career) {
        return Boolean.TRUE.equals(career.getCurrentlyEmployed()) ? "재직" : "퇴사";
    }

    /**
     * 군필은 "필/군별/계급/복무개월", 그 밖은 구분만. 미필·면제 사유는 기존 엑셀처럼 넣지 않는다.
     * 복무개월 = 시작일부터 종료일까지(종료일 포함) 꽉 찬 개월 수.
     */
    private static String military(ApplicationMilitary military) {
        MilitarySubjectType type = military.getMilitarySubjectType();
        String status = ApplicationPdfLabels.label(type);
        if (type != MilitarySubjectType.COMPLETED) {
            return status;
        }
        String months = military.getServiceStartDate() == null || military.getServiceEndDate() == null
                ? ""
                : String.valueOf(Period.between(military.getServiceStartDate(),
                        military.getServiceEndDate().plusDays(1)).toTotalMonths());
        return String.join("/", status, ApplicationPdfLabels.label(military.getMilitaryBranch()),
                ApplicationPdfLabels.label(military.getRank()), months);
    }

    private static String militaryPeriod(ApplicationMilitary military) {
        if (military.getServiceStartDate() == null && military.getServiceEndDate() == null) {
            return "";
        }
        return format(military.getServiceStartDate(), DATE) + "~" + format(military.getServiceEndDate(), DATE);
    }

    /** 항목을 {@code count} 칸에 하나씩 나눈다. 넘치는 항목은 마지막 칸에 모은다. */
    static <E> List<List<E>> slots(List<E> items, int count) {
        List<List<E>> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (i >= items.size()) {
                slots.add(List.of());
            } else if (i == count - 1) {
                slots.add(items.subList(i, items.size()));
            } else {
                slots.add(List.of(items.get(i)));
            }
        }
        return slots;
    }

    private static <E> String lines(List<E> items, Function<E, String> line) {
        return items.stream().map(line).collect(Collectors.joining(LINE_SEPARATOR));
    }

    private static String joinNonBlank(String separator, String... values) {
        return Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(separator));
    }

    private static List<String> blanks(int count) {
        return Collections.nCopies(count, "");
    }

    private static String format(LocalDate value, DateTimeFormatter formatter) {
        return value == null ? "" : value.format(formatter);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static <E> Map<Long, E> single(List<E> entities, Function<E, JobApplication> owner) {
        return entities.stream().collect(Collectors.toMap(e -> owner.apply(e).getId(), e -> e, (a, b) -> a));
    }

    private static <E> Map<Long, List<E>> grouped(List<E> entities, Function<E, JobApplication> owner) {
        return entities.stream().collect(Collectors.groupingBy(
                e -> owner.apply(e).getId(), LinkedHashMap::new, Collectors.toList()));
    }
}
