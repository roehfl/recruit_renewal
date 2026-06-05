package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.repository.ActivityLogRepository;
import com.shinyoung.recruit.dto.response.AuditActivityResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.exception.ActivityLogNotFoundException;
import com.shinyoung.recruit.exception.InvalidAuditQueryException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 감사 로그 read API 서비스(Phase 09b). 읽기 전용 — append-only 증적은 수정/삭제하지 않는다.
 *
 * <p><b>read API 가드(설계 9b, 리뷰 #6)</b>:
 * <ul>
 *   <li>page size 상한({@value #MAX_PAGE_SIZE}) — 대량 dump 차단</li>
 *   <li>occurredAt 범위 상한({@value #MAX_RANGE_DAYS}일) — full scan 차단</li>
 *   <li>범위 미지정 시 default 최근 {@value #DEFAULT_RANGE_DAYS}일</li>
 *   <li>권한별 projection 분리 — {@code includeSensitive} 가 true(ROLE_PRIVACY_ADMIN)일 때만 ip/ua 원문</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AuditActivityReadService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_RANGE_DAYS = 90;
    static final int DEFAULT_RANGE_DAYS = 30;

    private final ActivityLogRepository activityLogRepository;
    private final Clock clock;

    public AuditActivityReadService(ActivityLogRepository activityLogRepository, Clock clock) {
        this.activityLogRepository = activityLogRepository;
        this.clock = clock;
    }

    public PageResponse<AuditActivityResponse> search(
            String actorId,
            AuditActionType actionType,
            AuditActionResult actionResult,
            AuditTargetType targetType,
            Long jobPostingId,
            Long applicationId,
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

        return PageResponse.from(activityLogRepository.search(
                        effectiveFrom,
                        effectiveTo,
                        normalize(actorId),
                        actionType,
                        actionResult,
                        targetType,
                        jobPostingId,
                        applicationId,
                        PageRequest.of(page, size))
                .map(log -> AuditActivityResponse.from(log, includeSensitive)));
    }

    public AuditActivityResponse getActivity(Long id, boolean includeSensitive) {
        return activityLogRepository.findById(id)
                .map(log -> AuditActivityResponse.from(log, includeSensitive))
                .orElseThrow(() -> new ActivityLogNotFoundException("Audit activity was not found."));
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new InvalidAuditQueryException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidAuditQueryException("size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from.isAfter(to)) {
            throw new InvalidAuditQueryException("occurredAt 검색 범위가 올바르지 않습니다(from > to).");
        }
        // toDays()는 소수 일수를 버려 90일 23:59도 통과시키므로 Duration 자체를 비교한다(9b 리뷰 Medium 1).
        if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_RANGE_DAYS)) > 0) {
            throw new InvalidAuditQueryException("occurredAt 검색 범위는 최대 " + MAX_RANGE_DAYS + "일입니다.");
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
