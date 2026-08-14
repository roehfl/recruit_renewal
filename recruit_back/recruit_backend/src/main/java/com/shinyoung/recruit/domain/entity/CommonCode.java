package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.exception.InvalidCommonCodeException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자가 런타임에 관리하는 코드성 lookup master(Phase 08a).
 *
 * <p>{@code groupCode + code} 로 식별하고, public read 는 {@code active=true} 만 {@code sortOrder} 순으로 노출한다.
 * {@code code} 는 생성 후 불변(프론트가 저장·참조하는 안정 키)이며 삭제는 {@code active=false} soft delete 로만 한다.
 * 기존 enum 을 대체하지 않는 추가형 도입이다(ADR 0003).
 */
@Entity
@Getter
@Table(
        name = "common_code",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_common_code_group_code", columnNames = {"group_code", "code"})
        },
        indexes = {
                @Index(name = "idx_common_code_group", columnList = "group_code")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommonCode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_code", nullable = false, length = 100)
    private String groupCode;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String displayName;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 500)
    private String description;

    private CommonCode(
            String groupCode,
            String code,
            String displayName,
            Integer sortOrder,
            boolean active,
            String description
    ) {
        this.groupCode = requireText(groupCode, "groupCode");
        this.code = requireText(code, "code");
        this.displayName = requireText(displayName, "displayName");
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.active = active;
        this.description = normalize(description);
    }

    public static CommonCode create(
            String groupCode,
            String code,
            String displayName,
            Integer sortOrder,
            Boolean active,
            String description
    ) {
        return new CommonCode(groupCode, code, displayName, sortOrder, active == null || active, description);
    }

    /**
     * code/groupCode 는 불변. displayName/sortOrder/active/description 만 변경한다.
     */
    public void update(String displayName, Integer sortOrder, Boolean active, String description) {
        this.displayName = requireText(displayName, "displayName");
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        if (active != null) {
            this.active = active;
        }
        this.description = normalize(description);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidCommonCodeException(field + "은(는) 필수입니다.");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
