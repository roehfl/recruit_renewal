package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.repository.ClientEventLogRepository;
import com.shinyoung.recruit.dto.response.ClientEventLogResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.ClientEventSeverity;
import com.shinyoung.recruit.enumeration.ClientEventSource;
import com.shinyoung.recruit.enumeration.ClientEventType;
import com.shinyoung.recruit.exception.ClientEventLogNotFoundException;
import com.shinyoung.recruit.exception.InvalidClientEventQueryException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * client event read API 서비스(Phase 09f-3). {@code AuditActivityReadService}(09b) 가드 패턴 —
 * page size 상한 / 범위 상한(90일) / default 범위. 진단 로그라 default는 감사(30일)보다 짧은
 * 최근 {@value #DEFAULT_RANGE_DAYS}일이다(설계 8.2).
 */
@Service
@Transactional(readOnly = true)
public class ClientEventLogReadService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_RANGE_DAYS = 90;
    static final int DEFAULT_RANGE_DAYS = 7;

    private final ClientEventLogRepository repository;
    private final Clock clock;

    public ClientEventLogReadService(ClientEventLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PageResponse<ClientEventLogResponse> search(
            ClientEventType eventType,
            ClientEventSeverity severity,
            ClientEventSource source,
            Long applicationId,
            Long jobPostingId,
            String clientSessionId,
            String relatedCorrelationId,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size,
            boolean includeSensitive
    ) {
        validatePaging(page, size);
        LocalDateTime effectiveTo = to != null ? to : LocalDateTime.now(clock);
        LocalDateTime effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_RANGE_DAYS);
        validateRange(effectiveFrom, effectiveTo);

        return PageResponse.from(repository.search(
                        effectiveFrom,
                        effectiveTo,
                        eventType,
                        severity,
                        source,
                        applicationId,
                        jobPostingId,
                        normalize(clientSessionId),
                        normalize(relatedCorrelationId),
                        PageRequest.of(page, size))
                .map(log -> ClientEventLogResponse.from(log, includeSensitive)));
    }

    public ClientEventLogResponse getEvent(Long id, boolean includeSensitive) {
        return repository.findById(id)
                .map(log -> ClientEventLogResponse.from(log, includeSensitive))
                .orElseThrow(() -> new ClientEventLogNotFoundException("Client event log was not found."));
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new InvalidClientEventQueryException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidClientEventQueryException("size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from.isAfter(to)) {
            throw new InvalidClientEventQueryException("receivedAt 검색 범위가 올바르지 않습니다(from > to).");
        }
        // toDays()는 소수 일수를 버려 경계가 새므로 Duration 자체를 비교한다(09b 선례).
        if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
            throw new InvalidClientEventQueryException("receivedAt 검색 범위는 최대 " + MAX_RANGE_DAYS + "일입니다.");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
