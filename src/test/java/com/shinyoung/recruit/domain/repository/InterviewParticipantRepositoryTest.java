package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.common.crypto.CryptoHolder;
import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.config.CryptoConfig;
import com.shinyoung.recruit.config.JpaConfig;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.Employee;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.InterviewParticipant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewParticipantRole;
import com.shinyoung.recruit.enumeration.InterviewParticipantStatus;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CryptoConfig.class, JpaConfig.class, CryptoHolder.class})
class InterviewParticipantRepositoryTest {

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private InterviewParticipantRepository participantRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void candidate와_interviewer를_저장하고_조회한다() {
        Interview interview = saveInterview();
        JobApplication application = saveApplication(interview.getJobPosting());
        Employee employee = saveEmployee();
        InterviewParticipant candidate = participantRepository.save(
                InterviewParticipant.candidate(interview, application, 1)
        );
        InterviewParticipant interviewer = participantRepository.saveAndFlush(
                InterviewParticipant.interviewer(interview, employee, 2)
        );

        List<InterviewParticipant> participants = participantRepository.findByInterviewIdOrderByRoleAscSortOrderAscIdAsc(
                interview.getId()
        );

        assertThat(participants).extracting(InterviewParticipant::getId)
                .containsExactly(candidate.getId(), interviewer.getId());
    }

    @Test
    void existsByInterviewIdAndRoleAndJobApplicationId는_candidate_중복_확인에_사용할_수_있다() {
        Interview interview = saveInterview();
        JobApplication application = saveApplication(interview.getJobPosting());
        participantRepository.saveAndFlush(InterviewParticipant.candidate(interview, application, 1));

        boolean exists = participantRepository.existsByInterviewIdAndRoleAndJobApplicationId(
                interview.getId(),
                InterviewParticipantRole.CANDIDATE,
                application.getId()
        );

        assertThat(exists).isTrue();
    }

    @Test
    void existsByInterviewIdAndRoleAndEmployeeId는_interviewer_중복_확인에_사용할_수_있다() {
        Interview interview = saveInterview();
        Employee employee = saveEmployee();
        participantRepository.saveAndFlush(InterviewParticipant.interviewer(interview, employee, 1));

        boolean exists = participantRepository.existsByInterviewIdAndRoleAndEmployeeId(
                interview.getId(),
                InterviewParticipantRole.INTERVIEWER,
                employee.getId()
        );

        assertThat(exists).isTrue();
    }

    @Test
    void findByJobApplicationIdAndRoleAndParticipantStatus는_지원자_배정_조회에_사용할_수_있다() {
        Interview interview = saveInterview();
        JobApplication application = saveApplication(interview.getJobPosting());
        InterviewParticipant participant = participantRepository.saveAndFlush(
                InterviewParticipant.candidate(interview, application, 1)
        );

        List<InterviewParticipant> participants = participantRepository.findByJobApplicationIdAndRoleAndParticipantStatus(
                application.getId(),
                InterviewParticipantRole.CANDIDATE,
                InterviewParticipantStatus.ASSIGNED
        );

        assertThat(participants).extracting(InterviewParticipant::getId)
                .containsExactly(participant.getId());
    }

    @Test
    void findByEmployeeIdAndRoleAndParticipantStatus는_면접관_배정_조회에_사용할_수_있다() {
        Interview interview = saveInterview();
        Employee employee = saveEmployee();
        InterviewParticipant participant = participantRepository.saveAndFlush(
                InterviewParticipant.interviewer(interview, employee, 1)
        );

        List<InterviewParticipant> participants = participantRepository.findByEmployeeIdAndRoleAndParticipantStatus(
                employee.getId(),
                InterviewParticipantRole.INTERVIEWER,
                InterviewParticipantStatus.ASSIGNED
        );

        assertThat(participants).extracting(InterviewParticipant::getId)
                .containsExactly(participant.getId());
    }

    @Test
    void existsConfirmedTimeCollision_detects_candidate_and_interviewer_overlap() {
        Interview confirmed = saveInterview();
        confirmed.confirm();
        JobApplication application = saveApplication(confirmed.getJobPosting());
        Employee employee = saveEmployee();
        participantRepository.save(InterviewParticipant.candidate(confirmed, application, 1));
        participantRepository.saveAndFlush(InterviewParticipant.interviewer(confirmed, employee, 1));

        Interview overlapping = interviewRepository.saveAndFlush(Interview.createDraft(
                confirmed.getJobPosting(),
                confirmed.getStage(),
                "2Group",
                start().plusMinutes(30),
                start().plusHours(1).plusMinutes(30),
                InterviewMethod.IN_PERSON,
                "Office",
                null,
                null,
                null
        ));

        assertThat(participantRepository.existsCandidateConfirmedTimeCollision(
                application.getId(),
                overlapping.getId(),
                overlapping.getStartDateTime(),
                overlapping.getEndDateTime()
        )).isTrue();
        assertThat(participantRepository.existsInterviewerConfirmedTimeCollision(
                employee.getId(),
                overlapping.getId(),
                overlapping.getStartDateTime(),
                overlapping.getEndDateTime()
        )).isTrue();
    }

