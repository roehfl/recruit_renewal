package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 메시지 종류 1개에 속한 메일(제목·본문)과 SMS 본문 묶음. 종류당 기본 템플릿은 서비스가 1개로 유지한다. */
@Entity
@Getter
@Table(
        name = "message_template",
        indexes = {
                @Index(name = "idx_message_template_type", columnList = "message_type")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 40)
    private MessageType type;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean defaultTemplate;

    @Column(length = 200)
    private String mailSubject;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String mailBody;

    @Column(length = 2000)
    private String smsBody;

    private MessageTemplate(MessageType type, String name, boolean defaultTemplate,
                            String mailSubject, String mailBody, String smsBody) {
        this.type = type;
        this.name = name;
        this.defaultTemplate = defaultTemplate;
        this.mailSubject = mailSubject;
        this.mailBody = mailBody;
        this.smsBody = smsBody;
    }

    public static MessageTemplate create(MessageType type, String name, boolean defaultTemplate,
                                         String mailSubject, String mailBody, String smsBody) {
        return new MessageTemplate(type, name, defaultTemplate, mailSubject, mailBody, smsBody);
    }

    public void update(MessageType type, String name, boolean defaultTemplate,
                       String mailSubject, String mailBody, String smsBody) {
        this.type = type;
        this.name = name;
        this.defaultTemplate = defaultTemplate;
        this.mailSubject = mailSubject;
        this.mailBody = mailBody;
        this.smsBody = smsBody;
    }

    public void unmarkDefault() {
        this.defaultTemplate = false;
    }
}
