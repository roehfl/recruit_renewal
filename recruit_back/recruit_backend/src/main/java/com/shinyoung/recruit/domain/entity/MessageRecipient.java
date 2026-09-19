package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.common.crypto.AesAttributeConverter;
import com.shinyoung.recruit.enumeration.MessageChannel;
import com.shinyoung.recruit.enumeration.MessageDeliveryStatus;
import com.shinyoung.recruit.enumeration.SmsKind;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * 발송 1회의 수신자 1명과 채널별 결과·솔루션 거래 ID. 이름·연락처는 발송 시점 값을 AES 로 암호화해 보관한다.
 * 테스트 발송 수신자(인사팀 담당자)는 jobApplication 이 null 이다.
 *
 * <p>{@code @DynamicUpdate}: 한 채널의 접수 기록(엔티티 변경)과 다른 채널의 결과 반영(JPQL bulk update)이
 * 같은 행에서 겹쳐도 바뀐 컬럼만 update 해 서로 덮어쓰지 않게 한다(설계서 7.4).
 */
@Entity
@Getter
@DynamicUpdate
@Table(
        name = "message_recipient",
        indexes = {
                @Index(name = "idx_message_recipient_send", columnList = "message_send_id"),
                @Index(name = "idx_message_recipient_application", columnList = "job_application_id"),
                @Index(name = "idx_message_recipient_mail_tx", columnList = "mail_transaction_id"),
                @Index(name = "idx_message_recipient_sms_tx", columnList = "sms_transaction_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageRecipient extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_send_id", nullable = false)
    private MessageSend messageSend;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id")
    private JobApplication jobApplication;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String recipientName;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String email;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageDeliveryStatus mailStatus;

    @Column(length = 200)
    private String mailFailureReason;

    /** 메일 솔루션 거래 ID. 발송 결과 매칭 키. */
    @Column(length = 100)
    private String mailTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageDeliveryStatus smsStatus;

    @Column(length = 200)
    private String smsFailureReason;

    /** 문자 솔루션 거래 ID. 발송 결과 매칭 키. */
    @Column(length = 100)
    private String smsTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SmsKind smsKind;

    /** 마지막 상태 변경 시각(접수·결과 수신). */
    private LocalDateTime processedAt;

    public static MessageRecipient create(MessageSend messageSend, JobApplication jobApplication,
                                          String recipientName, String email, String phone,
                                          MessageDeliveryStatus mailStatus, String mailFailureReason,
                                          MessageDeliveryStatus smsStatus, String smsFailureReason,
                                          SmsKind smsKind) {
        MessageRecipient recipient = new MessageRecipient();
        recipient.messageSend = messageSend;
        recipient.jobApplication = jobApplication;
        recipient.recipientName = recipientName;
        recipient.email = email;
        recipient.phone = phone;
        recipient.mailStatus = mailStatus;
        recipient.mailFailureReason = mailFailureReason;
        recipient.smsStatus = smsStatus;
        recipient.smsFailureReason = smsFailureReason;
        recipient.smsKind = smsKind;
        return recipient;
    }

    /** 솔루션이 접수한 채널을 REQUESTED 로 두고 결과 매칭용 거래 ID 를 기록한다. 최종 결과는 발송 결과로 온다. */
    public void recordRequested(MessageChannel channel, String transactionId, LocalDateTime processedAt) {
        if (channel == MessageChannel.MAIL) {
            this.mailStatus = MessageDeliveryStatus.REQUESTED;
            this.mailTransactionId = transactionId;
        } else {
            this.smsStatus = MessageDeliveryStatus.REQUESTED;
            this.smsTransactionId = transactionId;
        }
        this.processedAt = processedAt;
    }

    /** 채널 상태와 사유를 기록한다(접수 실패 등). */
    public void recordResult(MessageChannel channel, MessageDeliveryStatus status, String failureReason,
                             LocalDateTime processedAt) {
        if (channel == MessageChannel.MAIL) {
            this.mailStatus = status;
            this.mailFailureReason = failureReason;
        } else {
            this.smsStatus = status;
            this.smsFailureReason = failureReason;
        }
        this.processedAt = processedAt;
    }
}
