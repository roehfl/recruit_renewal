package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.service.GatewayResult;
import com.shinyoung.recruit.service.MailGateway;
import com.shinyoung.recruit.service.MailMessage;
import com.shinyoung.recruit.service.SmsGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class ApplicantPasswordResetControllerTest {

    private static final Pattern CODE = Pattern.compile("인증번호: (\\d{6})");
    private static final String EMAIL = "reset01@example.com";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ApplicantRepository applicantRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MailGateway mailGateway;
    @MockitoBean
    private SmsGateway smsGateway;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        SecurityContextHolder.clearContext();
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("TX-RESET-1"));
    }

    @Test
    void 인증번호를_확인하면_새_비밀번호로_바꾸고_인증은_1회용이다() throws Exception {
        Applicant applicant = saveApplicant();
        MockHttpSession session = new MockHttpSession();

        send(session, EMAIL).andExpect(status().isOk());
        ArgumentCaptor<MailMessage> mail = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(mail.capture(), eq(List.of(EMAIL)), eq(List.of("김재설정")));
        assertThat(mail.getValue().text()).startsWith("김재설정님,");
        Matcher matcher = CODE.matcher(mail.getValue().text());
        assertThat(matcher.find()).isTrue();

        confirm(session, EMAIL, matcher.group(1)).andExpect(status().isOk());
        reset(session, EMAIL, "NewPassword1!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Applicant changed = applicantRepository.findById(applicant.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword1!", changed.getPassword())).isTrue();
        reset(session, EMAIL, "AnotherPassword1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 인증이 필요합니다."));
    }

    @Test
    void 재설정_요청_이메일의_대소문자가_달라도_인증한_계정의_비밀번호를_바꾼다() throws Exception {
        Applicant applicant = saveApplicant();
        MockHttpSession session = new MockHttpSession();

        send(session, EMAIL).andExpect(status().isOk());
        ArgumentCaptor<MailMessage> mail = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(mail.capture(), anyList(), anyList());
        Matcher matcher = CODE.matcher(mail.getValue().text());
        assertThat(matcher.find()).isTrue();

        String upper = EMAIL.toUpperCase();
        confirm(session, upper, matcher.group(1)).andExpect(status().isOk());
        reset(session, upper, "NewPassword1!").andExpect(status().isOk());

        Applicant changed = applicantRepository.findById(applicant.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword1!", changed.getPassword())).isTrue();
    }

    @Test
    void 가입되지_않은_이메일이면_404() throws Exception {
        send(new MockHttpSession(), "none@example.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("가입된 이메일이 아닙니다."));
        verifyNoInteractions(mailGateway);
    }

    @Test
    void 인증_없이_바꾸면_400() throws Exception {
        saveApplicant();

        reset(new MockHttpSession(), EMAIL, "NewPassword1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 인증이 필요합니다."));
    }

    @Test
    void 새_비밀번호가_8자_미만이면_400() throws Exception {
        reset(new MockHttpSession(), EMAIL, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Applicant saveApplicant() {
        Applicant applicant = new Applicant(HashUtil.sha256("reset-test-ci"));
        applicant.setLoginId(EMAIL);
        applicant.setName("김재설정");
        applicant.setUserName("김재설정");
        applicant.setPassword(passwordEncoder.encode("OldPassword1!"));
        applicant.setPhoneNumber("01000000000");
        applicant.setEmail(EMAIL);
        return applicantRepository.save(applicant);
    }

    private ResultActions send(MockHttpSession session, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/applicants/password-reset/send")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions confirm(MockHttpSession session, String email, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/applicants/password-reset/verify")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
    }

    private ResultActions reset(MockHttpSession session, String email, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/auth/applicants/password-reset")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }
}
