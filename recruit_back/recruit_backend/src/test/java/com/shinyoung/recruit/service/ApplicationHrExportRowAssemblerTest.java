package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.ApplicationCareer;
import com.shinyoung.recruit.domain.entity.ApplicationCertificate;
import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.domain.entity.ApplicationLanguage;
import com.shinyoung.recruit.domain.entity.ApplicationMilitary;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.domain.entity.StageResult;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCareerRepository;
import com.shinyoung.recruit.domain.repository.ApplicationCertificateRepository;
import com.shinyoung.recruit.domain.repository.ApplicationEducationRepository;
import com.shinyoung.recruit.domain.repository.ApplicationLanguageRepository;
import com.shinyoung.recruit.domain.repository.ApplicationMilitaryRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.domain.repository.StageRepository;
import com.shinyoung.recruit.domain.repository.StageResultRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.ApplicationHrExportRow;
import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.Gender;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.MilitaryBranch;
import com.shinyoung.recruit.enumeration.MilitaryRank;
import com.shinyoung.recruit.enumeration.MilitaryServiceType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import jakarta.persistence.EntityManager;
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
 * 인사팀 양식 셀 값을 실제 JPA 조회로 고정한다. 공통코드는 테스트 DB 에 없으므로 코드값 fallback 으로 검증한다.
 */
