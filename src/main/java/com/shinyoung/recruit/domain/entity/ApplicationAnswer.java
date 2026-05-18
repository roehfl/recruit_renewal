package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "application_answer",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_application_answer_application_question",
                        columnNames = {"job_application_id", "job_posting_question_id"}
                )
        },
        indexes = {
                @Index(name = "idx_application_answer_application", columnList = "job_application_id"),
                @Index(name = "idx_application_answer_question", columnList = "job_posting_question_id"),
                @Index(name = "idx_application_answer_application_sort", columnList = "job_application_id, sort_order_snapshot")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationAnswer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_question_id", nullable = false)
    private JobPostingQuestion jobPostingQuestion;

    @Column(length = 5000)
    private String answerText;

    @Column(nullable = false, length = 2000)
    private String questionTextSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionCategory categorySnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionAnswerType answerTypeSnapshot;

    @Column(nullable = false)
    private Boolean requiredSnapshot;

    private Integer minLengthSnapshot;

    @Column(nullable = false)
    private Integer maxLengthSnapshot;

    @Column(nullable = false)
    private Integer sortOrderSnapshot;

    private ApplicationAnswer(
            JobApplication jobApplication,
            JobPostingQuestion jobPostingQuestion,
            String answerText
    ) {
        this.jobApplication = jobApplication;
        this.jobPostingQuestion = jobPostingQuestion;
        this.answerText = answerText;
        syncSnapshot(jobPostingQuestion);
    }

    public static ApplicationAnswer create(
            JobApplication jobApplication,
            JobPostingQuestion jobPostingQuestion,
            String answerText
    ) {
        return new ApplicationAnswer(jobApplication, jobPostingQuestion, answerText);
    }

    public void updateAnswer(JobPostingQuestion jobPostingQuestion, String answerText) {
        this.jobPostingQuestion = jobPostingQuestion;
        this.answerText = answerText;
        syncSnapshot(jobPostingQuestion);
    }

    private void syncSnapshot(JobPostingQuestion question) {
        this.questionTextSnapshot = question.getQuestionText();
        this.categorySnapshot = question.getCategory();
        this.answerTypeSnapshot = question.getAnswerType();
        this.requiredSnapshot = question.getRequired();
        this.minLengthSnapshot = question.getMinLength();
        this.maxLengthSnapshot = question.getMaxLength();
        this.sortOrderSnapshot = question.getSortOrder();
    }
}
