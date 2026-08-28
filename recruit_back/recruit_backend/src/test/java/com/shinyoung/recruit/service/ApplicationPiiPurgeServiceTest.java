package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import com.shinyoung.recruit.domain.entity.ApplicationAward;
import com.shinyoung.recruit.domain.entity.ApplicationCareer;
import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationGapPeriod;
import com.shinyoung.recruit.domain.entity.ApplicationLanguage;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewEvaluation;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.JobPostingQuestion;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.entity.StageResultCorrectionHistory;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAnswerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationAwardRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationGapPeriodRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.EmployeeRepository;
import com.shinyoung.recruit.domain.repository.InterviewEvaluationRepository;
import com.shinyoung.recruit.domain.repository.InterviewParticipantRepository;
import com.shinyoung.recruit.domain.repository.InterviewRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingQuestionRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultCorrectionHistoryRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.EvaluationGrade;
import com.shinyoung.recruit.enumeration.EvaluationRecommendation;
import com.shinyoung.recruit.enumeration.GapType;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.MilitaryBranch;
import com.shinyoung.recruit.enumeration.MilitaryRank;
import com.shinyoung.recruit.enumeration.MilitaryServiceType;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.service.JobApplicationService;
import com.shinyoung.recruit.dto.request.ApplicationCreateRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관계형 PII tombstone 의 field-level 계약 테스트(Phase 09d-1) — PII 인벤토리 §3~§7 분류표를
 * 테이블 단위로 고정한다. PLACEHOLDER/NULLIFY/HASH_ONLY/KEEP_TOMBSTONE 이 분류 그대로 적용되는지 검증.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationPiiPurgeServiceTest {

    private static final String PURGED = "__PURGED__";

    @Autowired private ApplicationPiiPurgeService applicationPiiPurgeService;
    @Autowired private JobPostingService jobPostingService;
    @Autowired private JobApplicationService jobApplicationService;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private JobPostingQuestionRepository jobPostingQuestionRepository;
    @Autowired private ApplicationBasicInfoRepository basicInfoRepository;
    @Autowired private ApplicationAnswerRepository answerRepository;
    @Autowired private ApplicationEducationRepository educationRepository;
    @Autowired private ApplicationCareerRepository careerRepository;
    @Autowired private ApplicationCertificateRepository certificateRepository;
    @Autowired private ApplicationLanguageRepository languageRepository;
    @Autowired private ApplicationMilitaryRepository militaryRepository;
    @Autowired private ApplicationAwardRepository awardRepository;
    @Autowired private ApplicationGapPeriodRepository gapPeriodRepository;
    @Autowired private StageRepository stageRepository;
    @Autowired private StageResultRepository stageResultRepository;
    @Autowired private StageResultCorrectionHistoryRepository stageResultCorrectionHistoryRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private InterviewRepository interviewRepository;
    @Autowired private InterviewParticipantRepository interviewParticipantRepository;
    @Autowired private InterviewEvaluationRepository interviewEvaluationRepository;
    @Autowired private AuditHmac auditHmac;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void 인벤토리_분류표대로_관계형_PII가_tombstone_된다() {
        // ---- fixture ----
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                "pii purge recruitment", "<p>content</p>",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)));
        jobPostingService.publish(jobPostingId);
        JobPosting posting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        Long positionId = posting.getJobPositions().stream()
                .sorted(Comparator.comparing(JobPosition::getSortOrder)).map(JobPosition::getId).findFirst().orElseThrow();

        Applicant applicant = new Applicant("pii-ci", HashUtil.sha256("pii-ci"));
        applicant.setLoginId("pii-purge-applicant");
        applicant.setName("홍길동");
        applicant.setUserName("홍길동");
        applicant.setPassword("encoded");
        applicant.setPhoneNumber("01012345678");
        applicant = applicantRepository.save(applicant);

        Long applicationId = jobApplicationService.create(
                applicant.getId(), new ApplicationCreateRequest(jobPostingId, positionId));
        JobApplication application = jobApplicationRepository.findById(applicationId).orElseThrow();

        JobPostingQuestion question = jobPostingQuestionRepository.save(JobPostingQuestion.createDirect(
                posting, "지원 동기?", null, QuestionCategory.GENERAL, QuestionAnswerType.SHORT_TEXT, true, null, 500, 1));
        answerRepository.save(ApplicationAnswer.create(application, question, "저는 홍길동이고 010-1234-5678 입니다"));

        educationRepository.save(ApplicationEducation.create(
                application, EducationLevel.UNIVERSITY, "서울대학교", "컴퓨터공학",
                "DOUBLE_MAJOR", "경영학", "딥러닝 추천시스템",
                LocalDate.of(2015, 3, 2), LocalDate.of(2019, 2, 25),
                GraduationStatus.GRADUATED, DayNightType.DAY, CampusType.MAIN, false, "KR", 1));
        careerRepository.save(ApplicationCareer.create(
                application, "이전회사", "개발팀", "대리", EmploymentType.FULL_TIME,
                LocalDate.of(2019, 3, 1), LocalDate.of(2023, 5, 31), null, false, 5000, "이직", 1));
        certificateRepository.save(ApplicationCertificate.create(
                application, "정보처리기사", "한국산업인력공단", LocalDate.of(2020, 5, 1),
                "12-345-678", LocalDate.of(2030, 5, 1), "합격", 1));
        languageRepository.save(ApplicationLanguage.create(
                application, "ENGLISH", "영어", "TOEIC", "TOEIC", "900", "상", LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 1, 1), "ETS", 1));
        militaryRepository.save(ApplicationMilitary.create(
                application, MilitarySubjectType.COMPLETED, MilitaryServiceType.ACTIVE_DUTY,
                MilitaryBranch.ARMY, MilitaryRank.SERGEANT,
                LocalDate.of(2013, 1, 1), LocalDate.of(2014, 10, 1), null));
        awardRepository.save(ApplicationAward.create(
                application, "최우수상", "한국정보과학회", LocalDate.of(2018, 11, 1), "졸업작품 수상", 1));
        gapPeriodRepository.save(ApplicationGapPeriod.create(
                application, LocalDate.of(2023, 6, 1), LocalDate.of(2024, 1, 1),
                GapType.CAREER, "건강 문제로 휴식", "상세 설명", 1));

        // 면접 평가(per-candidate comment) fixture.
        Stage stage = stageRepository.save(Stage.create(posting, "면접", StageType.DOCUMENT, 0, null, true));
        Employee interviewer = new Employee();
        interviewer.setLoginId("pii-interviewer");
        interviewer.setName("면접관");
        interviewer.setDeptName("인사팀");
        interviewer = employeeRepository.save(interviewer);
        Interview interview = interviewRepository.save(Interview.createDraft(
                posting, stage, "Group A", LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(5).plusHours(1),
                InterviewMethod.IN_PERSON, "본사", "회의실A", null, null));
        InterviewParticipant candidate = interviewParticipantRepository.save(
                InterviewParticipant.candidate(interview, application, 1));
        InterviewParticipant interviewerParticipant = interviewParticipantRepository.save(
                InterviewParticipant.interviewer(interview, interviewer, 1));
        InterviewEvaluation evaluation = InterviewEvaluation.initialize(interview, candidate, interviewerParticipant);
        evaluation.updateContent(EvaluationGrade.G, EvaluationRecommendation.YES, "지원자 홍길동은 인상적이었음");
        evaluation = interviewEvaluationRepository.save(evaluation);

        // StageResult 자유서술 + 정정 이력(9d-1 리뷰 Major 1 — PURGED 전 소거 대상).
        StageResult stageResult = StageResult.initialize(stage, application);
        stageResult.updateResult(StageResultStatus.PASSED, BigDecimal.valueOf(88),
                "홍길동 — 서류 우수, 연락처 010-1234-5678 확인", LocalDateTime.now(), "emp01");
        stageResult = stageResultRepository.save(stageResult);
        StageResultCorrectionHistory correction = stageResultCorrectionHistoryRepository.save(
                StageResultCorrectionHistory.create(
                        stageResult, LocalDateTime.now(), "emp01", "지원자 홍길동 이의제기 반영",
                        StageResultStatus.PENDING, StageResultStatus.PASSED,
                        null, BigDecimal.valueOf(88),
                        "이전 코멘트 — 홍길동", "새 코멘트", null, LocalDateTime.now()));

        basicInfoRepository.save(ApplicationBasicInfo.create(
                application, "홍길동", "Hong", NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01012345678", "01099998888", "hong@example.com",
                VeteranStatus.SUBJECT, "국가유공자", DisabilityStatus.NOT_SUBJECT, null, null,
                "06236", "서울시 강남구", "101동"));

        entityManager.flush();

        // ---- 실행 ----
        applicationPiiPurgeService.purgeRelationalPii(applicationId);
        entityManager.flush();
        entityManager.clear();

        // ---- 검증(인벤토리 분류 그대로) ----
        ApplicationAnswer purgedAnswer = answerRepository.findAll().stream()
                .filter(a -> a.getJobApplication().getId().equals(applicationId)).findFirst().orElseThrow();
        assertThat(purgedAnswer.getAnswerText()).isNull();
        assertThat(purgedAnswer.getQuestionTextSnapshot()).isEqualTo("지원 동기?"); // KEEP_TOMBSTONE

        ApplicationEducation education = educationRepository.findAll().get(0);
        assertThat(education.getSchoolName()).isEqualTo(PURGED);
        assertThat(education.getMajorName()).isNull();
        assertThat(education.getAdditionalMajorName()).isNull();
        assertThat(education.getThesisTitle()).isNull();
        assertThat(education.getAdditionalMajorType()).isEqualTo("DOUBLE_MAJOR");
        assertThat(education.getCountryCode()).isNull();
        assertThat(education.getAdmissionDate()).isNull(); // 안 A — 정확 날짜 보존 금지
        assertThat(education.getGraduationDate()).isNull();
        assertThat(education.getEducationLevel()).isEqualTo(EducationLevel.UNIVERSITY); // KEEP
        assertThat(education.getGraduationStatus()).isEqualTo(GraduationStatus.GRADUATED);

        ApplicationCareer career = careerRepository.findAll().get(0);
        assertThat(career.getCompanyName()).isEqualTo(PURGED);
        assertThat(career.getDepartmentName()).isNull();
        assertThat(career.getPositionTitle()).isNull();
        assertThat(career.getCurrentSalary()).isNull();
        assertThat(career.getResignationReason()).isNull();
        assertThat(career.getStartDate()).isNull();
        assertThat(career.getEndDate()).isNull();
        assertThat(career.getPromotionDate()).isNull();
        assertThat(career.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME); // KEEP

        ApplicationCertificate certificate = certificateRepository.findAll().get(0);
        assertThat(certificate.getCertificateName()).isEqualTo(PURGED);
        assertThat(certificate.getIssuingOrganization()).isEqualTo(PURGED);
        assertThat(certificate.getAcquiredDate()).isNull();
        assertThat(certificate.getExpiredDate()).isNull();
        assertThat(certificate.getScoreOrGrade()).isNull();
        // HASH_ONLY — 원문 제거, HMAC 만(dedup 능력 보존).
        assertThat(certificate.getCertificateNumber()).isEqualTo(auditHmac.hmacHex("CERT_NO:12-345-678"));
        assertThat(certificate.getCertificateNumber()).isNotEqualTo("12-345-678");

        ApplicationLanguage language = languageRepository.findAll().get(0);
        assertThat(language.getLanguageName()).isEqualTo(PURGED);
        assertThat(language.getTestName()).isEqualTo(PURGED);
        assertThat(language.getLanguageCode()).isNull();
        assertThat(language.getTestCode()).isNull();
        assertThat(language.getExamDate()).isNull();
        assertThat(language.getScoreOrGrade()).isNull();
        assertThat(language.getConversationalAbility()).isNull();
        assertThat(language.getExpiredDate()).isNull();
        assertThat(language.getIssuingOrganization()).isNull();

        ApplicationMilitary military = militaryRepository.findAll().get(0);
        assertThat(military.getServiceStartDate()).isNull();
        assertThat(military.getServiceEndDate()).isNull();
        assertThat(military.getNonServiceReason()).isNull();
        assertThat(military.getMilitarySubjectType()).isEqualTo(MilitarySubjectType.COMPLETED); // KEEP

        ApplicationAward award = awardRepository.findAll().get(0);
        assertThat(award.getAwardName()).isEqualTo(PURGED);
        assertThat(award.getAwardingOrganization()).isEqualTo(PURGED);
        assertThat(award.getAwardDate()).isNull();
        assertThat(award.getDescription()).isNull();

        ApplicationGapPeriod gapPeriod = gapPeriodRepository.findAll().get(0);
        assertThat(gapPeriod.getReason()).isEqualTo(PURGED);
        assertThat(gapPeriod.getStartDate()).isNull();
        assertThat(gapPeriod.getEndDate()).isNull();
        assertThat(gapPeriod.getDescription()).isNull();
        assertThat(gapPeriod.getGapType()).isEqualTo(GapType.CAREER); // KEEP

        // 평가 — per-candidate comment 만 NULLIFY, 평가 결과/Interview-level 텍스트는 보존(인벤토리 §7).
        InterviewEvaluation purgedEvaluation = interviewEvaluationRepository.findById(evaluation.getId()).orElseThrow();
        assertThat(purgedEvaluation.getComment()).isNull();
        assertThat(purgedEvaluation.getGrade()).isEqualTo(EvaluationGrade.G); // KEEP
        Interview reloadedInterview = interviewRepository.findById(interview.getId()).orElseThrow();
        assertThat(reloadedInterview.getLocationName()).isEqualTo("본사"); // interview-level OUT(공유 행)

        // StageResult 자유서술 NULLIFY — 결과/점수/decidedBy(직원)는 KEEP(9d-1 리뷰 Major 1).
        StageResult purgedResult = stageResultRepository.findById(stageResult.getId()).orElseThrow();
        assertThat(purgedResult.getComment()).isNull();
        assertThat(purgedResult.getResultStatus()).isEqualTo(StageResultStatus.PASSED); // KEEP
        assertThat(purgedResult.getDecidedBy()).isEqualTo("emp01"); // KEEP(직원)

        // 정정 이력 — reason PLACEHOLDER, comment 전후 스냅샷 NULLIFY, 상태/점수/correctedBy 는 KEEP.
        StageResultCorrectionHistory purgedCorrection =
                stageResultCorrectionHistoryRepository.findById(correction.getId()).orElseThrow();
        assertThat(purgedCorrection.getReason()).isEqualTo(PURGED);
        assertThat(purgedCorrection.getPreviousComment()).isNull();
        assertThat(purgedCorrection.getNewComment()).isNull();
        assertThat(purgedCorrection.getPreviousStatus()).isEqualTo(StageResultStatus.PENDING); // KEEP
        assertThat(purgedCorrection.getCorrectedBy()).isEqualTo("emp01"); // KEEP(직원)

        // BasicInfo — 암호화 컬럼은 placeholder 불가(복호화 시 깨짐), 전 PII 컬럼 null.
        ApplicationBasicInfo purgedBasicInfo = basicInfoRepository.findByJobApplicationId(applicationId).orElseThrow();
        assertThat(purgedBasicInfo.getNameKorean()).isNull();
        assertThat(purgedBasicInfo.getNameEnglish()).isNull();
        assertThat(purgedBasicInfo.getEmail()).isNull();
        assertThat(purgedBasicInfo.getMobilePhone()).isNull();
        assertThat(purgedBasicInfo.getEmergencyPhone()).isNull();
        assertThat(purgedBasicInfo.getBirthDate()).isNull();
        assertThat(purgedBasicInfo.getNationalityType()).isNull();
        assertThat(purgedBasicInfo.getCountryCode()).isNull();
        assertThat(purgedBasicInfo.getVeteranStatus()).isNull();
        assertThat(purgedBasicInfo.getVeteranType()).isNull();
        assertThat(purgedBasicInfo.getDisabilityStatus()).isNull();
        assertThat(purgedBasicInfo.getDisabilityGradeCode()).isNull();
        assertThat(purgedBasicInfo.getDisabilityTypeCode()).isNull();
        assertThat(purgedBasicInfo.getZipCode()).isNull();
        assertThat(purgedBasicInfo.getAddressBasic()).isNull();
        assertThat(purgedBasicInfo.getAddressDetail()).isNull();
    }
}
