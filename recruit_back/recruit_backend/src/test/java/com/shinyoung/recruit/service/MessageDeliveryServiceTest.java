package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.SmsKind;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageDeliveryServiceTest {

    private final MailGateway mailGateway = mock(MailGateway.class);
    private final SmsGateway smsGateway = mock(SmsGateway.class);
    private final MessageMailLayout mailLayout = mock(MessageMailLayout.class);
    private final MessageDeliveryService service =
            new MessageDeliveryService(mailGateway, smsGateway, mailLayout, new MessageProperties());

    @Test
    void 메일은_레이아웃_HTML과_발신정보로_한번에_보내고_접수_결과를_돌려준다() {
        when(mailLayout.render("제목", "본문")).thenReturn("<html>본문</html>");
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("TX-1"));
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.MAIL, "제목", "본문", null,
                List.of(1L, 2L), List.of("김민준", "이서연"), List.of("a@example.com", "b@example.com"));

        GatewayResult result = service.deliver(unit);

        assertThat(result.accepted()).isTrue();
        assertThat(result.transactionId()).isEqualTo("TX-1");
        ArgumentCaptor<MailMessage> captor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailGateway).send(captor.capture(), org.mockito.ArgumentMatchers.eq(List.of("a@example.com", "b@example.com")),
                org.mockito.ArgumentMatchers.eq(List.of("김민준", "이서연")));
        assertThat(captor.getValue().fromName()).isEqualTo("신영증권 채용담당");
        assertThat(captor.getValue().fromAddress()).isEqualTo("recruit@example.co.kr");
        assertThat(captor.getValue().html()).isEqualTo("<html>본문</html>");
        assertThat(captor.getValue().text()).isEqualTo("본문");
    }

    @Test
    void SMS는_발신번호와_구분을_넣어_보낸다() {
        when(smsGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("TX-2"));
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.SMS, null, "문자", SmsKind.LMS,
                List.of(1L), List.of("김민준"), List.of("01012345678"));

        service.deliver(unit);

        ArgumentCaptor<SmsMessage> captor = ArgumentCaptor.forClass(SmsMessage.class);
        verify(smsGateway).send(captor.capture(), org.mockito.ArgumentMatchers.eq(List.of("01012345678")),
                org.mockito.ArgumentMatchers.eq(List.of("김민준")));
        assertThat(captor.getValue().callbackNumber()).isEqualTo("02-0000-0000");
        assertThat(captor.getValue().kind()).isEqualTo(SmsKind.LMS);
    }

    @Test
    void 게이트웨이_예외는_그_단위만_GATEWAY_ERROR_실패로_바꾼다() {
        when(smsGateway.send(any(), anyList(), anyList())).thenThrow(new IllegalStateException("smtp down 010-1234-5678"));
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.SMS, null, "문자", SmsKind.SMS,
                List.of(1L), List.of("김민준"), List.of("01012345678"));

        GatewayResult result = service.deliver(unit);

        assertThat(result.accepted()).isFalse();
        assertThat(result.failureReason()).isEqualTo(MessageContacts.GATEWAY_ERROR);
    }

    @Test
    void 게이트웨이가_null을_돌려주면_실패로_본다() {
        when(mailLayout.render(any(), any())).thenReturn("<html/>");
        when(mailGateway.send(any(), anyList(), anyList())).thenReturn(null);
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.MAIL, "제목", "본문", null,
                List.of(1L), List.of("김민준"), List.of("a@example.com"));

        assertThat(service.deliver(unit).failureReason()).isEqualTo(MessageContacts.GATEWAY_ERROR);
    }

    @Test
    void 사유_없는_실패는_GATEWAY_ERROR로_채운다() {
        when(smsGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.failure(" "));
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.SMS, null, "문자", SmsKind.SMS,
                List.of(1L), List.of("김민준"), List.of("01012345678"));

        GatewayResult result = service.deliver(unit);

        assertThat(result.accepted()).isFalse();
        assertThat(result.failureReason()).isEqualTo(MessageContacts.GATEWAY_ERROR);
    }

    @Test
    void 접수했는데_거래_ID가_없으면_결과를_매칭할_수_없어_GATEWAY_ERROR_실패로_바꾼다() {
        when(smsGateway.send(any(), anyList(), anyList())).thenReturn(new GatewayResult(true, " ", null));
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.SMS, null, "문자", SmsKind.SMS,
                List.of(1L), List.of("김민준"), List.of("01012345678"));

        GatewayResult result = service.deliver(unit);

        assertThat(result.accepted()).isFalse();
        assertThat(result.transactionId()).isNull();
        assertThat(result.failureReason()).isEqualTo(MessageContacts.GATEWAY_ERROR);
    }

    @Test
    void 거래_ID가_100자를_넘으면_저장할_수_없어_GATEWAY_ERROR_실패로_바꾼다() {
        when(smsGateway.send(any(), anyList(), anyList())).thenReturn(GatewayResult.accepted("x".repeat(101)));
        DeliveryUnit unit = new DeliveryUnit(MessageChannel.SMS, null, "문자", SmsKind.SMS,
                List.of(1L), List.of("김민준"), List.of("01012345678"));

        GatewayResult result = service.deliver(unit);

        assertThat(result.accepted()).isFalse();
        assertThat(result.failureReason()).isEqualTo(MessageContacts.GATEWAY_ERROR);
    }
}
