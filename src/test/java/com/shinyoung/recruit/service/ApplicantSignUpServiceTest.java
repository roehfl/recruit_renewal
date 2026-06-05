package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.UserRepository;
import com.shinyoung.recruit.dto.request.ApplicantSignUpRequest;
import com.shinyoung.recruit.dto.response.ApplicantEmailAvailabilityResponse;
import com.shinyoung.recruit.dto.response.ApplicantSignUpResponse;
import com.shinyoung.recruit.exception.InvalidApplicantSignUpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicantSignUpServiceTest {

    @Mock
    private ApplicantRepository applicantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private ApplicantSignUpService applicantSignUpService;

    @BeforeEach
    void setUp() {
        applicantSignUpService = new ApplicantSignUpService(applicantRepository, userRepository, passwordEncoder);
    }

    @Test
    void 회원가입_성공() {
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "applicant01", "Password1234!", "홍길동",
                "01012345678", "applicant01@example.com", "test-ci-applicant01"
        );
        given(userRepository.existsByLoginId("applicant01")).willReturn(false);
        given(applicantRepository.existsByEmail("applicant01@example.com")).willReturn(false);
        given(applicantRepository.existsByCiHash(anyString())).willReturn(false);
        given(passwordEncoder.encode("Password1234!")).willReturn("encoded-password");
        given(applicantRepository.save(any(Applicant.class))).willAnswer(invocation -> {
            Applicant a = invocation.getArgument(0);
            // simulate ID assignment
            try {
                var idField = com.shinyoung.recruit.domain.entity.User.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(a, 1L);
            } catch (Exception ignored) {}
            return a;
        });

        ApplicantSignUpResponse response = applicantSignUpService.signUp(request);

        assertThat(response.applicantId()).isEqualTo(1L);
        assertThat(response.loginId()).isEqualTo("applicant01");
        assertThat(response.name()).isEqualTo("홍길동");
    }

    @Test
    void loginId_중복이면_실패() {
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "duplicate", "Password1234!", "홍길동",
                "01012345678", null, "test-ci"
        );
        given(userRepository.existsByLoginId("duplicate")).willReturn(true);

        assertThatThrownBy(() -> applicantSignUpService.signUp(request))
                .isInstanceOf(InvalidApplicantSignUpException.class)
                .hasMessageContaining("아이디");
    }

    @Test
    void 임직원이_점유한_loginId면_실패() {
        // User 레벨 체크 검증 — 임직원(LDAP JIT) loginId도 users 테이블에 있으므로 가입이 차단되어야 한다.
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "emp01", "Password1234!", "홍길동",
                "01012345678", null, "test-ci"
        );
        given(userRepository.existsByLoginId("emp01")).willReturn(true);

        assertThatThrownBy(() -> applicantSignUpService.signUp(request))
                .isInstanceOf(InvalidApplicantSignUpException.class)
                .hasMessageContaining("아이디");
    }

    @Test
    void email_중복이면_실패() {
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "newuser", "Password1234!", "홍길동",
                "01012345678", "dup@example.com", "test-ci"
        );
        given(userRepository.existsByLoginId("newuser")).willReturn(false);
        given(applicantRepository.existsByEmail("dup@example.com")).willReturn(true);

        assertThatThrownBy(() -> applicantSignUpService.signUp(request))
                .isInstanceOf(InvalidApplicantSignUpException.class)
                .hasMessageContaining("이메일");
    }

    @Test
    void ciHash_중복이면_실패() {
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "newuser", "Password1234!", "홍길동",
                "01012345678", null, "dup-ci"
        );
        given(userRepository.existsByLoginId("newuser")).willReturn(false);
        given(applicantRepository.existsByCiHash(HashUtil.sha256("dup-ci"))).willReturn(true);

        assertThatThrownBy(() -> applicantSignUpService.signUp(request))
                .isInstanceOf(InvalidApplicantSignUpException.class)
                .hasMessageContaining("본인인증");
    }

    @Test
    void password가_인코딩되어_저장된다() {
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "enctest", "RawPassword1!", "테스트",
                "01011111111", null, "enc-ci"
        );
        given(userRepository.existsByLoginId("enctest")).willReturn(false);
        given(applicantRepository.existsByCiHash(anyString())).willReturn(false);
        given(passwordEncoder.encode("RawPassword1!")).willReturn("$2a$encoded");
        given(applicantRepository.save(any(Applicant.class))).willAnswer(invocation -> invocation.getArgument(0));

        applicantSignUpService.signUp(request);

        ArgumentCaptor<Applicant> captor = ArgumentCaptor.forClass(Applicant.class);
        verify(applicantRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$encoded");
        assertThat(captor.getValue().getPassword()).isNotEqualTo("RawPassword1!");
    }

    @Test
    void 응답에_민감정보가_없다() {
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "safeuser", "Password1234!", "안전",
                "01099999999", "safe@example.com", "safe-ci"
        );
        given(userRepository.existsByLoginId("safeuser")).willReturn(false);
        given(applicantRepository.existsByEmail("safe@example.com")).willReturn(false);
        given(applicantRepository.existsByCiHash(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded");
        given(applicantRepository.save(any(Applicant.class))).willAnswer(invocation -> invocation.getArgument(0));

        ApplicantSignUpResponse response = applicantSignUpService.signUp(request);

        assertThat(response.loginId()).isNotNull();
        assertThat(response.name()).isNotNull();
        assertThat(response.toString()).doesNotContain("Password1234!");
        assertThat(response.toString()).doesNotContain("safe-ci");
        assertThat(response.toString()).doesNotContain("safe@example.com");
        assertThat(response.toString()).doesNotContain("01099999999");
    }

    @Test
    void 이메일_가용성_미점유면_true() {
        given(applicantRepository.existsByEmail("free@example.com")).willReturn(false);

        ApplicantEmailAvailabilityResponse response =
                applicantSignUpService.checkEmailAvailability("free@example.com");

        assertThat(response.available()).isTrue();
    }

    @Test
    void 이메일_가용성_점유면_false() {
        given(applicantRepository.existsByEmail("taken@example.com")).willReturn(true);

        ApplicantEmailAvailabilityResponse response =
                applicantSignUpService.checkEmailAvailability("taken@example.com");

        assertThat(response.available()).isFalse();
    }

    @Test
    void 이메일_가용성_공백은_trim_정규화_후_판정한다() {
        // signUp의 normalizeEmail과 동일 정규화 — 양끝 공백을 제거한 값으로 판정해야 한다.
        given(applicantRepository.existsByEmail("trim@example.com")).willReturn(false);

        ApplicantEmailAvailabilityResponse response =
                applicantSignUpService.checkEmailAvailability("  trim@example.com  ");

        assertThat(response.available()).isTrue();
        verify(applicantRepository).existsByEmail("trim@example.com");
    }
}