    @Test
    void findVisibleApplicantInterviewParticipants_filters_applicant_candidate_assignment_visibility() {
        Interview confirmed = saveInterview();
        confirmed.confirm();
        interviewRepository.saveAndFlush(confirmed);
        JobPosting jobPosting = confirmed.getJobPosting();
        Stage stage = confirmed.getStage();
        JobApplication application = saveApplication(jobPosting);
        JobApplication otherApplication = saveApplication(jobPosting);
        Interview cancelled = saveInterview(jobPosting, stage, "Cancelled", start().plusHours(2), InterviewStatus.CANCELLED);
        Interview draft = saveInterview(jobPosting, stage, "Draft", start().plusHours(4), InterviewStatus.DRAFT);
        Interview otherApplicantInterview = saveInterview(jobPosting, stage, "Other", start().plusHours(6), InterviewStatus.CONFIRMED);
        Interview cancelledParticipantInterview = saveInterview(jobPosting, stage, "Participant", start().plusHours(8), InterviewStatus.CONFIRMED);
        Interview interviewerOnlyInterview = saveInterview(jobPosting, stage, "Interviewer", start().plusHours(10), InterviewStatus.CONFIRMED);
        participantRepository.save(InterviewParticipant.candidate(confirmed, application, 1));
        participantRepository.save(InterviewParticipant.candidate(cancelled, application, 1));
        participantRepository.save(InterviewParticipant.candidate(draft, application, 1));
        participantRepository.save(InterviewParticipant.candidate(otherApplicantInterview, otherApplication, 1));
        InterviewParticipant cancelledParticipant = InterviewParticipant.candidate(cancelledParticipantInterview, application, 1);
        cancelledParticipant.cancel();
        participantRepository.save(cancelledParticipant);
        participantRepository.saveAndFlush(InterviewParticipant.interviewer(interviewerOnlyInterview, saveEmployee(), 1));

        List<InterviewParticipant> participants = participantRepository.findVisibleApplicantInterviewParticipants(
                application.getApplicant().getId(),
                List.of(InterviewStatus.CONFIRMED, InterviewStatus.CANCELLED),
                null,
                null,
                null
        );
        List<InterviewParticipant> applicationParticipants = participantRepository.findVisibleApplicationInterviewParticipants(
                application.getApplicant().getId(),
                application.getId(),
                List.of(InterviewStatus.CONFIRMED, InterviewStatus.CANCELLED),
                InterviewStatus.CONFIRMED,
                start().minusMinutes(1),
                start().plusHours(1).plusMinutes(1)
        );

        assertThat(participants).extracting(participant -> participant.getInterview().getId())
                .containsExactly(confirmed.getId(), cancelled.getId());
        assertThat(applicationParticipants).extracting(participant -> participant.getInterview().getId())
                .containsExactly(confirmed.getId());
        assertThat(participantRepository.findVisibleApplicantInterviewParticipant(
                application.getApplicant().getId(),
                confirmed.getId(),
                List.of(InterviewStatus.CONFIRMED, InterviewStatus.CANCELLED)
        )).isPresent();
        assertThat(participantRepository.findVisibleApplicantInterviewParticipant(
                application.getApplicant().getId(),
                draft.getId(),
                List.of(InterviewStatus.CONFIRMED, InterviewStatus.CANCELLED)
        )).isEmpty();
    }

