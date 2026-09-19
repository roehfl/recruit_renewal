package com.shinyoung.recruit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 거래 ID 가 아직 기록되지 않아 짝을 못 찾은 발송 결과를 잠시 보관한다(설계서 7.4).
 * 메모리 보관이라 서버 1대를 전제하고, 서버가 재시작되면 보관 중인 결과는 사라진다.
 */
@Component
@RequiredArgsConstructor
public class DeliveryReportBuffer {

    private final Clock clock;
    private final Map<String, Entry> reports = new ConcurrentHashMap<>();

    /** 같은 거래 ID 가 이미 있으면 처음 받은 시각을 유지한다. */
    public void put(DeliveryReport report) {
        reports.putIfAbsent(report.transactionId(), new Entry(report, LocalDateTime.now(clock)));
    }

    public Optional<DeliveryReport> find(String transactionId) {
        Entry entry = reports.get(transactionId);
        return entry == null ? Optional.empty() : Optional.of(entry.report());
    }

    public void remove(String transactionId) {
        reports.remove(transactionId);
    }

    /** 순회 중 추가·삭제와 겹쳐도 되도록 복사본을 준다. */
    public List<Entry> entries() {
        return List.copyOf(reports.values());
    }

    public int size() {
        return reports.size();
    }

    public record Entry(DeliveryReport report, LocalDateTime receivedAt) {
    }
}
