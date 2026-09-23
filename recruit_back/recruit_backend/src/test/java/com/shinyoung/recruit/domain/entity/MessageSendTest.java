package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSendTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 10, 0);

    @Test
    void 관리자_발송은_ADMIN으로_만든다() {
        MessageSend send = MessageSend.create(MessageType.FREE, false, null, null, null, null, null,
                true, false, "제목", "본문", null, "hr.kim", "김인사", 1, NOW);

        assertThat(send.getOrigin()).isEqualTo(MessageOrigin.ADMIN);
    }

    @Test
    void 시스템_발송은_템플릿_원문과_시스템_발송자로_만든다() {
        MessageTemplate template = MessageTemplate.create(MessageType.SIGNUP_VERIFICATION, "회원가입 인증 메일", true,
                "[신영증권 채용] 회원가입 이메일 인증번호", "인증번호: #{인증번호}", null);

        MessageSend send = MessageSend.createSystem(MessageType.SIGNUP_VERIFICATION, null, template, NOW);

        assertThat(send.getOrigin()).isEqualTo(MessageOrigin.SYSTEM);
        assertThat(send.getType()).isEqualTo(MessageType.SIGNUP_VERIFICATION);
        assertThat(send.isTest()).isFalse();
        assertThat(send.getJobPosting()).isNull();
        assertThat(send.getStage()).isNull();
        assertThat(send.getConditionSummary()).isNull();
        assertThat(send.getTemplateName()).isEqualTo("회원가입 인증 메일");
        assertThat(send.isMailEnabled()).isTrue();
        assertThat(send.isSmsEnabled()).isFalse();
        assertThat(send.getMailSubject()).isEqualTo("[신영증권 채용] 회원가입 이메일 인증번호");
        assertThat(send.getMailBody()).isEqualTo("인증번호: #{인증번호}");
        assertThat(send.getSmsBody()).isNull();
        assertThat(send.getSenderLoginId()).isEqualTo("SYSTEM");
        assertThat(send.getSenderName()).isEqualTo("시스템");
        assertThat(send.getRecipientCount()).isEqualTo(1);
        assertThat(send.getRequestedAt()).isEqualTo(NOW);
    }
}
