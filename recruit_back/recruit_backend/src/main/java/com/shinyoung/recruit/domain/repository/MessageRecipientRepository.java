package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.MessageRecipient;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MessageRecipientRepository extends JpaRepository<MessageRecipient, Long> {

    List<MessageRecipient> findByMessageSendIdOrderByIdAsc(Long messageSendId);

    /** 메일 거래 1건의 수신자(최대 10명). 발송 결과를 받을 수신자를 연락처로 고르는 데 쓴다. */
    List<MessageRecipient> findByMailTransactionId(String mailTransactionId);

    /** SMS 거래 1건의 수신자(최대 10명). */
    List<MessageRecipient> findBySmsTransactionId(String smsTransactionId);

    /**
     * 수신자 1명의 메일 발송 결과 반영. 아직 REQUESTED 인 행만 바꾸므로 다시 온 결과는 0행이다(멱등).
     * bulk update 라 엔티티 감사(updatedAt)는 거치지 않고 processedAt 만 갱신한다. 같은 트랜잭션의 이후 조회가
     * 새 값을 보도록 영속성 컨텍스트를 비운다(테스트 발송은 요청 트랜잭션에 합류한다).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MessageRecipient r
            set r.mailStatus = :status, r.mailFailureReason = :failureReason, r.processedAt = :processedAt
            where r.id = :recipientId
              and r.mailStatus = com.shinyoung.recruit.enumeration.MessageDeliveryStatus.REQUESTED
            """)
    int applyMailReport(
            @Param("recipientId") Long recipientId,
            @Param("status") MessageDeliveryStatus status,
            @Param("failureReason") String failureReason,
            @Param("processedAt") LocalDateTime processedAt
    );

    /** 수신자 1명의 SMS 발송 결과 반영. 규칙은 applyMailReport 와 같다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MessageRecipient r
            set r.smsStatus = :status, r.smsFailureReason = :failureReason, r.processedAt = :processedAt
            where r.id = :recipientId
              and r.smsStatus = com.shinyoung.recruit.enumeration.MessageDeliveryStatus.REQUESTED
            """)
    int applySmsReport(
            @Param("recipientId") Long recipientId,
            @Param("status") MessageDeliveryStatus status,
            @Param("failureReason") String failureReason,
            @Param("processedAt") LocalDateTime processedAt
    );

    /** 이력 목록 건수: 발송별 (메일 상태, SMS 상태) 조합의 수신자 수. 페이지의 발송 id 로 한 번에 센다. */
    @Query("""
            select new com.shinyoung.recruit.domain.repository.MessageRecipientStatusCount(
                r.messageSend.id, r.mailStatus, r.smsStatus, count(r)
            )
            from MessageRecipient r
            where r.messageSend.id in :messageSendIds
            group by r.messageSend.id, r.mailStatus, r.smsStatus
            """)
    List<MessageRecipientStatusCount> countStatusesByMessageSendIds(
            @Param("messageSendIds") Collection<Long> messageSendIds
    );
}
