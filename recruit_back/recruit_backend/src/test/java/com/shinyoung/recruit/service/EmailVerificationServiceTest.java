package com.shinyoung.recruit.service;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.enumeration.EmailVerificationPurpose;
import com.shinyoung.recruit.enumeration.MessageType;
import com.shinyoung.recruit.enumeration.SystemMailOutcome;
import com.shinyoung.recruit.exception.InvalidEmailVerificationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static com.shinyoung.recruit.enumeration.EmailVerificationPurpose.PASSWORD_RESET;
import static com.shinyoung.recruit.enumeration.EmailVerificationPurpose.SIGNUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Instant BASE = Instant.parse("2026-09-23T01:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final String EMAIL = "applicant@example.com";
    private static final String REISSUE = "인증번호를 다시 받아 주세요.";
    private static final String REQUIRED = "이메일 인증이 필요합니다.";

    @Mock
    private SystemMailService systemMailService;

    @Captor
    private ArgumentCaptor<Map<String, String>> variables;

    private EmailVerificationService at(Duration elapsed) {
        return new EmailVerificationService(systemMailService, Clock.fixed(BASE.plus(elapsed), ZONE));
    }

    private static LocalDateTime time(Duration elapsed) {
        return LocalDateTime.ofInstant(BASE.plus(elapsed), ZONE);
    }

    @Test
    void 발급하면_6자리_번호와_번호_해시만_담은_상태를_준다() {
        EmailVerificationService.IssuedCode issued = at(Duration.ZERO).issue(null, SIGNUP, " " + EMAIL + " ");

        assertThat(issued.code()).matches("\\d{6}");
        EmailVerificationState state = issued.state();
        assertThat(state.getPurpose()).isEqualTo(SIGNUP);
        assertThat(state.getEmail()).isEqualTo(EMAIL);
        assertThat(state.getCodeHash()).isEqualTo(HashUtil.sha256(issued.code())).isNotEqualTo(issued.code());
        assertThat(state.getExpiresAt()).isEqualTo(time(Duration.ofMinutes(5)));
        assertThat(state.getSentAt()).isEqualTo(time(Duration.ZERO));
        assertThat(state.getFailedCount()).isZero();
        assertThat(state.getVerifiedAt()).isNull();
    }

    @Test
    void 같은_목적은_60초가_지나야_다시_받는다() {
        EmailVerificationState first = at(Duration.ZERO).issue(null, SIGNUP, EMAIL).state();

        assertThatThrownBy(() -> at(Duration.ofSeconds(59)).issue(first, SIGNUP, EMAIL))
                .isInstanceOf(InvalidEmailVerificationException.class)
                .hasMessage("인증번호는 60초 후에 다시 받을 수 있습니다.");
        assertThat(at(Duration.ofSeconds(60)).issue(first, SIGNUP, EMAIL).state().getSentAt())
                .isEqualTo(time(Duration.ofSeconds(60)));
    }

    @Test
    void 만료됐거나_이메일이_다르거나_상태가_없으면_다시_받으라고_한다() {
        EmailVerificationService.IssuedCode issued = at(Duration.ZERO).issue(null, SIGNUP, EMAIL);

        assertThatThrownBy(() -> at(Duration.ofMinutes(5)).verify(issued.state(), SIGNUP, EMAIL, issued.code()))
                .isInstanceOf(InvalidEmailVerificationException.class)
                .hasMessage(REISSUE);
        assertThatThrownBy(() -> at(Duration.ofMinutes(1)).verify(issued.state(), SIGNUP, "other@example.com", issued.code()))
                .hasMessage(REISSUE);
        assertThatThrownBy(() -> at(Duration.ofMinutes(1)).verify(null, SIGNUP, EMAIL, issued.code()))
                .hasMessage(REISSUE);
        assertThatThrownBy(() -> at(Duration.ofMinutes(1)).verify(issued.state(), PASSWORD_RESET, EMAIL, issued.code()))
                .hasMessage(REISSUE);
    }

    @Test
    void 틀리면_실패_수를_늘리고_5회_틀리면_맞아도_거부한다() {
        EmailVerificationService.IssuedCode issued = at(Duration.ZERO).issue(null, SIGNUP, EMAIL);
        EmailVerificationService service = at(Duration.ofMinutes(1));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.verify(issued.state(), SIGNUP, EMAIL, "wrong"))
                    .isInstanceOf(InvalidEmailVerificationException.class)
                    .hasMessage("인증번호가 일치하지 않습니다.");
        }

        assertThat(issued.state().getFailedCount()).isEqualTo(5);
        assertThatThrownBy(() -> service.verify(issued.state(), SIGNUP, EMAIL, issued.code()))
                .hasMessage("인증번호를 5회 틀렸습니다. 인증번호를 다시 받아 주세요.");
        assertThat(issued.state().getVerifiedAt()).isNull();
    }

    @Test
    void 맞으면_확인하고_10분_동안_대소문자와_공백을_무시한_같은_이메일만_통과한다() {
        EmailVerificationService.IssuedCode issued = at(Duration.ZERO).issue(null, SIGNUP, EMAIL);
        EmailVerificationState state = issued.state();

        at(Duration.ofMinutes(1)).verify(state, SIGNUP, EMAIL, issued.code());

        assertThat(state.getVerifiedAt()).isEqualTo(time(Duration.ofMinutes(1)));
        assertThatCode(() -> at(Duration.ofMinutes(10).plusSeconds(59))
                .requireVerified(state, SIGNUP, " Applicant@Example.com "))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> at(Duration.ofMinutes(11)).requireVerified(state, SIGNUP, EMAIL))
                .isInstanceOf(InvalidEmailVerificationException.class)
                .hasMessage(REQUIRED);
        assertThatThrownBy(() -> at(Duration.ofMinutes(2)).requireVerified(state, SIGNUP, "other@example.com"))
                .hasMessage(REQUIRED);
        assertThatThrownBy(() -> at(Duration.ofMinutes(2)).requireVerified(state, PASSWORD_RESET, EMAIL))
                .hasMessage(REQUIRED);
        assertThatThrownBy(() -> at(Duration.ofMinutes(2)).requireVerified(state, SIGNUP, null))
                .hasMessage(REQUIRED);
    }

    @Test
    void 확인_전이거나_상태가_없으면_이메일_인증이_필요하다() {
        EmailVerificationState state = at(Duration.ZERO).issue(null, SIGNUP, EMAIL).state();

        assertThatThrownBy(() -> at(Duration.ofMinutes(1)).requireVerified(state, SIGNUP, EMAIL))
                .hasMessage(REQUIRED);
        assertThatThrownBy(() -> at(Duration.ofMinutes(1)).requireVerified(null, SIGNUP, EMAIL))
                .hasMessage(REQUIRED);
    }

    @Test
    void 발송이_접수되면_상태를_주고_메일에는_평문_번호를_넘긴다() {
        given(systemMailService.send(any(), any(), any(), any(), any(), any())).willReturn(SystemMailOutcome.ACCEPTED);

        EmailVerificationState state = at(Duration.ZERO).send(null, PASSWORD_RESET, EMAIL, "김재설정");

        verify(systemMailService).send(eq(MessageType.PASSWORD_RESET), eq(EMAIL), eq("김재설정"),
                variables.capture(), isNull(), isNull());
        assertThat(HashUtil.sha256(variables.getValue().get("인증번호"))).isEqualTo(state.getCodeHash());
        assertThat(state.getPurpose()).isEqualTo(PASSWORD_RESET);
    }

    @Test
    void 발송이_실패하거나_템플릿이_없으면_거부한다() {
        given(systemMailService.send(any(), any(), any(), any(), any(), any()))
                .willReturn(SystemMailOutcome.FAILED, SystemMailOutcome.NO_TEMPLATE);

        assertThatThrownBy(() -> at(Duration.ZERO).send(null, SIGNUP, EMAIL, ""))
                .isInstanceOf(InvalidEmailVerificationException.class)
                .hasMessage("인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.");
        assertThatThrownBy(() -> at(Duration.ZERO).send(null, SIGNUP, EMAIL, ""))
                .isInstanceOf(InvalidEmailVerificationException.class)
                .hasMessage("인증 메일 템플릿이 없습니다. 관리자에게 문의하세요.");
    }

    @Test
    void 용도마다_세션_키와_메일_종류가_다르다() {
        assertThat(EmailVerificationService.sessionKey(SIGNUP)).isEqualTo("EMAIL_VERIFICATION_SIGNUP");
        assertThat(EmailVerificationService.sessionKey(PASSWORD_RESET)).isEqualTo("EMAIL_VERIFICATION_PASSWORD_RESET");
        assertThat(EmailVerificationPurpose.SIGNUP.getMessageType()).isEqualTo(MessageType.SIGNUP_VERIFICATION);
        assertThat(EmailVerificationPurpose.PASSWORD_RESET.getMessageType()).isEqualTo(MessageType.PASSWORD_RESET);
    }
}
