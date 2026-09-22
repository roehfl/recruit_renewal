package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.MessageProperties;
import com.shinyoung.recruit.enumeration.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 발송 솔루션(UMS)이 보내는 발송 결과를 TCP 로 받는다(recruit.message.gateway=trnode, 포트 recruit.message.report-port).
 * 레거시 RemoteUploadServer·FTPThread.receiveSQL 과 같은 규약: 연결 1개에 결과 여러 줄이 오고, 솔루션이 보내기를 마치면(EOF)
 * 받은 결과 줄 수를 4바이트 int 로 돌려주고 닫는다. 한 줄 = 수신자 1명(고정 위치, 앞 8자리는 쓰지 않는다):
 * [8,13) 결과 종류(UMS01 메일, UMS04·UMS05 SMS·LMS, 그 밖은 무시) [13,45) UUID = 거래 ID
 * [45,145) 수신 이메일 또는 번호 [145,147) 결과코드.
 * 레거시와 달리 연결은 작은 고정 스레드 풀에서 처리하고 읽기 타임아웃을 두며, 잘못된 줄은 그 줄만 건너뛴다.
 * 로그에는 건수만 남긴다(연락처·원문 금지).
 */
@Component
@ConditionalOnProperty(prefix = "recruit.message", name = "gateway", havingValue = "trnode")
public class UmsReportServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(UmsReportServer.class);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final int WORKERS = 4;
    private static final int CODE_END = 13;
    private static final int LINE_LENGTH = 147;

    private final DeliveryReportHandler deliveryReportHandler;
    private final int port;
    private ServerSocket serverSocket;
    private ExecutorService workers;
    private volatile boolean running;

    public UmsReportServer(DeliveryReportHandler deliveryReportHandler, MessageProperties messageProperties) {
        this.deliveryReportHandler = deliveryReportHandler;
        this.port = messageProperties.getReportPort();
    }

    /** 포트를 열지 못하면 기동을 실패시킨다(결과를 못 받는 채로 조용히 떠 있지 않게). */
    @Override
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            throw new IllegalStateException("발송 결과 수신 포트를 열 수 없습니다: port=" + port, e);
        }
        AtomicInteger workerNumber = new AtomicInteger();
        workers = Executors.newFixedThreadPool(WORKERS,
                task -> new Thread(task, "ums-report-" + workerNumber.incrementAndGet()));
        running = true;
        Thread acceptor = new Thread(this::acceptLoop, "ums-report-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
        log.info("발송 결과 수신 시작: port={}", localPort());
    }

    @Override
    public void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // 닫는 중 오류는 무시한다.
        }
        workers.shutdown();
        try {
            workers.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    int localPort() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (running) {
                    log.warn("발송 결과 연결 수락 실패: error={}", e.getClass().getSimpleName());
                }
                continue;
            }
            try {
                workers.execute(() -> receive(socket));
            } catch (RejectedExecutionException e) {
                closeQuietly(socket);
            }
        }
    }

    /** 연결 1개: EOF 까지 줄을 읽어 결과마다 반영을 넘기고, 받은 결과 줄 수를 돌려준다. */
    private void receive(Socket socket) {
        try (socket) {
            socket.setSoTimeout((int) READ_TIMEOUT.toMillis());
            // 필드는 모두 ASCII 다. 바이트 위치 = 글자 위치가 되도록 ISO-8859-1 로 읽는다.
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            int lines = 0;
            int reports = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                lines++;
                Optional<DeliveryReport> report = parse(line);
                if (report.isPresent()) {
                    deliveryReportHandler.handle(report.get());
                    reports++;
                }
            }
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(reports);
            out.flush();
            log.info("발송 결과 수신: lines={} reports={}", lines, reports);
        } catch (IOException e) {
            log.warn("발송 결과 수신 실패: error={}", e.getClass().getSimpleName());
        }
    }

    /** 결과 한 줄 → 수신자 1명의 발송 결과. 메일·SMS 결과가 아니거나 길이가 모자라면 비운다. */
    static Optional<DeliveryReport> parse(String line) {
        if (line.length() < CODE_END) {
            log.warn("발송 결과 줄이 짧아 무시합니다: length={}", line.length());
            return Optional.empty();
        }
        MessageChannel channel = switch (line.substring(8, CODE_END).trim()) {
            case "UMS01" -> MessageChannel.MAIL;
            case "UMS04", "UMS05" -> MessageChannel.SMS;
            default -> null;
        };
        if (channel == null) {
            return Optional.empty();
        }
        if (line.length() < LINE_LENGTH) {
            log.warn("발송 결과 줄이 짧아 무시합니다: length={}", line.length());
            return Optional.empty();
        }
        return Optional.of(new DeliveryReport(channel,
                line.substring(CODE_END, 45).trim(), line.substring(45, 145).trim(), line.substring(145, LINE_LENGTH).trim()));
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 닫는 중 오류는 무시한다.
        }
    }
}
