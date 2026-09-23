package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.AuditHmac;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.UserRepository;
import com.shinyoung.recruit.dto.request.ApplicantSignUpRequest;
import com.shinyoung.recruit.dto.response.ApplicantEmailAvailabilityResponse;
import com.shinyoung.recruit.dto.response.ApplicantSignUpResponse;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import com.shinyoung.recruit.exception.InvalidApplicantSignUpException;
import com.shinyoung.recruit.service.nice.NiceVerifiedIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ApplicantSignUpServiceTest {

    @Mock
    private ApplicantRepository applicantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationService emailVerificationService;

    private final AuditHmac auditHmac = new AuditHmac("test-secret-value");

    private ApplicantSignUpService applicantSignUpService;

    @BeforeEach
    void setUp() {
        applicantSignUpService = new ApplicantSignUpService(
                applicantRepository, userRepository, passwordEncoder, auditHmac, emailVerificationService);
    }

    private NiceVerifiedIdentity identity(String name, String phoneNumber, String birthDate, String gender) {
        return new NiceVerifiedIdentity(
                NiceVerificationPurpose.SIGNUP, name, phoneNumber, birthDate, gender, Instant.now());
    }

    @Test
    void 회원가입_성공() {
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "applicant01@example.com", "Password1234!", "applicant01@example.com"
        );
        NiceVerifiedIdentity identity = identity("홍길동", "01012345678", "19900101", "1");
        given(userRepository.existsByLoginId("applicant01@example.com")).willReturn(false);
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

        ApplicantSignUpResponse response = applicantSignUpService.signUp(request, identity);

        assertThat(response.applicantId()).isEqualTo(1L);
        assertThat(response.loginId()).isEqualTo("applicant01@example.com");
        assertThat(response.name()).isEqualTo("홍길동");
    }

    @Test
    void 아이디가_인증한_이메일과_다르면_실패() {
        // 이메일 인증은 email 만 확인한다. loginId 를 다르게 보내면 남의 이메일을 아이디로 쓸 수 있다.
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "victim@example.com", "Password1234!", "attacker@example.com"
        );
        NiceVerifiedIdentity identity = identity("홍길동", "01012345678", "19900101", "1");

        assertThatThrownBy(() -> applicantSignUpService.signUp(request, identity))
                .isInstanceOf(InvalidApplicantSignUpException.class)
                .hasMessage("아이디는 인증한 이메일과 같아야 합니다.");
        verifyNoInteractions(userRepository, applicantRepository);
    }

    @Test
    void 아이디와_이메일은_앞뒤_공백과_대소문자를_무시하고_비교한다() {
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                " Mixed@Example.com ", "Password1234!", "mixed@example.com"
        );
        NiceVerifiedIdentity identity = identity("홍길동", "01012345678", "19900101", "1");
        given(applicantRepository.save(any(Applicant.class))).willAnswer(invocation -> invocation.getArgument(0));

        ApplicantSignUpResponse response = applicantSignUpService.signUp(request, identity);

        assertThat(response.loginId()).isEqualTo("Mixed@Example.com");
    }

    @Test
    void loginId_중복이면_실패() {
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "duplicate@example.com", "Password1234!", "duplicate@example.com"
        );
        NiceVerifiedIdentity identity = identity("홍길동", "01012345678", "19900101", "1");
        given(userRepository.existsByLoginId("duplicate@example.com")).willReturn(true);

        assertThatThrownBy(() -> applicantSignUpService.signUp(request, identity))
                .isInstanceOf(InvalidApplicantSignUpException.class)
                .hasMessageContaining("아이디");
    }

    @Test
    void 임직원이_점유한_loginId면_실패() {
        // User 레벨 체크 검증 — 임직원(LDAP JIT) loginId도 users 테이블에 있으므로 가입이 차단되어야 한다.
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "emp01@example.com", "Password1234!", "emp01@example.com"
        );
        NiceVerifiedIdentity identity = identity("홍길동", "01012345678", "19900101", "1");
        given(userRepository.existsByLoginId("emp01@example.com")).willReturn(true);

        assertThatThrownBy(() -> applicantSignUpService.signUp(request, identity))
                .isInstanceOf(InvalidApplicantSignUpException.class)
                .hasMessageContaining("아이디");
    }

    @Test
    void email_중복이면_실패() {
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "dup@example.com", "Password1234!", "dup@example.com"
        );
        NiceVerifiedIdentity identity = identity("홍길동", "01012345678", "19900101", "1");
        given(userRepository.existsByLoginId("dup@example.com")).willReturn(false);
        given(applicantRepository.existsByEmail("dup@example.com")).willReturn(true);

        assertThatThrownBy(() -> applicantSignUpService.signUp(request, identity))
                .isInstanceOf(InvalidApplicantSignUpException.class)
                .hasMessageContaining("이메일");
    }

    @Test
    void ciHash_중복이면_실패() {
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "newuser@example.com", "Password1234!", "newuser@example.com"
        );
        NiceVerifiedIdentity identity = identity("홍길동", "01012345678", "19900101", "1");
        given(userRepository.existsByLoginId("newuser@example.com")).willReturn(false);
        given(applicantRepository.existsByCiHash(auditHmac.identityHash("홍길동", "19900101", "1"))).willReturn(true);

        assertThatThrownBy(() -> applicantSignUpService.signUp(request, identity))
                .isInstanceOf(InvalidApplicantSignUpException.class)
                .hasMessageContaining("본인인증");
    }

    @Test
    void password가_인코딩되어_저장된다() {
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "enctest@example.com", "RawPassword1!", "enctest@example.com"
        );
        NiceVerifiedIdentity identity = identity("테스트", "01011111111", "19910202", "0");
        given(userRepository.existsByLoginId("enctest@example.com")).willReturn(false);
        given(applicantRepository.existsByCiHash(anyString())).willReturn(false);
        given(passwordEncoder.encode("RawPassword1!")).willReturn("$2a$encoded");
        given(applicantRepository.save(any(Applicant.class))).willAnswer(invocation -> invocation.getArgument(0));

        applicantSignUpService.signUp(request, identity);

        ArgumentCaptor<Applicant> captor = ArgumentCaptor.forClass(Applicant.class);
        verify(applicantRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$encoded");
        assertThat(captor.getValue().getPassword()).isNotEqualTo("RawPassword1!");
    }

    @Test
    void 응답에_민감정보가_없다() {
        ApplicantSignUpRequest request = new ApplicantSignUpRequest(
                "safe@example.com", "Password1234!", "safe@example.com"
        );
        NiceVerifiedIdentity identity = identity("안전", "01099999999", "19951231", "1");
        given(userRepository.existsByLoginId("safe@example.com")).willReturn(false);
        given(applicantRepository.existsByEmail("safe@example.com")).willReturn(false);
        given(applicantRepository.existsByCiHash(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded");
        given(applicantRepository.save(any(Applicant.class))).willAnswer(invocation -> invocation.getArgument(0));

        ApplicantSignUpResponse response = applicantSignUpService.signUp(request, identity);

        assertThat(response.loginId()).isNotNull();
        assertThat(response.name()).isNotNull();
        assertThat(response.toString()).doesNotContain("Password1234!");
        assertThat(response.toString()).doesNotContain("19951231");
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

    @Test
    void 가입된_이메일이면_인증번호를_보내지_않는다() {
        given(applicantRepository.existsByEmail("taken@example.com")).willReturn(true);

        assertThatThrownBy(() -> applicantSignUpService.sendEmailVerification(null, " taken@example.com "))
                .isInstanceOf(InvalidApplicantSignUpException.class)
                .hasMessage("이미 사용 중인 이메일입니다.");
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void 가입_인증번호는_이름_없이_보낸다() {
        EmailVerificationState state = EmailVerificationState.issued(EmailVerificationPurpose.SIGNUP,
                "new@example.com", "hash", LocalDateTime.of(2026, 9, 23, 10, 5), LocalDateTime.of(2026, 9, 23, 10, 0));
        given(applicantRepository.existsByEmail("new@example.com")).willReturn(false);
        given(emailVerificationService.send(null, EmailVerificationPurpose.SIGNUP, "new@example.com", "")).willReturn(state);

        assertThat(applicantSignUpService.sendEmailVerification(null, "new@example.com")).isSameAs(state);
    }
}
