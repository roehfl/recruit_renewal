package com.shinyoung.recruit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지원자가 쓴 추가사항 답변. 평문 저장, 지원서 파기 때 {@code answerText}를 null 로 지운다.
 */
@Entity
@Getter
@Table(
        name = "interview_supplement_answer",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_interview_supplement_answer_question_application",
                        columnNames = {"interview_supplement_question_id", "job_application_id"}
                )
        },
        indexes = {
                @Index(name = "idx_interview_supplement_answer_application", columnList = "job_application_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewSupplementAnswer extends BaseEntity {

    public static final int MAX_ANSWER_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_supplement_question_id", nullable = false)
    private InterviewSupplementQuestion question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Column(length = MAX_ANSWER_LENGTH)
    private String answerText;

    private InterviewSupplementAnswer(
            InterviewSupplementQuestion question,
            JobApplication jobApplication,
            String answerText
    ) {
        this.question = question;
        this.jobApplication = jobApplication;
        this.answerText = answerText;
    }

    public static InterviewSupplementAnswer create(
            InterviewSupplementQuestion question,
            JobApplication jobApplication,
            String answerText
    ) {
        return new InterviewSupplementAnswer(question, jobApplication, answerText);
    }

    public void updateAnswerText(String answerText) {
        this.answerText = answerText;
    }
}
