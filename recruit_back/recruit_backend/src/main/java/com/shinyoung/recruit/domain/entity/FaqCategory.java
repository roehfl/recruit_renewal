package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.exception.InvalidFaqException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FAQ 카테고리(예: "지원서 관련"). 지원자 FAQ 화면 좌측 목록의 한 항목이다.
 *
 * <p>{@code sortOrder} 는 전역 정렬값이고 {@code active} 가 지원자 화면 노출 여부다.
 * 삭제는 {@code active=false} soft delete 로만 하며 row 를 지우지 않는다(하위 {@link Faq} 보존).
 */
@Entity
@Getter
@Table(
        name = "faq_category",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_faq_category_name", columnNames = {"name"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private boolean active;

    private FaqCategory(String name, Integer sortOrder, boolean active) {
        this.name = requireText(name);
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.active = active;
    }

    public static FaqCategory create(String name, Integer sortOrder, Boolean active) {
        return new FaqCategory(name, sortOrder, active == null || active);
    }

    /** sortOrder 는 reorder 전용이라 여기서 바꾸지 않는다. */
    public void update(String name, Boolean active) {
        this.name = requireText(name);
        if (active != null) {
            this.active = active;
        }
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void deactivate() {
        this.active = false;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidFaqException("카테고리명은(는) 필수입니다.");
        }
        return value.trim();
    }
}
