package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ActivityLog;
import com.shinyoung.recruit.domain.repository.ActivityLogRepository;
import com.shinyoung.recruit.dto.response.AuditActivityResponse;
import com.shinyoung.recruit.enumeration.ActorType;
import com.shinyoung.recruit.enumeration.AuditActionResult;
import com.shinyoung.recruit.enumeration.AuditActionType;
import com.shinyoung.recruit.enumeration.AuditTargetType;
import com.shinyoung.recruit.exception.ActivityLogNotFoundException;
import com.shinyoung.recruit.exception.InvalidAuditQueryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuditActivityReadServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-05T12:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDateTime NOW = LocalDateTime.now(FIXED_CLOCK);

    @Mock
    private ActivityLogRepository activityLogRepository;

    private AuditActivityReadService auditActivityReadService;

    @BeforeEach
    void setUp() {
        auditActivityReadService = new AuditActivityReadService(activityLogRepository, FIXED_CLOCK);
    }

    private ActivityLog sampleLog() {
        return ActivityLog.builder()
                .occurredAt(NOW.minusDays(1))
                .actorType(ActorType.EMPLOYEE)
                .actorId("admin01")
                .actionType(AuditActionType.EXPORT_APPLICATIONS)
                .actionResult(AuditActionResult.SUCCESS)
                .targetType(AuditTargetType.EXPORT_DATASET)
                .ipAddress("10.0.0.1")
                .userAgent("test-agent")
                .build();
    }

    @Test
    void 범위_미지정이면_default_최근_30일로_검색한다() {
        given(activityLogRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(sampleLog())));

        auditActivityReadService.search(null, null, null, null, null, null, null, null, 0, 20, false);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(activityLogRepository).search(from.capture(), to.capture(),
                any(), any(), any(), any(), any(), any(), any(Pageable.class));
        assertThat(to.getValue()).isEqualTo(NOW);
        assertThat(from.getValue()).isEqualTo(NOW.minusDays(30));
    }

    @Test
    void page_size가_상한을_넘으면_400() {
        assertThatThrownBy(() -> auditActivityReadService.search(
                null, null, null, null, null, null, null, null, 0, 101, false))
                .isInstanceOf(InvalidAuditQueryException.class);
        verifyNoInteractions(activityLogRepository);
    }

    @Test
    void 검색_범위가_90일을_넘으면_400() {
        assertThatThrownBy(() -> auditActivityReadService.search(
                null, null, null, null, null, null, NOW.minusDays(91), NOW, 0, 20, false))
                .isInstanceOf(InvalidAuditQueryException.class);
        verifyNoInteractions(activityLogRepository);
    }

    @Test
    void 검색_범위_90일_1분도_400() {
        // toDays() 절삭 우회 방지(리뷰 Medium 1) — 90일 + 1분은 거부돼야 한다.
        assertThatThrownBy(() -> auditActivityReadService.search(
                null, null, null, null, null, null, NOW.minusDays(90).minusMinutes(1), NOW, 0, 20, false))
                .isInstanceOf(InvalidAuditQueryException.class);
        verifyNoInteractions(activityLogRepository);
    }

    @Test
    void 검색_범위_정확히_90일은_허용된다() {
        given(activityLogRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        auditActivityReadService.search(
                null, null, null, null, null, null, NOW.minusDays(90), NOW, 0, 20, false);

        verify(activityLogRepository).search(eq(NOW.minusDays(90)), eq(NOW),
                any(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void from이_to보다_뒤면_400() {
        assertThatThrownBy(() -> auditActivityReadService.search(
                null, null, null, null, null, null, NOW, NOW.minusDays(1), 0, 20, false))
                .isInstanceOf(InvalidAuditQueryException.class);
        verifyNoInteractions(activityLogRepository);
    }

    @Test
    void 민감필드는_includeSensitive_false면_마스킹된다() {
        given(activityLogRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(sampleLog())));

        AuditActivityResponse masked = auditActivityReadService
                .search(null, null, null, null, null, null, null, null, 0, 20, false)
                .content().get(0);

        assertThat(masked.ipAddress()).isEqualTo("***");
        assertThat(masked.userAgent()).isEqualTo("***");
    }

    @Test
    void 민감필드는_includeSensitive_true면_원문이다() {
        given(activityLogRepository.findById(1L)).willReturn(Optional.of(sampleLog()));

        AuditActivityResponse raw = auditActivityReadService.getActivity(1L, true);

        assertThat(raw.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(raw.userAgent()).isEqualTo("test-agent");
    }

    @Test
    void 단건_부재면_404_예외() {
        given(activityLogRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> auditActivityReadService.getActivity(99L, false))
                .isInstanceOf(ActivityLogNotFoundException.class);
    }

    @Test
    void actorId는_trim_정규화되고_blank면_null로_검색한다() {
        given(activityLogRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        auditActivityReadService.search("  admin01  ", null, null, null, null, null, null, null, 0, 20, false);
        verify(activityLogRepository).search(any(), any(), eq("admin01"), any(), any(), any(), any(), any(), any(Pageable.class));

        auditActivityReadService.search("   ", null, null, null, null, null, null, null, 0, 20, false);
        verify(activityLogRepository).search(any(), any(), eq(null), any(), any(), any(), any(), any(), any(Pageable.class));
    }
}
