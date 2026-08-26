package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.exception.InvalidFaqException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * FAQ 한 건(질문 + 답변). 반드시 하나의 {@link FaqCategory} 에 속한다.
 *
 * <p>{@code answer} 는 평문이다. HTML 을 저장하지 않으며 프론트도 이스케이프해서 렌더링한다.
 * {@code sortOrder} 는 카테고리 내부 정렬값이라 카테고리가 바뀌면 재부여한다.
 */
@Entity
@Getter
@Table(
        name = "faq",
        indexes = {
                @Index(name = "idx_faq_category", columnList = "faq_category_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq extends BaseEntity {

    private static final int QUESTION_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faq_category_id", nullable = false)
    private FaqCategory category;

    @Column(nullable = false, length = QUESTION_MAX_LENGTH)
    private String question;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String answer;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private boolean active;

    private Faq(FaqCategory category, String question, String answer, Integer sortOrder, boolean active) {
        this.category = requireCategory(category);
        this.question = requireQuestion(question);
        this.answer = requireAnswer(answer);
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.active = active;
    }

    public static Faq create(FaqCategory category, String question, String answer, Integer sortOrder, Boolean active) {
        return new Faq(category, question, answer, sortOrder, active == null || active);
    }

    /**
     * 카테고리 이동을 허용한다. 이동한 경우 새 카테고리 기준 sortOrder 를 호출부가 넘긴다.
     * 같은 카테고리면 {@code movedSortOrder} 는 null 이고 기존 순서를 유지한다.
     */
    public void update(FaqCategory category, String question, String answer, Boolean active, Integer movedSortOrder) {
        this.category = requireCategory(category);
        this.question = requireQuestion(question);
        this.answer = requireAnswer(answer);
        if (active != null) {
            this.active = active;
        }
        if (movedSortOrder != null) {
            this.sortOrder = movedSortOrder;
        }
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void deactivate() {
        this.active = false;
    }

    private static FaqCategory requireCategory(FaqCategory category) {
        if (category == null) {
            throw new InvalidFaqException("카테고리는 필수입니다.");
        }
        return category;
    }

    private static String requireQuestion(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidFaqException("질문은(는) 필수입니다.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > QUESTION_MAX_LENGTH) {
            throw new InvalidFaqException("질문은 " + QUESTION_MAX_LENGTH + "자를 초과할 수 없습니다.");
        }
        return trimmed;
    }

    private static String requireAnswer(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidFaqException("답변은(는) 필수입니다.");
        }
        return value.trim();
    }
}
