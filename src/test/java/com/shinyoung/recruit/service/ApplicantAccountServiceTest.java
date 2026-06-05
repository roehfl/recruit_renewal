package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.dto.request.ApplicantPasswordChangeRequest;
import com.shinyoung.recruit.dto.request.ApplicantPhoneNumberChangeRequest;
import com.shinyoung.recruit.exception.InvalidApplicantAccountException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ApplicantAccountServiceTest {

    @Mock
    private ApplicantRepository applicantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private ApplicantAccountService applicantAccountService;

    private Applicant applicant;

    @BeforeEach
    void setUp() {
        applicantAccountService = new ApplicantAccountService(applicantRepository, passwordEncoder);

        applicant = new Applicant("account-ci", HashUtil.sha256("account-ci"));
        applicant.setLoginId("account01");
        applicant.setName("홍길동");
        applicant.setUserName("홍길동");
        applicant.setPassword("encoded-current");
        applicant.setPhoneNumber("01012345678");
    }

    @Test
    void 비밀번호_변경_성공_시_인코딩되어_저장된다() {
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(passwordEncoder.matches("CurrentPw1234!", "encoded-current")).willReturn(true);
        given(passwordEncoder.matches("NewPassword1!", "encoded-current")).willReturn(false);
        given(passwordEncoder.encode("NewPassword1!")).willReturn("encoded-new");

        applicantAccountService.changePassword(1L,
                new ApplicantPasswordChangeRequest("CurrentPw1234!", "NewPassword1!"));

        assertThat(applicant.getPassword()).isEqualTo("encoded-new");
    }

    @Test
    void 비밀번호_변경_현재_비밀번호_불일치면_실패() {
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(passwordEncoder.matches("WrongPw1234!", "encoded-current")).willReturn(false);

        assertThatThrownBy(() -> applicantAccountService.changePassword(1L,
                new ApplicantPasswordChangeRequest("WrongPw1234!", "NewPassword1!")))
                .isInstanceOf(InvalidApplicantAccountException.class)
                .hasMessageContaining("현재 비밀번호");

        assertThat(applicant.getPassword()).isEqualTo("encoded-current");
    }

    @Test
    void 비밀번호_변경_새_비밀번호가_현재와_동일하면_실패() {
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(passwordEncoder.matches("SamePassword1!", "encoded-current")).willReturn(true);

        assertThatThrownBy(() -> applicantAccountService.changePassword(1L,
                new ApplicantPasswordChangeRequest("SamePassword1!", "SamePassword1!")))
                .isInstanceOf(InvalidApplicantAccountException.class)
                .hasMessageContaining("달라야");

        assertThat(applicant.getPassword()).isEqualTo("encoded-current");
    }

    @Test
    void 비밀번호_변경_지원자_없으면_실패() {
        given(applicantRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> applicantAccountService.changePassword(99L,
                new ApplicantPasswordChangeRequest("CurrentPw1234!", "NewPassword1!")))
                .isInstanceOf(InvalidApplicantAccountException.class)
                .hasMessageContaining("지원자");
    }

    @Test
    void 전화번호_변경_성공() {
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(passwordEncoder.matches("CurrentPw1234!", "encoded-current")).willReturn(true);

        applicantAccountService.changePhoneNumber(1L,
                new ApplicantPhoneNumberChangeRequest("CurrentPw1234!", "01099998888"));

        assertThat(applicant.getPhoneNumber()).isEqualTo("01099998888");
    }

    @Test
    void 전화번호_변경_시_trim된다() {
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(passwordEncoder.matches("CurrentPw1234!", "encoded-current")).willReturn(true);

        applicantAccountService.changePhoneNumber(1L,
                new ApplicantPhoneNumberChangeRequest("CurrentPw1234!", "  01099998888  "));

        assertThat(applicant.getPhoneNumber()).isEqualTo("01099998888");
    }

    @Test
    void 전화번호_변경_현재_비밀번호_불일치면_실패() {
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(passwordEncoder.matches("WrongPw1234!", "encoded-current")).willReturn(false);

        assertThatThrownBy(() -> applicantAccountService.changePhoneNumber(1L,
                new ApplicantPhoneNumberChangeRequest("WrongPw1234!", "01099998888")))
                .isInstanceOf(InvalidApplicantAccountException.class)
                .hasMessageContaining("현재 비밀번호");

        assertThat(applicant.getPhoneNumber()).isEqualTo("01012345678");
    }
}
