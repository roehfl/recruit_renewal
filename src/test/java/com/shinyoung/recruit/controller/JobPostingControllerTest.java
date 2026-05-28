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
                .andExpect(jsonPath("$.data.title").value("2026 recruitment"))
                .andExpect(jsonPath("$.data.postingType").value("EXPERIENCED_RECRUITMENT"))
                .andExpect(jsonPath("$.data.summary").value("Experienced recruitment"))
                .andExpect(jsonPath("$.data.visible").value(false))
                .andExpect(jsonPath("$.data.pinned").value(true))
                .andExpect(jsonPath("$.data.displayOrder").value(5))
                .andExpect(jsonPath("$.data.receptionStatus").exists())
                .andExpect(jsonPath("$.data.accepting").exists())
                .andExpect(jsonPath("$.data.positionCount").value(2))
                .andExpect(jsonPath("$.data.applicationFormConfig.useEducation").value(true))
                .andExpect(jsonPath("$.data.applicationFormConfig.requireEducation").value(true))
                .andExpect(jsonPath("$.data.applicationFormConfig.requireCertificate").value(false))
                .andExpect(jsonPath("$.data.jobPositions[1].applicationType").value("EXPERIENCED"))
                .andExpect(jsonPath("$.data.jobPositions[1].jobGroup").value("IT"))
                .andExpect(jsonPath("$.data.jobPositions[1].jobTitle").value("Backend Engineer"))
                .andExpect(jsonPath("$.data.jobPositions[1].workLocation").value("Seoul"))
                .andExpect(jsonPath("$.data.jobPositions[1].employmentType").value("FULL_TIME"));
    }

    @Test
    void get_job_postings_success() throws Exception {
        mockMvc.perform(post("/admin/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPostingJson()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/job-postings")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].postingType").value("EXPERIENCED_RECRUITMENT"))
                .andExpect(jsonPath("$.data.content[0].summary").value("Experienced recruitment"))
                .andExpect(jsonPath("$.data.content[0].visible").value(false))
                .andExpect(jsonPath("$.data.content[0].pinned").value(true))
                .andExpect(jsonPath("$.data.content[0].displayOrder").value(5))
                .andExpect(jsonPath("$.data.content[0].positionCount").value(2));
    }

    @Test
    void update_job_posting_with_extended_fields_success() throws Exception {
        String response = mockMvc.perform(post("/admin/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPostingJson()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = response.replaceAll(".*\"data\":(\\d+).*", "$1");

        mockMvc.perform(post("/admin/job-postings/{id}", Long.parseLong(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJobPostingJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/admin/job-postings/{id}", Long.parseLong(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postingType").value("INTERN_RECRUITMENT"))
                .andExpect(jsonPath("$.data.summary").value("Intern recruitment"))
                .andExpect(jsonPath("$.data.visible").value(true))
                .andExpect(jsonPath("$.data.pinned").value(false))
                .andExpect(jsonPath("$.data.displayOrder").value(2))
                .andExpect(jsonPath("$.data.applicationFormConfig.useCareer").value(false))
                .andExpect(jsonPath("$.data.applicationFormConfig.requireCareer").value(false))
                .andExpect(jsonPath("$.data.jobPositions[0].employmentType").value("INTERN"));
    }

    @Test
    void invalid_extended_fields_return_bad_request() throws Exception {
        mockMvc.perform(post("/admin/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPostingJson().replace("Experienced recruitment", "<b>bad</b>")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/admin/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPostingJson().replace(
                                "\"displayStartDateTime\": \"2026-05-25T09:00:00\"",
                                "\"displayStartDateTime\": \"2026-07-02T09:00:00\""
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/admin/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPostingJson().replace("\"sortOrder\": 1", "\"sortOrder\": 0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void attachment_requirement_get_and_replace_success() throws Exception {
        String response = mockMvc.perform(post("/admin/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPostingJson()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = Long.parseLong(response.replaceAll(".*\"data\":(\\d+).*", "$1"));

        mockMvc.perform(post("/admin/job-postings/{id}/attachment-requirements", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requirements": [
                                    {
                                      "attachmentType": "RESUME",
                                      "sectionType": "APPLICATION",
                                      "required": true,
                                      "minCount": 1,
                                      "sortOrder": 0,
                                      "displayName": "Resume",
                                      "description": "Upload resume."
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].attachmentType").value("RESUME"))
                .andExpect(jsonPath("$.data[0].sectionType").value("APPLICATION"))
                .andExpect(jsonPath("$.data[0].required").value(true))
                .andExpect(jsonPath("$.data[0].minCount").value(1))
                .andExpect(jsonPath("$.data[0].storagePath").doesNotExist())
                .andExpect(jsonPath("$.data[0].physicalFileStatus").doesNotExist());

        mockMvc.perform(get("/admin/job-postings/{id}/attachment-requirements", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].displayName").value("Resume"));
    }

    @Test
    void invalid_attachment_requirement_returns_bad_request() throws Exception {
        String response = mockMvc.perform(post("/admin/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPostingJson()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = Long.parseLong(response.replaceAll(".*\"data\":(\\d+).*", "$1"));

        mockMvc.perform(post("/admin/job-postings/{id}/attachment-requirements", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requirements": [
                                    {
                                      "attachmentType": "RESUME",
                                      "sectionType": "APPLICATION",
                                      "required": true,
                                      "minCount": 0,
                                      "sortOrder": 0,
                                      "displayName": "Resume"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void published_posting_attachment_requirement_replace_returns_bad_request() throws Exception {
        String response = mockMvc.perform(post("/admin/job-postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJobPostingJson()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = Long.parseLong(response.replaceAll(".*\"data\":(\\d+).*", "$1"));
        mockMvc.perform(post("/admin/job-postings/{id}/publish", id))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/job-postings/{id}/attachment-requirements", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requirements": [
                                    {
                                      "attachmentType": "RESUME",
                                      "sectionType": "APPLICATION",
                                      "required": true,
                                      "minCount": 1,
                                      "sortOrder": 0,
                                      "displayName": "Resume"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private String createJobPostingJson() {
        return """
                {
                  "title": "2026 recruitment",
                  "postingType": "EXPERIENCED_RECRUITMENT",
                  "summary": "Experienced recruitment",
                  "contentHtml": "<p>content</p>",
                  "receptionStartDateTime": "2026-06-01T09:00:00",
                  "receptionEndDateTime": "2026-06-30T18:00:00",
                  "displayStartDateTime": "2026-05-25T09:00:00",
                  "displayEndDateTime": "2026-07-01T18:00:00",
                  "visible": false,
                  "pinned": true,
                  "displayOrder": 5,
                  "jobPositions": [
                    {
                      "positionName": "Backend",
                      "applicationType": "EXPERIENCED",
                      "jobGroup": "IT",
                      "jobTitle": "Backend Engineer",
                      "workLocation": "Seoul",
                      "employmentType": "FULL_TIME",
                      "sortOrder": 1
                    },
                    {
                      "positionName": "Frontend",
                      "sortOrder": 0
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

    private String updateJobPostingJson() {
        return """
                {
                  "title": "2026 intern recruitment",
                  "postingType": "INTERN_RECRUITMENT",
                  "summary": "Intern recruitment",
                  "contentHtml": "<p>updated</p>",
                  "receptionStartDateTime": "2026-06-01T09:00:00",
                  "receptionEndDateTime": "2026-06-30T18:00:00",
                  "displayStartDateTime": "2026-05-25T09:00:00",
                  "displayEndDateTime": "2026-07-01T18:00:00",
                  "visible": true,
                  "pinned": false,
                  "displayOrder": 2,
                  "jobPositions": [
                    {
                      "positionName": "Intern",
                      "applicationType": "NEW_GRADUATE",
                      "jobGroup": "IT",
                      "jobTitle": "Backend Intern",
                      "workLocation": "Seoul",
                      "employmentType": "INTERN",
                      "sortOrder": 0
                    }
                  ],
                  "applicationFormConfig": {
                    "useEducation": true,
                    "useCareer": false,
                    "useCertificate": false,
                    "useLanguage": false,
                    "useMilitary": false,
                    "useAward": false,
                    "useGapPeriod": false
                  }
                }
                """;
    }
}
