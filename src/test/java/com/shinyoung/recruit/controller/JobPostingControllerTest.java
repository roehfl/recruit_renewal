package com.shinyoung.recruit.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class JobPostingControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void create_and_get_job_posting_success() throws Exception {
        String response = mockMvc.perform(post("/admin/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPostingJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = response.replaceAll(".*\"data\":(\\d+).*", "$1");

        mockMvc.perform(get("/admin/job-postings/{id}", Long.parseLong(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("2026 recruitment"));
    }

    @Test
    void get_job_postings_success() throws Exception {
        mockMvc.perform(get("/admin/job-postings")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    private String createJobPostingJson() {
        return """
                {
                  "title": "2026 recruitment",
                  "contentHtml": "<p>content</p>",
                  "receptionStartDateTime": "2026-06-01T09:00:00",
                  "receptionEndDateTime": "2026-06-30T18:00:00",
                  "jobPositions": [
                    {
                      "positionName": "Backend",
                      "headcount": 2,
                      "sortOrder": 1
                    }
                  ],
                  "applicationFormConfig": {
                    "useEducation": true,
                    "useCareer": true,
                    "useCertificate": true,
                    "useLanguage": true,
                    "useMilitary": true,
                    "useAward": true,
                    "useGapPeriod": true
                  }
                }
                """;
    }
}
