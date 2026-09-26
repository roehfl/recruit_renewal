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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "interview_supplement_question",
        indexes = {
                @Index(name = "idx_interview_supplement_question_supplement", columnList = "interview_supplement_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewSupplementQuestion extends BaseEntity {

    public static final int MAX_CONTENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_supplement_id", nullable = false)
    private InterviewSupplement supplement;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(nullable = false)
    private Integer sortOrder;

    private InterviewSupplementQuestion(InterviewSupplement supplement, String content, Integer sortOrder) {
        this.supplement = supplement;
        this.content = content;
        this.sortOrder = sortOrder;
    }

    public static InterviewSupplementQuestion create(InterviewSupplement supplement, String content, Integer sortOrder) {
        return new InterviewSupplementQuestion(supplement, content, sortOrder);
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void reorder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
