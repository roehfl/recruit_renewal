package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.dto.response.AdminApplicationAnswerResponse;
import com.shinyoung.recruit.dto.response.AdminApplicationStageResultResponse;
import com.shinyoung.recruit.dto.response.AdminAwardResponse;
import com.shinyoung.recruit.dto.response.AdminCareerItemResponse;
import com.shinyoung.recruit.dto.response.AdminCareerResponse;
import com.shinyoung.recruit.dto.response.AdminCertificateResponse;
import com.shinyoung.recruit.dto.response.AdminEducationResponse;
import com.shinyoung.recruit.dto.response.AdminGapPeriodResponse;
import com.shinyoung.recruit.dto.response.AdminLanguageResponse;
import com.shinyoung.recruit.dto.response.AdminMilitaryResponse;
import com.shinyoung.recruit.dto.response.AdminSemesterGradeResponse;
import com.shinyoung.recruit.dto.response.ApplicationPdfView;
import com.shinyoung.recruit.dto.response.ApplicationPdfView.Field;
import com.shinyoung.recruit.dto.response.ApplicationPdfView.RecordRow;
import com.shinyoung.recruit.dto.response.ApplicationPdfView.Section;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 운영자용 Application PDF 생성 서비스(admin 전용, read-only). 지원서 양식 섹션을 그대로 미러한 표시 모델을
 * 만들어 {@link ApplicationPdfRenderer}로 PDF byte[]를 생성한다.
 *
 * <p>섹션 데이터는 {@link AdminApplicationSectionService}를 재사용한다(자격번호/병역 면제사유 마스킹 정책을 그대로 상속).
 * 기본정보의 연락처(name/phoneNumber/email)는 admin이 export/PDF에서 보는 PII surface다.
 * {@code ci}/{@code ciHash}/{@code password}는 어디에도 포함하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String EMPTY = "없음";

    private final JobApplicationRepository jobApplicationRepository;
    private final AdminApplicationSectionService sectionService;
    private final ApplicationPdfRenderer renderer;

    public ApplicationPdfDocument generate(Long applicationId) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new JobApplicationNotFoundException("지원서를 찾을 수 없습니다. id=" + applicationId));

        ApplicationPdfView view = new ApplicationPdfView(buildHeader(application), buildSections(applicationId));
        byte[] content = renderer.render(view);
        return new ApplicationPdfDocument(
                content,
                "application-" + applicationId + ".pdf",
                application.getJobPosting().getId(),
                application.getJobPosition().getId());
    }

    private ApplicationPdfView.Header buildHeader(JobApplication application) {
        Applicant applicant = application.getApplicant();
        return new ApplicationPdfView.Header(
                application.getId(),
                application.getApplicantNameSnapshot(),
                applicant.getPhoneNumber(),
                applicant.getEmail(),
                application.getJobPostingTitleSnapshot(),
                application.getJobPositionNameSnapshot(),
                str(application.getStatus()),
                str(application.getSubmittedAt()));
    }

    private List<Section> buildSections(Long applicationId) {
        List<Section> sections = new ArrayList<>();
        sections.add(educationSection(applicationId));
        sections.add(careerSection(applicationId));
        sections.add(certificateSection(applicationId));
        sections.add(languageSection(applicationId));
        sections.add(militarySection(applicationId));
        sections.add(awardSection(applicationId));
        sections.add(gapPeriodSection(applicationId));
        sections.add(answerSection(applicationId));
        sections.add(stageResultSection(applicationId));
        return sections;
    }

    private Section educationSection(Long applicationId) {
        List<RecordRow> rows = new ArrayList<>();
        for (AdminEducationResponse e : sectionService.getEducations(applicationId)) {
            List<Field> fields = new ArrayList<>(List.of(
                    field("학교명", e.schoolName()),
                    field("전공", e.majorName()),
                    field("학위", e.degreeName()),
                    field("학력수준", e.educationLevel()),
                    field("입학일", e.admissionDate()),
                    field("졸업일", e.graduationDate()),
                    field("졸업상태", e.graduationStatus()),
                    field("주야구분", e.dayNightType()),
                    field("캠퍼스", e.campusType()),
                    field("편입여부", e.transfer()),
                    field("국가코드", e.countryCode())));
            for (AdminSemesterGradeResponse g : e.semesterGrades()) {
                fields.add(field(
                        str(g.schoolYear()) + "학년 " + str(g.semester()) + "학기 성적",
                        "취득 " + str(g.earnedCredits())
                                + " / 평점 " + str(g.gradePoint()) + "/" + str(g.maxGradePoint())
                                + " (전공 " + str(g.majorGradePoint()) + "/" + str(g.majorMaxGradePoint()) + ")"));
            }
            rows.add(new RecordRow(fields));
        }
        return new Section("학력", rows, EMPTY);
    }

    private Section careerSection(Long applicationId) {
        AdminCareerResponse career = sectionService.getCareers(applicationId);
        List<RecordRow> rows = new ArrayList<>();
        for (AdminCareerItemResponse c : career.careers()) {
            rows.add(new RecordRow(List.of(
                    field("회사명", c.companyName()),
                    field("부서", c.departmentName()),
                    field("직위", c.positionTitle()),
                    field("고용형태", c.employmentType()),
                    field("입사일", c.startDate()),
                    field("퇴사일", c.endDate()),
                    field("재직중", c.currentlyEmployed()),
                    field("담당업무", c.responsibilities()),
                    field("퇴사사유", c.resignationReason()))));
        }
        return new Section("경력 (경력구분: " + str(career.careerType()) + ")", rows, EMPTY);
    }

    private Section certificateSection(Long applicationId) {
        List<RecordRow> rows = new ArrayList<>();
        for (AdminCertificateResponse c : sectionService.getCertificates(applicationId)) {
            rows.add(new RecordRow(List.of(
                    field("자격명", c.certificateName()),
                    field("발급기관", c.issuingOrganization()),
                    field("취득일", c.acquiredDate()),
                    field("자격번호", c.certificateNumberMasked()),
                    field("만료일", c.expiredDate()),
                    field("점수/등급", c.scoreOrGrade()))));
        }
        return new Section("자격", rows, EMPTY);
    }

    private Section languageSection(Long applicationId) {
        List<RecordRow> rows = new ArrayList<>();
        for (AdminLanguageResponse l : sectionService.getLanguages(applicationId)) {
            rows.add(new RecordRow(List.of(
                    field("언어", l.languageName()),
                    field("시험명", l.testName()),
                    field("점수", l.score()),
                    field("등급", l.grade()),
                    field("응시일", l.examDate()),
                    field("만료일", l.expiredDate()),
                    field("발급기관", l.issuingOrganization()))));
        }
        return new Section("어학", rows, EMPTY);
    }

    private Section militarySection(Long applicationId) {
        AdminMilitaryResponse m = sectionService.getMilitary(applicationId);
        List<RecordRow> rows = new ArrayList<>();
        if (m != null) {
            rows.add(new RecordRow(List.of(
                    field("대상구분", m.militarySubjectType()),
                    field("복무형태", m.serviceType()),
                    field("군별", m.militaryBranch()),
                    field("계급", m.rank()),
                    field("복무시작", m.serviceStartDate()),
                    field("복무종료", m.serviceEndDate()),
                    field("면제사유", m.exemptionReasonMasked()))));
        }
        return new Section("병역", rows, EMPTY);
    }

    private Section awardSection(Long applicationId) {
        List<RecordRow> rows = new ArrayList<>();
        for (AdminAwardResponse a : sectionService.getAwards(applicationId)) {
            rows.add(new RecordRow(List.of(
                    field("수상명", a.awardName()),
                    field("수여기관", a.awardingOrganization()),
                    field("수상일", a.awardDate()),
                    field("설명", a.description()))));
        }
        return new Section("수상", rows, EMPTY);
    }

    private Section gapPeriodSection(Long applicationId) {
        List<RecordRow> rows = new ArrayList<>();
        for (AdminGapPeriodResponse g : sectionService.getGapPeriods(applicationId)) {
            rows.add(new RecordRow(List.of(
                    field("시작일", g.startDate()),
                    field("종료일", g.endDate()),
                    field("구분", g.gapType()),
                    field("사유", g.reason()),
                    field("설명", g.description()))));
        }
        return new Section("공백기간", rows, EMPTY);
    }

    private Section answerSection(Long applicationId) {
        List<RecordRow> rows = new ArrayList<>();
        for (AdminApplicationAnswerResponse a : sectionService.getAnswers(applicationId)) {
            rows.add(new RecordRow(List.of(
                    field("질문", a.questionText()),
                    field("답변", a.answerText()))));
        }
        return new Section("질문답변", rows, EMPTY);
    }

    private Section stageResultSection(Long applicationId) {
        List<RecordRow> rows = new ArrayList<>();
        for (AdminApplicationStageResultResponse s : sectionService.getStageResults(applicationId)) {
            rows.add(new RecordRow(List.of(
                    field("전형", s.stageName()),
                    field("결과", s.resultStatus()),
                    field("점수", s.score()),
                    field("코멘트", s.comment()),
                    field("결정일시", s.decidedAt()))));
        }
        return new Section("전형결과", rows, EMPTY);
    }

    private Field field(String label, Object value) {
        return new Field(label, str(value));
    }

    private String str(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof java.time.LocalDate date) {
            return date.format(DATE);
        }
        if (value instanceof java.time.LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME);
        }
        if (value instanceof Boolean bool) {
            return bool ? "예" : "아니오";
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return String.valueOf(value);
    }
}
