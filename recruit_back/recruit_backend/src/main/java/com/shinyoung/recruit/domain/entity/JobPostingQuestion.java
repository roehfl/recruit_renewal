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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "job_posting_question",
        indexes = {
                @Index(name = "idx_job_posting_question_posting", columnList = "job_posting_id"),
                @Index(name = "idx_job_posting_question_posting_active", columnList = "job_posting_id, active"),
                @Index(name = "idx_job_posting_question_posting_sort", columnList = "job_posting_id, sort_order"),
                @Index(name = "idx_job_posting_question_template", columnList = "question_template_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingQuestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_template_id")
    private QuestionTemplate questionTemplate;

    @Column(nullable = false, length = 2000)
    private String questionText;

    @Column(length = 2000)
    private String helperText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionAnswerType answerType;

    @Column(nullable = false)
    private Boolean required;

    private Integer minLength;

    @Column(nullable = false)
    private Integer maxLength;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Boolean active;

    private JobPostingQuestion(
            JobPosting jobPosting,
            QuestionTemplate questionTemplate,
            String questionText,
            String helperText,
            QuestionCategory category,
            QuestionAnswerType answerType,
            Boolean required,
            Integer minLength,
            Integer maxLength,
            Integer sortOrder
    ) {
        this.jobPosting = jobPosting;
        this.questionTemplate = questionTemplate;
        this.questionText = questionText;
        this.helperText = helperText;
        this.category = category;
        this.answerType = answerType;
        this.required = required;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.sortOrder = sortOrder;
        this.active = true;
    }

    public static JobPostingQuestion createFromTemplate(
            JobPosting jobPosting,
            QuestionTemplate questionTemplate,
            String questionText,
            String helperText,
            QuestionCategory category,
            QuestionAnswerType answerType,
            Boolean required,
            Integer minLength,
            Integer maxLength,
            Integer sortOrder
    ) {
        return new JobPostingQuestion(
                jobPosting,
                questionTemplate,
                questionText,
                helperText,
                category,
                answerType,
                required,
                minLength,
                maxLength,
                sortOrder
        );
    }

    public static JobPostingQuestion createDirect(
            JobPosting jobPosting,
            String questionText,
            String helperText,
            QuestionCategory category,
            QuestionAnswerType answerType,
            Boolean required,
            Integer minLength,
            Integer maxLength,
            Integer sortOrder
    ) {
        return new JobPostingQuestion(
                jobPosting,
                null,
                questionText,
                helperText,
                category,
                answerType,
                required,
                minLength,
                maxLength,
                sortOrder
        );
    }

    public void update(
            String questionText,
            String helperText,
            QuestionCategory category,
            QuestionAnswerType answerType,
            Boolean required,
            Integer minLength,
            Integer maxLength,
            Integer sortOrder
    ) {
        this.questionText = questionText;
        this.helperText = helperText;
        this.category = category;
        this.answerType = answerType;
        this.required = required;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.sortOrder = sortOrder;
    }

    public void changeOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void deactivate() {
        this.active = false;
    }
}
