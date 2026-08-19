package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.enumeration.QuestionAnswerType;
import com.shinyoung.recruit.enumeration.QuestionCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "question_template",
        indexes = {
                @Index(name = "idx_question_template_active", columnList = "active")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

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
    private Boolean defaultRequired;

    @Column(nullable = false)
    private Integer defaultMaxLength;

    @Column(nullable = false)
    private Boolean active;

    private QuestionTemplate(
            String title,
            String questionText,
            String helperText,
            QuestionCategory category,
            QuestionAnswerType answerType,
            Boolean defaultRequired,
            Integer defaultMaxLength
    ) {
        this.title = title;
        this.questionText = questionText;
        this.helperText = helperText;
        this.category = category;
        this.answerType = answerType;
        this.defaultRequired = defaultRequired;
        this.defaultMaxLength = defaultMaxLength;
        this.active = true;
    }

    public static QuestionTemplate create(
            String title,
            String questionText,
            String helperText,
            QuestionCategory category,
            QuestionAnswerType answerType,
            Boolean defaultRequired,
            Integer defaultMaxLength
    ) {
        return new QuestionTemplate(title, questionText, helperText, category, answerType, defaultRequired, defaultMaxLength);
    }

    public void update(
            String title,
            String questionText,
            String helperText,
            QuestionCategory category,
            QuestionAnswerType answerType,
            Boolean defaultRequired,
            Integer defaultMaxLength
    ) {
        this.title = title;
        this.questionText = questionText;
        this.helperText = helperText;
        this.category = category;
        this.answerType = answerType;
        this.defaultRequired = defaultRequired;
        this.defaultMaxLength = defaultMaxLength;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
