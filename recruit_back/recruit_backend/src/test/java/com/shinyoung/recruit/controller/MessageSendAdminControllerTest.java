package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class MessageSendAdminControllerTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private ApplicantRepository applicantRepository;

    private MockMvc mockMvc;
    private JobPosting posting;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        posting = JobPosting.create("메시지 공고", "Content",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 22, 18, 0));
        posting.replaceJobPositions(List.of(JobPosition.create("Sales", 1)));
        posting = jobPostingRepository.saveAndFlush(posting);
    }

    @Test
    void 직접입력_대상자와_변수_발신정보를_조회한다() throws Exception {
        JobApplication application = submitted("김지원");

        mockMvc.perform(get("/api/admin/messages/targets")
                        .param("type", "FREE")
                        .param("jobPostingId", String.valueOf(posting.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recipients.length()").value(1))
                .andExpect(jsonPath("$.data.recipients[0].applicationId").value(application.getId()))
                .andExpect(jsonPath("$.data.recipients[0].variables['이름']").value("김지원"))
                .andExpect(jsonPath("$.data.recipients[0].smsAvailable").value(true))
                .andExpect(jsonPath("$.data.interviewGroups.length()").value(0))
                .andExpect(jsonPath("$.data.sender.smsCallbackNumber").value("02-0000-0000"));
    }

    @Test
    void 결과발표에_전형이_없으면_400() throws Exception {
        mockMvc.perform(get("/api/admin/messages/targets")
                        .param("type", "RESULT_ANNOUNCEMENT")
                        .param("jobPostingId", String.valueOf(posting.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("전형을 선택해야 합니다."));
    }

    @Test
    void 없는_공고는_404() throws Exception {
        mockMvc.perform(get("/api/admin/messages/targets")
                        .param("type", "FREE")
                        .param("jobPostingId", String.valueOf(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("공고를 찾을 수 없습니다."));
    }

    private JobApplication submitted(String name) {
        String ci = "test-ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName(name);
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setUserName(name);
        applicant.setPhoneNumber("01000000000");
        applicantRepository.saveAndFlush(applicant);
        JobPosition jobPosition = posting.getJobPositions().get(0);
        JobApplication application = JobApplication.create(
                applicant, posting, jobPosition, name, posting.getTitle(), jobPosition.getPositionName());
        application.submit(LocalDateTime.of(2026, 9, 10, 9, 0));
        return jobApplicationRepository.saveAndFlush(application);
    }
}
