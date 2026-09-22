package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.enumeration.MessageChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/** 레거시 FTPThread.receiveSQL 과 같은 결과 줄 규약·연결 흐름(EOF 뒤 4바이트 응답)을 확인한다. */
class UmsReportServerTest {

    private final DeliveryReportHandler handler = mock(DeliveryReportHandler.class);
    private UmsReportServer server;

    @AfterEach
    void stopServer() {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    /** 앞 8자리 + 결과 종류 5 + UUID 32 + 수신자 100 + 결과코드 2 = 147자. */
    private static String line(String code, String uuid, String member, String res) {
        return "20260922" + pad(code, 5) + pad(uuid, 32) + pad(member, 100) + pad(res, 2);
    }

    private static String pad(String value, int length) {
        return value + " ".repeat(length - value.length());
    }

    private UmsReportServer startServer(int port) {
        MessageProperties properties = new MessageProperties();
        properties.setReportPort(port);
        server = new UmsReportServer(handler, properties);
        server.start();
        return server;
    }

    @Test
    void UMS01은_메일_UMS04와_UMS05는_SMS_결과로_읽는다() {
        assertThat(UmsReportServer.parse(line("UMS01", "UUID-1", "kim@example.com", "00")))
                .contains(new DeliveryReport(MessageChannel.MAIL, "UUID-1", "kim@example.com", "00"));
        assertThat(UmsReportServer.parse(line("UMS04", "UUID-2", "01000000000", "00")))
                .contains(new DeliveryReport(MessageChannel.SMS, "UUID-2", "01000000000", "00"));
        assertThat(UmsReportServer.parse(line("UMS05", "UUID-3", "01000000001", "99")))
                .contains(new DeliveryReport(MessageChannel.SMS, "UUID-3", "01000000001", "99"));
    }

    @Test
    void 메일_수신_확인_UMS02와_모르는_종류_짧은_줄은_무시한다() {
        assertThat(UmsReportServer.parse(line("UMS02", "UUID-1", "kim@example.com", "Y"))).isEmpty();
        assertThat(UmsReportServer.parse(line("UMS03", "UUID-1", "kim@example.com", "00"))).isEmpty();
        assertThat(UmsReportServer.parse(line("UMS01", "UUID-1", "kim@example.com", "00").substring(0, 146))).isEmpty();
        assertThat(UmsReportServer.parse("short")).isEmpty();
    }

    @Test
    void 연결의_결과_줄을_EOF까지_받아_반영을_넘기고_받은_결과_수를_돌려준다() throws Exception {
        startServer(0);

        try (Socket client = new Socket("127.0.0.1", server.localPort())) {
            OutputStream out = client.getOutputStream();
            String lines = line("UMS01", "UUID-1", "kim@example.com", "00") + "\r\n"
                    + line("UMS02", "UUID-1", "kim@example.com", "Y") + "\n"
                    + line("UMS04", "UUID-2", "01000000000", "99") + "\n";
            out.write(lines.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            client.shutdownOutput();

            assertThat(new DataInputStream(client.getInputStream()).readInt()).isEqualTo(2);
        }

        verify(handler).handle(new DeliveryReport(MessageChannel.MAIL, "UUID-1", "kim@example.com", "00"));
        verify(handler).handle(new DeliveryReport(MessageChannel.SMS, "UUID-2", "01000000000", "99"));
        verifyNoMoreInteractions(handler);
    }

    @Test
    void 포트를_열지_못하면_기동을_실패시킨다() throws IOException {
        try (ServerSocket occupied = new ServerSocket(0)) {
            assertThatThrownBy(() -> startServer(occupied.getLocalPort()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("port=" + occupied.getLocalPort());
        }
    }

    @Test
    void 멈추면_포트를_닫는다() {
        int port = startServer(0).localPort();

        server.stop();

        assertThat(server.isRunning()).isFalse();
        assertThatThrownBy(() -> new Socket("127.0.0.1", port).close()).isInstanceOf(IOException.class);
    }
}
