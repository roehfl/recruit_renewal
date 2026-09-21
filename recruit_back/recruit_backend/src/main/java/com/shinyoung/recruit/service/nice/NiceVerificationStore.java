package com.shinyoung.recruit.service.nice;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 진행 중인 본인확인 요청 보관소.
 *
 * <p><b>인메모리다. DDL 을 추가하지 않는다.</b> 수명이 10분인 임시 상태라 영속화 가치가 없다.
 * 이 시스템은 이미 HTTP 세션이 인메모리라 같은 단일 인스턴스 전제 위에 있다.
 * 다중 인스턴스로 확장하면 세션과 함께 외부 저장소로 옮겨야 한다.
 *
 * <p>재기동하면 진행 중이던 인증이 끊긴다. 사용자는 재인증하면 되고, 빈도가 낮아 허용한다.
 */
@Component
public class NiceVerificationStore {

    private final Map<String, NiceVerificationRecord> records = new ConcurrentHashMap<>();

    /**
     * 있으면 덮어쓴다. 서비스는 쓰지 않는다 — 발급은 {@link #saveIfAbsent}, 상태 전이는
     * {@link #compareAndSet} 을 쓴다. 테스트 픽스처가 상태를 직접 심을 때 쓴다.
     */
    public void save(NiceVerificationRecord record) {
        records.put(record.reqSeq(), record);
    }

    /**
     * 키가 없을 때만 저장한다. 이미 있으면 덮어쓰지 않고 false.
     *
     * <p>모듈의 REQ_SEQ 는 밀리초 + random%100 이라 동시 발급 시 충돌한다(실측 2,000회 중 1,765건).
     * put 으로 덮어쓰면 앞사람의 PENDING 레코드가 조용히 사라진다.
     */
    public boolean saveIfAbsent(NiceVerificationRecord record) {
        return records.putIfAbsent(record.reqSeq(), record) == null;
    }

    /**
     * 현재 값이 expected 일 때만 next 로 바꾼다. 원자적이다.
     *
     * <p>find → 검사 → save 는 check-then-act 경쟁이라 같은 REQ_SEQ 콜백이 동시에 두 번 오면
     * 둘 다 통과한다. 이 CAS 로 한쪽만 이기게 한다. 비교는 record 의 값 기반 equals 로 한다.
     */
    public boolean compareAndSet(NiceVerificationRecord expected, NiceVerificationRecord next) {
        return records.replace(expected.reqSeq(), expected, next);
    }

    public Optional<NiceVerificationRecord> find(String reqSeq) {
        if (reqSeq == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(records.get(reqSeq));
    }

    public Optional<NiceVerificationRecord> findByResultToken(String resultToken) {
        if (resultToken == null) {
            return Optional.empty();
        }
        return records.values().stream()
                .filter(record -> resultToken.equals(record.resultToken()))
                .findFirst();
    }

    /**
     * resultToken 에 해당하는 레코드를 찾아 원자적으로 꺼낸다. 동시에 두 번 불리면 한쪽만 받는다.
     *
     * <p>findByResultToken → remove 를 따로 부르면 둘 다 레코드를 찾고 둘 다 통과한다.
     * remove(key, value) 는 값이 그대로일 때만 지우고 성공 여부를 돌려주므로, 먼저 지운 쪽만 이긴다.
     */
    public Optional<NiceVerificationRecord> takeByResultToken(String resultToken) {
        return findByResultToken(resultToken)
                .filter(record -> records.remove(record.reqSeq(), record));
    }

    public void remove(String reqSeq) {
        records.remove(reqSeq);
    }

    /**
     * 발급 시각이 ttl 보다 오래된 레코드를 지운다.
     *
     * <p>만료 판정 자체는 읽는 시점에도 하므로 이 정리가 늦어져도 보안에 영향이 없다.
     * 메모리 누적을 막는 것이 목적이다.
     */
    public void purgeExpired(Instant now, Duration ttl) {
        Instant threshold = now.minus(ttl);
        records.values().removeIf(record -> record.issuedAt().isBefore(threshold));
    }
}
