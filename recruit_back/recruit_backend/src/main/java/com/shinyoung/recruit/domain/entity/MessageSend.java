package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 메시지 발송 요청 1회. 치환 전 원문·조건 요약·발송자를 보관한다(설계서 8절).
 * 상태·채널별 건수는 저장하지 않고 조회할 때 수신자 채널 상태로 계산한다(7.4, MessageHistoryService).
 */
@Entity
@Getter
@Table(
        name = "message_send",
        indexes = {
                @Index(name = "idx_message_send_requested_at", columnList = "requested_at"),
                @Index(name = "idx_message_send_posting_requested_at", columnList = "job_posting_id, requested_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageSend extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 40)
    private MessageType type;

    @Column(name = "test_send", nullable = false)
    private boolean test;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id")
    private Stage stage;

    @Column(length = 200)
    private String conditionSummary;

    private Long templateId;

    @Column(length = 100)
    private String templateName;

    @Column(nullable = false)
    private boolean mailEnabled;

    @Column(nullable = false)
    private boolean smsEnabled;

    @Column(length = 200)
    private String mailSubject;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String mailBody;

    @Column(length = 2000)
    private String smsBody;

    @Column(nullable = false, length = 100)
    private String senderLoginId;

    @Column(length = 100)
    private String senderName;

    @Column(nullable = false)
    private int recipientCount;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    public static MessageSend create(MessageType type, boolean test, JobPosting jobPosting, Stage stage,
                                     String conditionSummary, Long templateId, String templateName,
                                     boolean mailEnabled, boolean smsEnabled,
                                     String mailSubject, String mailBody, String smsBody,
                                     String senderLoginId, String senderName,
                                     int recipientCount, LocalDateTime requestedAt) {
        MessageSend send = new MessageSend();
        send.type = type;
        send.test = test;
        send.jobPosting = jobPosting;
        send.stage = stage;
        send.conditionSummary = conditionSummary;
        send.templateId = templateId;
        send.templateName = templateName;
        send.mailEnabled = mailEnabled;
        send.smsEnabled = smsEnabled;
        send.mailSubject = mailSubject;
        send.mailBody = mailBody;
        send.smsBody = smsBody;
        send.senderLoginId = senderLoginId;
        send.senderName = senderName;
        send.recipientCount = recipientCount;
        send.requestedAt = requestedAt;
        return send;
    }
}
