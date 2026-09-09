package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.dto.response.AdminApplicationAnswerResponse;
import com.shinyoung.recruit.dto.response.AdminAttachmentResponse;
import com.shinyoung.recruit.dto.response.AdminAwardResponse;
import com.shinyoung.recruit.dto.response.AdminBasicInfoResponse;
import com.shinyoung.recruit.dto.response.AdminCareerItemResponse;
import com.shinyoung.recruit.dto.response.AdminCareerResponse;
import com.shinyoung.recruit.dto.response.AdminCertificateResponse;
import com.shinyoung.recruit.dto.response.AdminEducationResponse;
import com.shinyoung.recruit.dto.response.AdminGapPeriodResponse;
import com.shinyoung.recruit.dto.response.AdminLanguageResponse;
import com.shinyoung.recruit.dto.response.AdminMilitaryResponse;
import com.shinyoung.recruit.dto.response.AdminSemesterGradeResponse;
import com.shinyoung.recruit.dto.response.ApplicationPdfView;
import com.shinyoung.recruit.dto.response.ApplicationPdfView.BasicInfo;
import com.shinyoung.recruit.dto.response.ApplicationPdfView.Layout;
import com.shinyoung.recruit.dto.response.ApplicationPdfView.Row;
import com.shinyoung.recruit.dto.response.ApplicationPdfView.Section;
import com.shinyoung.recruit.dto.response.CommonCodeResponse;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.JobPostingType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 운영자용 Application PDF 생성 서비스(admin 전용, read-only). 관리자 지원서 상세 화면
 * ({@code Application.vue})의 섹션 구성과 순서를 그대로 미러한 표시 모델을 만들어
 * {@link ApplicationPdfRenderer}로 PDF byte[]를 생성한다.
 *
 * <p>섹션 데이터는 {@link AdminApplicationSectionService}를 재사용한다(자격번호/병역 사유 마스킹 정책을 그대로 상속).
 * enum은 {@link ApplicationPdfLabels}로, 공통코드 기반 값(국적/장애)은 {@link CommonCodeService}로 한글화한다.
 * 기본정보의 연락처(name/phoneNumber/email)는 admin이 export/PDF에서 보는 PII surface다.
 * {@code ci}/{@code ciHash}/{@code password}는 어디에도 포함하지 않는다.
 *
 * <p>첨부파일 목록과 전형결과는 PDF 범위 밖이다. 첨부는 증명사진을 고르기 위해서만 조회한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String EMPTY = "없음";
    private static final String DASH = "-";

    /** 화면 성적 영역의 안내 문구를 그대로 쓴다. */
    private static final String GRADE_NOTE = "※ 교환학생 및 계절학기 성적은 기재하지 않음";

    /** 화면과 동일하게 기본 8학기까지 한 표로 묶고, 그 이상은 다음 표로 넘긴다. */
    private static final int SEMESTERS_PER_TABLE = 8;

    private final JobApplicationRepository jobApplicationRepository;
    private final AdminApplicationSectionService sectionService;
    private final ApplicationPdfRenderer renderer;
    private final CommonCodeService commonCodeService;
    private final ApplicationPhotoLoader photoLoader;

    public ApplicationPdfDocument generate(Long applicationId) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. id=" + applicationId));

        AdminBasicInfoResponse basicInfo = sectionService.getBasicInfo(applicationId);
        ApplicationPdfView.Header header = buildHeader(application, basicInfo);

        // 첨부는 사진을 고르기 위해서만 읽는다. 첨부 목록 자체는 PDF 섹션에 넣지 않는다.
        List<AdminAttachmentResponse> attachments = sectionService.getAttachments(applicationId);
        String photoDataUri = photoLoader.loadPhotoDataUri(applicationId, attachments);

        ApplicationPdfView view = new ApplicationPdfView(
                header,
                buildBasicInfo(basicInfo, application, photoDataUri),
                buildSections(applicationId, application));

        return new ApplicationPdfDocument(
                renderer.render(view),
                buildFileName(header),
                application.getJobPosting().getId(),
                application.getJobPosition().getId());
    }

    /** zip 안에서 운영자가 구분할 수 있도록 수험번호(applicationId)와 이름을 쓴다. */
    private String buildFileName(ApplicationPdfView.Header header) {
        String name = sanitizeFileName(header.applicantName());
        if (name.isBlank()) {
            return header.applicationId() + ".pdf";
        }
        return header.applicationId() + "_" + name + ".pdf";
    }

    /** 경로 구분자/제어문자 등 파일명에 들어가면 안 되는 문자를 제거한다. */
    private String sanitizeFileName(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
    }

    private ApplicationPdfView.Header buildHeader(JobApplication application, AdminBasicInfoResponse basicInfo) {
        // BasicInfo row 가 존재하면 그것이 source of truth (파기로 필드가 null 이어도 fallback 하지 않는다).
        String name = basicInfo != null ? basicInfo.nameKorean() : application.getApplicantNameSnapshot();

        return new ApplicationPdfView.Header(
                application.getId(),
                name,
                application.getJobPostingTitleSnapshot(),
                ApplicationPdfLabels.label(application.getJobPosition().getApplicationType()),
                application.getJobPositionNameSnapshot(),
                application.getWorkLocationNameSnapshot(),
                str(application.getStatus()),
                dateTime(application.getSubmittedAt()));
    }

    private BasicInfo buildBasicInfo(
            AdminBasicInfoResponse basicInfo,
            JobApplication application,
            String photoDataUri
    ) {
        if (basicInfo == null) {
            // BasicInfo row 자체가 없을 때만 Applicant 값으로 fallback 한다(Phase 07e 동작 유지).
            // row 가 존재하는데 필드가 null 인 경우(파기)는 fallback 하지 않는다.
            Applicant applicant = application.getApplicant();
            return new BasicInfo(
                    photoDataUri,
                    application.getApplicantNameSnapshot(),
                    null,
                    phone(applicant.getPhoneNumber()),
                    "",
                    "",
                    "",
                    "",
                    "",
                    applicant.getEmail(),
                    "");
        }
        return new BasicInfo(
                photoDataUri,
                basicInfo.nameKorean(),
                basicInfo.nameEnglish(),
                phone(basicInfo.mobilePhone()),
                phone(basicInfo.emergencyPhone()),
                nationality(basicInfo),
                veteran(basicInfo),
                date(basicInfo.birthDate()),
                disability(basicInfo),
                basicInfo.email(),
                address(basicInfo));
    }

    /** 화면과 동일하게 "외국인 (국가명)" 형태로 합쳐 표시한다. */
    private String nationality(AdminBasicInfoResponse basicInfo) {
        NationalityType type = basicInfo.nationalityType();
        if (type == null) {
            return "";
        }
        if (type == NationalityType.DOMESTIC) {
            return ApplicationPdfLabels.label(type);
        }
        String country = codeName("NATIONALITY", basicInfo.countryCode());
        return country.isBlank()
                ? ApplicationPdfLabels.label(type)
                : ApplicationPdfLabels.label(type) + " (" + country + ")";
    }

    private String veteran(AdminBasicInfoResponse basicInfo) {
        VeteranStatus status = basicInfo.veteranStatus();
        if (status == null) {
            return "";
        }
        if (status == VeteranStatus.NOT_SUBJECT) {
            return ApplicationPdfLabels.label(status);
        }
        String type = nullToEmpty(basicInfo.veteranType());
        return type.isBlank()
                ? ApplicationPdfLabels.label(status)
                : ApplicationPdfLabels.label(status) + " (" + type + ")";
    }

    private String disability(AdminBasicInfoResponse basicInfo) {
        DisabilityStatus status = basicInfo.disabilityStatus();
        if (status == null) {
            return "";
        }
        if (status == DisabilityStatus.NOT_SUBJECT) {
            return ApplicationPdfLabels.label(status);
        }
        String grade = codeName("DISABILITY_GRADE", basicInfo.disabilityGradeCode());
        String type = codeName("DISABILITY_TYPE", basicInfo.disabilityTypeCode());
        return ApplicationPdfLabels.label(status) + " (등급: " + grade + " / 유형: " + type + ")";
    }

    private String address(AdminBasicInfoResponse basicInfo) {
        StringBuilder builder = new StringBuilder();
        if (hasText(basicInfo.zipCode())) {
            builder.append('(').append(basicInfo.zipCode()).append(") ");
        }
        if (hasText(basicInfo.addressBasic())) {
            builder.append(basicInfo.addressBasic());
        }
        if (hasText(basicInfo.addressDetail())) {
            builder.append(", ").append(basicInfo.addressDetail());
        }
        return builder.toString().trim();
    }

    /** 공통코드 표시명. 코드가 없거나 등록되지 않았으면 코드값을 그대로 노출한다(누락을 감추지 않는다). */
    private String codeName(String groupCode, String code) {
        if (!hasText(code)) {
            return "";
        }
        Map<String, String> codes = commonCodeService.getActiveCodes(groupCode).stream()
                .collect(Collectors.toMap(CommonCodeResponse::code, CommonCodeResponse::displayName, (a, b) -> a));
        return codes.getOrDefault(code, code);
    }

    /** 관리자 화면(Application.vue)의 섹션 순서를 그대로 따른다. */
    private List<Section> buildSections(Long applicationId, JobApplication application) {
        List<Section> sections = new ArrayList<>();
        sections.add(militarySection(applicationId));
        List<AdminEducationResponse> educations = sectionService.getEducations(applicationId);
        sections.add(educationSection(educations));
        sections.addAll(semesterGradeSections(educations, application));
        sections.add(careerSection(applicationId));
        sections.add(certificateSection(applicationId));
        sections.add(languageSection(applicationId));
        sections.add(awardSection(applicationId));
        sections.add(gapPeriodSection(applicationId));
        sections.add(answerSection(applicationId));
        // 전형결과는 설계상 "지원서 양식 섹션" 범위 밖이고 StageResult.comment 등 내부 운영 정보가 포함될 수 있어
        // 지원서 PDF에서는 제외한다(필요 시 별도 admin report로 분리).
        return sections;
    }

    private Section militarySection(Long applicationId) {
        AdminMilitaryResponse military = sectionService.getMilitary(applicationId);
        List<Row> rows = new ArrayList<>();
        if (military != null) {
            boolean completed = military.militarySubjectType() == MilitarySubjectType.COMPLETED;
            rows.add(new Row(List.of(
                    militaryStatus(military),
                    completed ? joinSpace(
                            ApplicationPdfLabels.label(military.militaryBranch()),
                            ApplicationPdfLabels.label(military.serviceType())) : DASH,
                    completed ? ApplicationPdfLabels.label(military.rank()) : DASH,
                    completed ? range(military.serviceStartDate(), military.serviceEndDate()) : DASH)));
        }
        return table("병역사항",
                List.of("군필여부", "군별", "계급", "복무기간"),
                List.of("15%", "30%", "15%", "40%"),
                rows);
    }

    /** 화면과 동일하게 미필/면제는 사유를 함께 노출한다(사유는 이미 마스킹된 값). */
    private String militaryStatus(AdminMilitaryResponse military) {
        MilitarySubjectType type = military.militarySubjectType();
        if (type == null) {
            return "";
        }
        String label = ApplicationPdfLabels.label(type);
        if (type == MilitarySubjectType.SUBJECT || type == MilitarySubjectType.EXEMPTED) {
            return label + " (사유: " + nullToEmpty(military.nonServiceReasonMasked()) + ")";
        }
        return label;
    }

    private Section educationSection(List<AdminEducationResponse> educations) {
        List<Row> rows = new ArrayList<>();
        for (AdminEducationResponse e : educations) {
            boolean highSchool = e.educationLevel() == EducationLevel.HIGH_SCHOOL;
            rows.add(new Row(List.of(
                    ApplicationPdfLabels.label(e.educationLevel()),
                    nullToEmpty(e.schoolName()),
                    date(e.admissionDate()),
                    date(e.graduationDate()),
                    ApplicationPdfLabels.label(e.graduationStatus()),
                    highSchool ? DASH : nullToEmpty(e.majorName()),
                    highSchool ? DASH : gradePoint(e.overallGradePoint(), e.overallMaxGradePoint()))));
        }
        return table(
                "학력사항",
                List.of("학교 구분", "학교명", "입학년월", "졸업년월", "졸업 구분", "전공", "평점 평균"),
                List.of(),
                rows);
    }

    /**
     * 학기별 성적. 화면(Application.vue)의 성적 영역 로직을 그대로 따른다.
     *
     * <ul>
     *   <li>공채(PUBLIC_RECRUITMENT) 공고에서만 노출한다.</li>
     *   <li>고등학교는 제외하고, 전문대학교 이상은 성적 입력이 없어도 학교마다 표를 만든다.</li>
     *   <li>1~8학기는 항상 출력하고 값이 없는 학기는 빈칸으로 둔다.</li>
     *   <li>5학년 이상 성적이 있으면 9~16학기 표를 덧붙이되, 열 라벨은 마지막 학기까지만 표시한다.</li>
     * </ul>
     */
    private List<Section> semesterGradeSections(List<AdminEducationResponse> educations, JobApplication application) {
        if (application.getJobPosting().getPostingType() != JobPostingType.PUBLIC_RECRUITMENT) {
            return List.of();
        }
        List<Section> sections = new ArrayList<>();
        for (AdminEducationResponse e : educations) {
            if (e.educationLevel() == EducationLevel.HIGH_SCHOOL) {
                continue;
            }
            Map<Integer, AdminSemesterGradeResponse> byNumber = e.semesterGrades().stream()
                    .collect(Collectors.toMap(this::semesterNumber, Function.identity(), (a, b) -> a));

            sections.add(gradeSection(
                    "■ " + nullToEmpty(e.schoolName()) + " 성적",
                    GRADE_NOTE,
                    byNumber, 1, SEMESTERS_PER_TABLE, SEMESTERS_PER_TABLE));

            if (hasAdditionalSemester(e)) {
                // 제목이 비면 템플릿이 머리글을 생략해 위 표에 이어 붙는다(화면의 한 표 4행 구조와 같은 모양).
                sections.add(gradeSection(
                        "", null,
                        byNumber, SEMESTERS_PER_TABLE + 1, SEMESTERS_PER_TABLE * 2, lastSemesterNumber(e)));
            }
        }
        return sections;
    }

    private Section gradeSection(
            String title,
            String note,
            Map<Integer, AdminSemesterGradeResponse> byNumber,
            int from,
            int to,
            int lastLabeled
    ) {
        List<String> columns = new ArrayList<>();
        List<String> widths = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (int number = from; number <= to; number++) {
            columns.add(number <= lastLabeled ? number + "학기" : "");
            widths.add("12.5%");
            values.add(semesterGradeText(byNumber.get(number)));
        }
        return new Section(title, note, Layout.GRADE, columns, widths, List.of(new Row(values)), EMPTY);
    }

    private boolean hasAdditionalSemester(AdminEducationResponse education) {
        return education.semesterGrades().stream()
                .anyMatch(grade -> grade.schoolYear() != null && grade.schoolYear() >= 5);
    }

    /** 화면과 동일하게 최소 8학기로 본다(그보다 앞에서 끝나도 기본 표는 8칸을 채운다). */
    private int lastSemesterNumber(AdminEducationResponse education) {
        return education.semesterGrades().stream()
                .mapToInt(this::semesterNumber)
                .max()
                .stream()
                .map(last -> Math.max(last, SEMESTERS_PER_TABLE))
                .findFirst()
                .orElse(SEMESTERS_PER_TABLE);
    }

    private int semesterNumber(AdminSemesterGradeResponse grade) {
        int schoolYear = grade.schoolYear() == null ? 1 : grade.schoolYear();
        int semester = grade.semester() == null ? 1 : grade.semester();
        return (schoolYear - 1) * 2 + semester;
    }

    /** 입력되지 않은 학기는 화면과 동일하게 빈칸으로 둔다. */
    private String semesterGradeText(AdminSemesterGradeResponse grade) {
        if (grade == null) {
            return "";
        }
        return gradePoint(grade.gradePoint(), grade.maxGradePoint());
    }

    private Section careerSection(Long applicationId) {
        AdminCareerResponse career = sectionService.getCareers(applicationId);
        List<Row> rows = new ArrayList<>();
        for (AdminCareerItemResponse c : career.careers()) {
            rows.add(new Row(List.of(
                    nullToEmpty(c.companyName()),
                    nullToEmpty(c.departmentName()),
                    c.endDate() == null
                            ? date(c.startDate()) + " ~ 재직중"
                            : range(c.startDate(), c.endDate()),
                    ApplicationPdfLabels.label(c.employmentType()),
                    positionWithPromotion(c),
                    c.currentSalary() == null ? "" : String.format("%,d", c.currentSalary()),
                    nullToEmpty(c.resignationReason()))));
        }
        return table(
                "경력사항",
                List.of("회사명(소재지)", "부서명(담당업무)", "근무기간", "고용형태",
                        "최종직급(승진일)", "연봉(만원)", "퇴직 사유"),
                // 헤더 문구가 길어 균등 분배로는 줄바꿈된다. 문구 길이에 맞춰 폭을 배분한다.
                List.of("17%", "19%", "12%", "10%", "17%", "13%", "12%"),
                rows);
    }

    private Section certificateSection(Long applicationId) {
        List<Row> rows = new ArrayList<>();
        for (AdminCertificateResponse c : sectionService.getCertificates(applicationId)) {
            rows.add(new Row(List.of(
                    nullToEmpty(c.certificateName()),
                    nullToEmpty(c.issuingOrganization()),
                    date(c.acquiredDate()),
                    nullToEmpty(c.certificateNumberMasked()))));
        }
        return table("자격사항",
                List.of("자격증", "발급기관", "취득일자", "자격증번호"),
                List.of("45%", "25%", "15%", "15%"),
                rows);
    }

    private Section languageSection(Long applicationId) {
        List<Row> rows = new ArrayList<>();
        for (AdminLanguageResponse l : sectionService.getLanguages(applicationId)) {
            rows.add(new Row(List.of(
                    nullToEmpty(l.languageName()),
                    nullToEmpty(l.testName()),
                    date(l.examDate()),
                    nullToEmpty(l.scoreOrGrade()),
                    nullToEmpty(l.conversationalAbility()))));
        }
        return table("어학",
                List.of("언어", "시험명", "응시일자", "점수/등급", "회화능력"),
                List.of("10%", "35%", "25%", "15%", "15%"),
                rows);
    }

    private Section awardSection(Long applicationId) {
        List<Row> rows = new ArrayList<>();
        for (AdminAwardResponse a : sectionService.getAwards(applicationId)) {
            rows.add(new Row(List.of(
                    nullToEmpty(a.awardName()),
                    nullToEmpty(a.awardingOrganization()),
                    yearMonth(a.awardDate()))));
        }
        return table("수상",
                List.of("수상명", "수여기관", "수상일자"),
                List.of("45%", "40%", "15%"),
                rows);
    }

    private Section gapPeriodSection(Long applicationId) {
        List<Row> rows = new ArrayList<>();
        for (AdminGapPeriodResponse g : sectionService.getGapPeriods(applicationId)) {
            rows.add(new Row(List.of(
                    ApplicationPdfLabels.label(g.gapType()),
                    yearMonth(g.startDate()) + " ~ " + yearMonth(g.endDate()),
                    nullToEmpty(g.reason()),
                    nullToEmpty(g.description()))));
        }
        return table("공백기간",
                List.of("구분", "기간", "사유", "상세 설명"),
                List.of("10%", "20%", "15%", "55%"),
                rows);
    }

    private Section answerSection(Long applicationId) {
        List<Row> rows = new ArrayList<>();
        List<AdminApplicationAnswerResponse> answers = sectionService.getAnswers(applicationId);
        for (AdminApplicationAnswerResponse a : answers) {
            int number = a.sortOrder() == null ? rows.size() + 1 : a.sortOrder() + 1;
            rows.add(new Row(List.of(
                    number + ". " + nullToEmpty(a.questionText()),
                    nullToEmpty(a.answerText()))));
        }
        return new Section("자기소개서", null, Layout.QA, List.of(), List.of(), rows, EMPTY);
    }

    private Section table(String title, List<String> columns, List<String> columnWidths, List<Row> rows) {
        return new Section(title, null, Layout.TABLE, columns, columnWidths, rows, EMPTY);
    }

    /** 화면이 "최종직급(승진일)" 한 항목으로 묶어 보여주므로 값도 같은 칸에 합친다. */
    private String positionWithPromotion(AdminCareerItemResponse career) {
        String position = nullToEmpty(career.positionTitle());
        String promotion = yearMonth(career.promotionDate());
        if (promotion.isBlank()) {
            return position;
        }
        return (position + " (" + promotion + ")").trim();
    }

    private String gradePoint(BigDecimal point, BigDecimal max) {
        if (point == null && max == null) {
            return "";
        }
        return plain(point) + " / " + plain(max);
    }

    private String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String range(LocalDate from, LocalDate to) {
        return date(from) + " ~ " + date(to);
    }

    private String joinSpace(String first, String second) {
        return (nullToEmpty(first) + " " + nullToEmpty(second)).trim();
    }

    /** 화면과 동일하게 하이픈을 넣어 표시한다. 숫자가 아니면 원본을 그대로 둔다. */
    private String phone(String value) {
        String digits = nullToEmpty(value).replaceAll("\\D", "");
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        }
        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
        }
        return nullToEmpty(value);
    }

    private String date(LocalDate value) {
        return value == null ? "" : value.format(DATE);
    }

    private String yearMonth(LocalDate value) {
        return value == null ? "" : value.format(YEAR_MONTH);
    }

    private String dateTime(java.time.LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String str(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Enum<?> enumValue) {
            return ApplicationPdfLabels.label(enumValue);
        }
        return String.valueOf(value);
    }
}