@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicationHrExportRowAssemblerTest {

    @Autowired private ApplicationHrExportRowAssembler assembler;
    @Autowired private JobPostingService jobPostingService;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private ApplicationBasicInfoRepository basicInfoRepository;
    @Autowired private ApplicationMilitaryRepository militaryRepository;
    @Autowired private ApplicationEducationRepository educationRepository;
    @Autowired private ApplicationCareerRepository careerRepository;
    @Autowired private ApplicationCertificateRepository certificateRepository;
    @Autowired private ApplicationLanguageRepository languageRepository;
    @Autowired private StageRepository stageRepository;
    @Autowired private StageResultRepository stageResultRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private CommonCodeService commonCodeService;

    @Test
    void 기본정보_성별_전형결과_병역을_양식대로_쓴다() {
        JobApplication application = persistApplication("hr-basic");
        basicInfoRepository.save(ApplicationBasicInfo.create(
                application, "홍길동", null, NationalityType.FOREIGN, "ZZ", LocalDate.of(1993, 1, 1),
                "01011112222", null, "hong@example.com", VeteranStatus.NOT_SUBJECT, null,
                DisabilityStatus.NOT_SUBJECT, null, null, "04524", "서울시 중구 세종대로 1", "101호", null));
        militaryRepository.save(ApplicationMilitary.create(
                application, MilitarySubjectType.COMPLETED, MilitaryServiceType.ACTIVE_DUTY, MilitaryBranch.MARINE,
                MilitaryRank.SERGEANT, LocalDate.of(2012, 1, 16), LocalDate.of(2013, 10, 15), null));
        decide(application, "1차면접", StageType.FIRST_INTERVIEW, 2, StageResultStatus.HOLD);
        decide(application, "서류전형", StageType.DOCUMENT, 1, StageResultStatus.PASSED);

        List<String> cells = assembleOne(application, Gender.MALE);

        assertThat(cells).hasSize(80);
        assertThat(cell(cells, "지원구분")).isEqualTo("경력");
        assertThat(cell(cells, "지원분야")).isEqualTo("Backend");
        assertThat(cell(cells, "진행결과(전형별결과)")).isEqualTo("서류전형: 합격\n1차면접: 보류");
        assertThat(cell(cells, "직무/근무지")).isEqualTo("본사");
        assertThat(cell(cells, "수험번호")).isEqualTo(String.valueOf(application.getId()));
        assertThat(cell(cells, "이름")).isEqualTo("홍길동");
        assertThat(cell(cells, "성별")).isEqualTo("남성");
        assertThat(cell(cells, "생년월일")).isEqualTo("19930101");
        assertThat(cell(cells, "내/외국인")).isEqualTo("ZZ");
        assertThat(cell(cells, "보훈")).isEqualTo("비대상");
        assertThat(cell(cells, "장애")).isEqualTo("비대상");
        // 복무개월 = 종료일 포함 꽉 찬 개월 수(양식 예시 2012.01.16~2013.10.15 = 21).
        assertThat(cell(cells, "병역사항")).isEqualTo("필/해병대/병장/21");
        assertThat(cell(cells, "병역기간")).isEqualTo("2012.01.16~2013.10.15");
        assertThat(cell(cells, "현거주지")).isEqualTo("(04524) 서울시 중구 세종대로 1, 101호");
        assertThat(cell(cells, "이메일")).isEqualTo("hong@example.com");
        assertThat(cell(cells, "연락처")).isEqualTo("01011112222");
    }

    @Test
    void 대학은_입학일로_최초와_최종을_가르고_평점은_45로_환산한다() {
        JobApplication application = persistApplication("hr-edu");
        educationRepository.save(ApplicationEducation.create(
                application, EducationLevel.HIGH_SCHOOL, "한국고", null, null, null, null,
                LocalDate.of(2007, 3, 2), LocalDate.of(2010, 2, 10), GraduationStatus.GRADUATED,
                DayNightType.DAY, null, false, null, 0));
        // 저장 순서가 아니라 입학일로 가르는지 보려고 최종 대학을 먼저 저장한다.
        educationRepository.save(ApplicationEducation.create(
                application, EducationLevel.UNIVERSITY, "한국대", "경영학", "MT_001", "경제학", null,
                LocalDate.of(2012, 3, 2), LocalDate.of(2014, 2, 20), GraduationStatus.GRADUATED,
                DayNightType.DAY, CampusType.MAIN, true, null, null, null,
                new BigDecimal("3.60"), new BigDecimal("4.0"), null, null, 1));
        educationRepository.save(ApplicationEducation.create(
                application, EducationLevel.COLLEGE, "한국전문대", "디자인", null, null, null,
                LocalDate.of(2010, 3, 2), LocalDate.of(2012, 2, 20), GraduationStatus.GRADUATED,
                DayNightType.NIGHT, CampusType.BRANCH, false, null, null, null,
                new BigDecimal("4.2"), new BigDecimal("4.5"), null, null, 2));
        educationRepository.save(ApplicationEducation.create(
                application, EducationLevel.MASTER, "한국대학원", "경영학", "MT_003", "재무", null,
                LocalDate.of(2015, 3, 2), LocalDate.of(2017, 2, 20), GraduationStatus.EXPECTED,
                null, CampusType.MAIN, false, null, null, null,
                new BigDecimal("90"), new BigDecimal("100"), null, null, 3));

        List<String> cells = assembleOne(application, null);

        assertThat(cell(cells, "최종학력")).isEqualTo("대학원(석사)");
        assertThat(cell(cells, "고교")).isEqualTo("한국고");

        assertThat(cell(cells, "최초대학")).isEqualTo("한국전문대");
        assertThat(cell(cells, "최초대학 구분_전문대/대학교")).isEqualTo("전문대");
        assertThat(cell(cells, "최초대학 입학년월")).isEqualTo("2010.03");
        assertThat(cell(cells, "최초대학 졸업년월")).isEqualTo("2012.02");
        assertThat(cell(cells, "최초대학_주간/야간")).isEqualTo("야간");
        assertThat(cell(cells, "최초대학_전공1")).isEqualTo("디자인");
        assertThat(cell(cells, "최초대학_본교/분교")).isEqualTo("분교");
        assertThat(cell(cells, "최초대학_수동평점(4.5환산)")).isEqualTo("4.2 / 4.5");

        assertThat(cell(cells, "최종대학(학사)")).isEqualTo("한국대");
        assertThat(cell(cells, "최종대학(학사) 구분_전문대/대학교")).isEqualTo("대학교");
        assertThat(cell(cells, "입학여부(최종대학(학사))")).isEqualTo("편입");
        assertThat(cell(cells, "졸업여부(최종대학(학사))")).isEqualTo("졸업");
        assertThat(cell(cells, "최종대학(학사)_전공1")).isEqualTo("경영학");
        assertThat(cell(cells, "최종대학(학사)_전공2")).isEqualTo("경제학");
        assertThat(cell(cells, "최종대학(학사)_복수/부전공")).isEqualTo("MT_001");
        assertThat(cell(cells, "최종대학(학사)_본교/분교")).isEqualTo("본교");
        // 3.60 / 4.0 → 4.05
        assertThat(cell(cells, "최종대학(학사)_수동평점(4.5환산)")).isEqualTo("4.05 / 4.5");

        assertThat(cell(cells, "최종대학(석/박)")).isEqualTo("한국대학원");
        assertThat(cell(cells, "최종대학(석/박) 구분_석사/박사")).isEqualTo("석사");
        assertThat(cell(cells, "졸업여부(최종대학(석/박))")).isEqualTo("졸업예정");
        assertThat(cell(cells, "최종대학(석/박) 입학년월")).isEqualTo("2015.03");
        assertThat(cell(cells, "최종대학(석/박)_전공")).isEqualTo("경영학");
        assertThat(cell(cells, "최종대학(석/박)_세부전공")).isEqualTo("재무");
        // 90 / 100 → 4.05
        assertThat(cell(cells, "최종대학(석/박)_수동평점(4.5환산)")).isEqualTo("4.05 / 4.5");
    }

    @Test
    void 자격증은_취득일순_경력은_최신순이고_넘치는_항목은_마지막_칸에_모은다() {
        JobApplication application = persistApplication("hr-multi");
        for (int i = 0; i < 6; i++) {
            languageRepository.save(ApplicationLanguage.create(application, "L" + i, "언어" + i, "T" + i, "시험" + i,
                    String.valueOf(900 + i), "중", null, null, null, null, i));
        }
        // sortOrder 와 반대로 취득일을 준다 → 취득일 순이면 역순으로 나온다.
        for (int i = 0; i < 12; i++) {
            certificateRepository.save(ApplicationCertificate.create(application, "자격" + i, "발급기관",
                    LocalDate.of(2020, 1, 1).minusDays(i), null, null, null, i));
        }
        // 입력 순서 0..9 는 입사일 오름차순 → 최신순이면 역순으로 나온다. 가장 최신(9)은 재직중.
        for (int i = 0; i < 10; i++) {
            careerRepository.save(ApplicationCareer.create(application, "회사" + i + "(여의도)", null, null,
                    EmploymentType.FULL_TIME, LocalDate.of(2010 + i, 1, 1), i == 9 ? null : LocalDate.of(2010 + i, 12, 31),
                    null, i == 9, null, null, i));
        }

        List<String> cells = assembleOne(application, null);

        assertThat(cell(cells, "외국어1")).isEqualTo("언어0/시험0/900");
        assertThat(cell(cells, "외국어 회화 능력")).isEqualTo("중");
        assertThat(cell(cells, "외국어5")).isEqualTo("언어4/시험4/904\n언어5/시험5/905");
        assertThat(cell(cells, "외국어5 회화 능력")).isEqualTo("중\n중");

        assertThat(cell(cells, "자격증1")).isEqualTo("자격11");
        assertThat(cell(cells, "자격증10")).isEqualTo("자격2");
        assertThat(cell(cells, "자격증 11")).isEqualTo("자격1\n자격0");

        // 경력 근무지는 회사명에 입력한 그대로 보여준다.
        assertThat(cell(cells, "경력1")).isEqualTo("회사9(여의도)");
        assertThat(cell(cells, "경력 1_재직여부")).isEqualTo("재직");
        assertThat(cell(cells, "경력2")).isEqualTo("회사8(여의도)");
        assertThat(cell(cells, "경력9")).isEqualTo("회사1(여의도)\n회사0(여의도)");
    }

    @Test
    void 섹션이_없으면_80칸을_빈칸으로_채운다() {
        JobApplication application = persistApplication("hr-empty");

        List<String> cells = assembleOne(application, null);

        assertThat(cells).hasSize(80);
        // 기본정보가 없으면 지원 당시 이름 snapshot·계정 연락처로 대신한다.
        assertThat(cell(cells, "이름")).isEqualTo("스냅샷");
        assertThat(cell(cells, "이메일")).isEqualTo("acct@example.com");
        assertThat(cell(cells, "성별")).isEmpty();
        assertThat(cell(cells, "최초대학")).isEmpty();
        assertThat(cell(cells, "최종대학(석/박)_수동평점(4.5환산)")).isEmpty();
        assertThat(cell(cells, "경력 1_재직여부")).isEmpty();
        assertThat(cell(cells, "병역사항")).isEmpty();
    }

    @Test
    void 넘치지_않으면_칸마다_하나씩_나눈다() {
        assertThat(ApplicationHrExportRowAssembler.slots(List.of("a", "b"), 3))
                .containsExactly(List.of("a"), List.of("b"), List.of());
        assertThat(ApplicationHrExportRowAssembler.slots(List.of("a", "b", "c", "d"), 3))
                .containsExactly(List.of("a"), List.of("b"), List.of("c", "d"));
    }

    private List<String> assembleOne(JobApplication application, Gender gender) {
        // assembler 는 조립 후 영속성 컨텍스트를 비운다 — 그 전에 시드를 DB 로 내보낸다.
        entityManager.flush();
        ApplicationHrExportRow row = new ApplicationHrExportRow(
                application.getId(), "스냅샷", "01000000000", "acct@example.com",
                JobPositionApplicationType.EXPERIENCED, "Backend", null, "본사", gender);
        return assembler.assemble(List.of(row), new CommonCodeNames(commonCodeService)).get(0);
    }

    private static String cell(List<String> cells, String header) {
        int index = ApplicationHrExportRowAssembler.HEADERS.indexOf(header);
        assertThat(index).as("header " + header).isNotNegative();
        return cells.get(index);
    }

    private JobApplication persistApplication(String loginId) {
        Long jobPostingId = jobPostingService.create(new JobPostingCreateRequest(
                loginId + " 공고",
                "<p>content</p>",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 30, 18, 0),
                List.of(new JobPositionRequest("Backend", 1)),
                new ApplicationFormConfigRequest(false, false, false, false, false, false, false)));
        JobPosting jobPosting = jobPostingRepository.findDetailById(jobPostingId).orElseThrow();
        JobPosition jobPosition = jobPosting.getJobPositions().stream()
                .min(Comparator.comparing(JobPosition::getSortOrder))
                .orElseThrow();

        Applicant applicant = new Applicant(HashUtil.sha256(loginId + "-ci"));
        applicant.setLoginId(loginId);
        applicant.setName("스냅샷");
        applicant.setUserName("스냅샷");
        applicant.setPassword("encoded-password");
        applicant.setPhoneNumber("01000000000");
        applicant.setEmail(loginId + "@example.com");
        applicantRepository.save(applicant);

        JobApplication application = JobApplication.create(
                applicant, jobPosting, jobPosition, "스냅샷", jobPosting.getTitle(), jobPosition.getPositionName());
        application.submit(LocalDateTime.of(2026, 5, 10, 10, 0));
        return jobApplicationRepository.save(application);
    }

    private void decide(JobApplication application, String stageName, StageType type, int order, StageResultStatus status) {
        Stage stage = stageRepository.save(Stage.create(
                application.getJobPosting(), stageName, type, order, LocalDateTime.of(2026, 7, 1, 10, 0), false));
        StageResult result = StageResult.initialize(stage, application);
        result.updateResult(status, null, null, LocalDateTime.of(2026, 6, 1, 10, 0), "tester");
        stageResultRepository.save(result);
    }
}