    @Test
    void findVisibleInterviewerInterviewParticipants_filters_employee_interviewer_assignment_visibility() {
        Interview confirmed = saveInterview();
        confirmed.confirm();
        interviewRepository.saveAndFlush(confirmed);
        JobPosting jobPosting = confirmed.getJobPosting();
        Stage stage = confirmed.getStage();
        Employee employee = saveEmployee();
        Employee otherEmployee = saveEmployee();
        JobApplication application = saveApplication(jobPosting);
        Interview cancelled = saveInterview(jobPosting, stage, "Cancelled", start().plusHours(2), InterviewStatus.CANCELLED);
        Interview draft = saveInterview(jobPosting, stage, "Draft", start().plusHours(4), InterviewStatus.DRAFT);
        Interview otherEmployeeInterview = saveInterview(jobPosting, stage, "Other", start().plusHours(6), InterviewStatus.CONFIRMED);
        Interview cancelledParticipantInterview = saveInterview(jobPosting, stage, "Participant", start().plusHours(8), InterviewStatus.CONFIRMED);
        Interview candidateOnlyInterview = saveInterview(jobPosting, stage, "Candidate", start().plusHours(10), InterviewStatus.CONFIRMED);
        participantRepository.save(InterviewParticipant.interviewer(confirmed, employee, 1));
        participantRepository.save(InterviewParticipant.candidate(confirmed, application, 1));
        participantRepository.save(InterviewParticipant.interviewer(cancelled, employee, 1));
        participantRepository.save(InterviewParticipant.interviewer(draft, employee, 1));
        participantRepository.save(InterviewParticipant.interviewer(otherEmployeeInterview, otherEmployee, 1));
        InterviewParticipant cancelledParticipant = InterviewParticipant.interviewer(cancelledParticipantInterview, employee, 1);
        cancelledParticipant.cancel();
        participantRepository.save(cancelledParticipant);
        InterviewParticipant cancelledCandidate = InterviewParticipant.candidate(confirmed, saveApplication(jobPosting), 2);
        cancelledCandidate.cancel();
        participantRepository.save(cancelledCandidate);
        participantRepository.saveAndFlush(InterviewParticipant.candidate(candidateOnlyInterview, application, 1));

        List<InterviewParticipant> participants = participantRepository.findVisibleInterviewerInterviewParticipants(
                employee.getId(),
                List.of(InterviewStatus.CONFIRMED, InterviewStatus.CANCELLED),
                null,
                null,
                null
        );
        List<InterviewParticipant> candidates = participantRepository.findAssignedCandidatesByInterviewId(confirmed.getId());

        assertThat(participants).extracting(participant -> participant.getInterview().getId())
                .containsExactly(confirmed.getId(), cancelled.getId());
        assertThat(participantRepository.findVisibleInterviewerInterviewParticipant(
                employee.getId(),
                confirmed.getId(),
                List.of(InterviewStatus.CONFIRMED, InterviewStatus.CANCELLED)
        )).isPresent();
        assertThat(participantRepository.findVisibleInterviewerInterviewParticipant(
                employee.getId(),
                draft.getId(),
                List.of(InterviewStatus.CONFIRMED, InterviewStatus.CANCELLED)
        )).isEmpty();
        assertThat(candidates).extracting(candidate -> candidate.getJobApplication().getId())
                .containsExactly(application.getId());
        assertThat(participantRepository.countAssignedCandidatesByInterviewId(confirmed.getId())).isEqualTo(1);
    }

    private Interview saveInterview() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = stageRepository.saveAndFlush(
                Stage.create(jobPosting, "1차 면접", StageType.FIRST_INTERVIEW, 1, null, false)
        );
        return interviewRepository.saveAndFlush(Interview.createDraft(
                jobPosting,
                stage,
                "1조",
                start(),
                start().plusHours(1),
                InterviewMethod.IN_PERSON,
                "본사",
                null,
                null,
                null
        ));
    }

    private Interview saveInterview(
            JobPosting jobPosting,
            Stage stage,
            String groupName,
            LocalDateTime startDateTime,
            InterviewStatus status
    ) {
        Interview interview = Interview.createDraft(
                jobPosting,
                stage,
                groupName,
                startDateTime,
                startDateTime.plusHours(1),
                InterviewMethod.IN_PERSON,
                "Office",
                null,
                null,
                null
        );
        if (status == InterviewStatus.CONFIRMED) {
            interview.confirm();
        }
        if (status == InterviewStatus.CANCELLED) {
            interview.confirm();
            interview.cancel();
        }
        return interviewRepository.saveAndFlush(interview);
    }

    private JobApplication saveApplication(JobPosting jobPosting) {
        Applicant applicant = saveApplicant();
        JobPosition jobPosition = jobPosting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant,
                jobPosting,
                jobPosition,
                "지원자",
                jobPosting.getTitle(),
                jobPosition.getPositionName()
        );
        application.submit(start().minusDays(1));
        return jobApplicationRepository.saveAndFlush(application);
    }

    private Applicant saveApplicant() {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName("지원자");
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName("지원자");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private Employee saveEmployee() {
        Employee employee = new Employee();
        employee.setLoginId("interviewer-" + UUID.randomUUID());
        employee.setName("면접관");
        employee.setDeptName("인사부-" + UUID.randomUUID());
        return employeeRepository.saveAndFlush(employee);
    }

    private JobPosting saveJobPosting() {
        JobPosting jobPosting = JobPosting.create("공고", "내용", start().minusDays(1), start().plusDays(10));
        jobPosting.replaceJobPositions(List.of(JobPosition.create("본사영업", 1, 1)));
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }
}
