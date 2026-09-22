package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** recruit.message.gateway=trnode 면 TR 게이트웨이 하나가 메일·SMS 를 맡고 결과 수신 서버가 뜨며, 목업 게이트웨이·목업 결과는 뜨지 않는다. */
@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.message.gateway=trnode",
        "node.url=http://node.example.test",
        "recruit.message.report-port=0"
})
class TRNodeMessageGatewayWiringTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void trnode면_TR_게이트웨이만_뜬다() {
        assertThat(context.getBean(MailGateway.class)).isInstanceOf(TRNodeMessageGateway.class);
        assertThat(context.getBean(SmsGateway.class)).isSameAs(context.getBean(MailGateway.class));
        assertThat(context.getBeansOfType(LoggingMailGateway.class)).isEmpty();
        assertThat(context.getBeansOfType(LoggingSmsGateway.class)).isEmpty();
        assertThat(context.getBeansOfType(MockDeliveryReportScheduler.class)).isEmpty();
        assertThat(context.getBean(UmsReportServer.class).isRunning()).isTrue();
    }
}
