package com.shinyoung.recruit.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 면접 단계 하나에 붙는 추가사항(질문 세트). 행이 있으면 그 단계에서 추가사항을 받는다.
 */
@Entity
@Getter
@Table(
        name = "interview_supplement",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_interview_supplement_stage", columnNames = {"stage_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewSupplement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    private InterviewSupplement(Stage stage) {
        this.stage = stage;
    }

    public static InterviewSupplement create(Stage stage) {
        return new InterviewSupplement(stage);
    }
}
