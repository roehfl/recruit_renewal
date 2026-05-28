package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.common.crypto.CryptoHolder;
import com.shinyoung.recruit.config.CryptoConfig;
import com.shinyoung.recruit.config.JpaConfig;
import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;
import com.shinyoung.recruit.enumeration.InterviewMethod;
import com.shinyoung.recruit.enumeration.InterviewStatus;
import com.shinyoung.recruit.enumeration.StageType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CryptoConfig.class, JpaConfig.class, CryptoHolder.class})
class InterviewRepositoryTest {

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private StageRepository stageRepository;

    @Test
    void Interview를_저장하고_조회한다() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = interview(jobPosting, stage, start());

        Interview saved = interviewRepository.saveAndFlush(interview);

        assertThat(interviewRepository.findById(saved.getId())).contains(saved);
    }

    @Test
    void findByJobPostingIdOrderByStartDateTimeAsc는_시간순으로_조회한다() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview later = interviewRepository.save(interview(jobPosting, stage, start().plusHours(2)));
        Interview earlier = interviewRepository.save(interview(jobPosting, stage, start()));
        interviewRepository.flush();

        List<Interview> interviews = interviewRepository.findByJobPostingIdOrderByStartDateTimeAsc(jobPosting.getId());

        assertThat(interviews).extracting(Interview::getId)
                .containsExactly(earlier.getId(), later.getId());
    }

    @Test
    void jobPosting과_stage로_면접을_조회한다() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview interview = interviewRepository.saveAndFlush(interview(jobPosting, stage, start()));

        List<Interview> interviews = interviewRepository.findByJobPostingIdAndStageIdOrderByStartDateTimeAsc(
                jobPosting.getId(),
                stage.getId()
        );

        assertThat(interviews).extracting(Interview::getId).containsExactly(interview.getId());
    }

    @Test
    void jobPosting과_status로_면접을_조회한다() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview draft = interviewRepository.save(interview(jobPosting, stage, start()));
        Interview confirmed = interview(jobPosting, stage, start().plusHours(2));
        confirmed.confirm();
        interviewRepository.saveAndFlush(confirmed);

        List<Interview> drafts = interviewRepository.findByJobPostingIdAndStatusOrderByStartDateTimeAsc(
                jobPosting.getId(),
                InterviewStatus.DRAFT
        );

        assertThat(drafts).extracting(Interview::getId).containsExactly(draft.getId());
    }

    @Test
    void searchAdminInterviews_filters_by_status_and_time_range() {
        JobPosting jobPosting = saveJobPosting();
        Stage stage = saveStage(jobPosting);
        Interview draft = interviewRepository.save(interview(jobPosting, stage, start()));
        Interview confirmed = interview(jobPosting, stage, start().plusHours(2));
        confirmed.confirm();
        interviewRepository.saveAndFlush(confirmed);

        List<Interview> interviews = interviewRepository.searchAdminInterviews(
                jobPosting.getId(),
                stage.getId(),
                InterviewStatus.DRAFT,
                start().minusMinutes(30),
                start().plusMinutes(30)
        );

        assertThat(interviews).extracting(Interview::getId).containsExactly(draft.getId());
    }

    private JobPosting saveJobPosting() {
        JobPosting jobPosting = JobPosting.create("공고", "내용", start().minusDays(1), start().plusDays(10));
        jobPosting.replaceJobPositions(List.of(JobPosition.create("본사영업", 1)));
        return jobPostingRepository.saveAndFlush(jobPosting);
    }

    private Stage saveStage(JobPosting jobPosting) {
        return stageRepository.saveAndFlush(
                Stage.create(jobPosting, "1차 면접", StageType.FIRST_INTERVIEW, 1, null, false)
        );
    }

    private Interview interview(JobPosting jobPosting, Stage stage, LocalDateTime startDateTime) {
        return Interview.createDraft(
                jobPosting,
                stage,
                "1조",
                startDateTime,
                startDateTime.plusHours(1),
                InterviewMethod.IN_PERSON,
                "본사",
                null,
                null,
                null
        );
    }

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0);
    }
}
