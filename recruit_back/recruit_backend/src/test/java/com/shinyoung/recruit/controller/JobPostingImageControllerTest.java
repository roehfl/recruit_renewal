package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.service.JobPostingImageService;
import com.shinyoung.recruit.service.JobPostingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.posting-image.storage-root=build/test-posting-images"
})
@AutoConfigureMockMvc
@Transactional
class JobPostingImageControllerTest {

    private static final byte[] PNG_HEAD = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MockMvc mockMvc;

    // Spring Boot 4는 Jackson 3(tools.jackson)을 auto-config하므로 Jackson 2
    // com.fasterxml.jackson.databind.ObjectMapper 빈이 없다(주입 실패). JusoAddressClient 선례를 따라
    // 자체 인스턴스를 만든다. LocalDateTime 직렬화를 위해 JavaTimeModule을 찾아 등록하고 ISO-8601 문자열로 쓴다.
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingImageService jobPostingImageService;

    private RequestPostProcessor admin() {
        return user("admin").authorities(new SimpleGrantedAuthority("ROLE_RECRUIT_ADMIN"));
    }

    private JobPostingCreateRequest createRequest() {
        return new JobPostingCreateRequest(
                "이미지 공고",
                null,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest("백엔드", 0)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );
    }

    private Long createPosting() {
        return jobPostingService.create(createRequest());
    }

    private MockMultipartFile pngPart(String partName, String fileName) {
        return new MockMultipartFile(partName, fileName, "image/png", PNG_HEAD);
    }

    @Test
    void multipart로_공고와_이미지를_함께_생성한다() throws Exception {
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(createRequest()));
        MockMultipartFile metasPart = new MockMultipartFile(
                "imageMetas", "", MediaType.APPLICATION_JSON_VALUE,
                "[{\"altText\":\"채용 포스터\",\"sortOrder\":0}]".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/admin/job-postings")
                        .file(requestPart)
                        .file(metasPart)
                        .file(pngPart("imageFiles", "poster.png"))
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    void 이미지를_개별_추가하고_상세에서_확인한다() throws Exception {
        Long postingId = createPosting();

        mockMvc.perform(multipart("/api/admin/job-postings/" + postingId + "/images")
                        .file(pngPart("file", "poster.png"))
                        .param("altText", "채용 포스터")
                        .with(admin()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/job-postings/" + postingId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images[0].altText").value("채용 포스터"));
    }

    @Test
    void 관리자_이미지_파일을_서빙한다() throws Exception {
        Long postingId = createPosting();
        Long imageId = jobPostingImageService.addImage(postingId, pngPart("file", "poster.png"), "포스터", 0);

        mockMvc.perform(get("/api/admin/job-postings/" + postingId + "/images/" + imageId + "/file").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=3600"));
    }

    @Test
    void draft_공고의_공개_이미지_서빙은_404() throws Exception {
        Long postingId = createPosting();
        Long imageId = jobPostingImageService.addImage(postingId, pngPart("file", "poster.png"), "포스터", 0);

        mockMvc.perform(get("/api/job-postings/" + postingId + "/images/" + imageId + "/file"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 발행_공고의_공개_이미지는_서빙된다() throws Exception {
        Long postingId = createPosting();
        Long imageId = jobPostingImageService.addImage(postingId, pngPart("file", "poster.png"), "포스터", 0);
        jobPostingService.publish(postingId);

        mockMvc.perform(get("/api/job-postings/" + postingId + "/images/" + imageId + "/file"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=3600"));
    }

    @Test
    void 이미지_삭제와_순서변경() throws Exception {
        Long postingId = createPosting();
        Long first = jobPostingImageService.addImage(postingId, pngPart("file", "a.png"), "포스터 1", 0);
        Long second = jobPostingImageService.addImage(postingId, pngPart("file", "b.png"), "포스터 2", 1);

        mockMvc.perform(post("/api/admin/job-postings/" + postingId + "/images/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[" + second + "," + first + "]}")
                        .with(admin()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/job-postings/" + postingId + "/images/" + first + "/delete")
                        .with(admin()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/job-postings/" + postingId).with(admin()))
                .andExpect(jsonPath("$.data.images.length()").value(1))
                .andExpect(jsonPath("$.data.images[0].altText").value("포스터 2"));
    }

    @Test
    void 권한없는_사용자는_이미지_API에_접근할_수_없다() throws Exception {
        Long postingId = createPosting();

        mockMvc.perform(multipart("/api/admin/job-postings/" + postingId + "/images")
                        .file(pngPart("file", "poster.png"))
                        .param("altText", "포스터")
                        .with(user("applicant").authorities(new SimpleGrantedAuthority("ROLE_APPLICANT"))))
                .andExpect(status().isForbidden());
    }
}
