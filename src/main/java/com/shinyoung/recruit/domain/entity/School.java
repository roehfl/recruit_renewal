package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.exception.InvalidSchoolException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학교 master(Phase 08b). 지원자 학력 입력의 자동완성/검색과 학교별 통계 grouping 의 기준이다(ADR 0004).
 *
 * <p>식별/중복제거는 {@code (schoolName, schoolType, region)} fallback 기준이다(재import 멱등은 08c).
 * 서술 필드는 수정 가능하다. {@code schoolType}/{@code schoolCategory}/{@code educationMode} 는 코드 문자열
 * (표시는 프론트가 CommonCode group 으로 렌더)이며 백엔드 validation 은 결합하지 않는다.
 */
@Entity
@Getter
@Table(
        name = "school",
        indexes = {
                @Index(name = "idx_school_name", columnList = "school_name"),
                @Index(name = "idx_school_type", columnList = "school_type")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class School extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_name", nullable = false, length = 200)
    private String schoolName;

    @Column(name = "school_type", length = 50)
    private String schoolType;

    @Column(name = "school_category", length = 50)
    private String schoolCategory;

    @Column(name = "education_mode", length = 50)
    private String educationMode;

    @Column(length = 100)
    private String region;

    @Column(length = 500)
    private String address;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(nullable = false)
    private boolean active;

    private School(
            String schoolName,
            String schoolType,
            String schoolCategory,
            String educationMode,
            String region,
            String address,
            String countryCode,
            boolean active
    ) {
        this.schoolName = requireText(schoolName);
        this.schoolType = normalize(schoolType);
        this.schoolCategory = normalize(schoolCategory);
        this.educationMode = normalize(educationMode);
        this.region = normalize(region);
        this.address = normalize(address);
        this.countryCode = normalize(countryCode);
        this.active = active;
    }

    public static School create(
            String schoolName,
            String schoolType,
            String schoolCategory,
            String educationMode,
            String region,
            String address,
            String countryCode,
            Boolean active
    ) {
        return new School(schoolName, schoolType, schoolCategory, educationMode, region, address, countryCode,
                active == null || active);
    }

    /**
     * 서술 필드만 변경한다.
     */
    public void update(
            String schoolName,
            String schoolType,
            String schoolCategory,
            String educationMode,
            String region,
            String address,
            String countryCode,
            Boolean active
    ) {
        this.schoolName = requireText(schoolName);
        this.schoolType = normalize(schoolType);
        this.schoolCategory = normalize(schoolCategory);
        this.educationMode = normalize(educationMode);
        this.region = normalize(region);
        this.address = normalize(address);
        this.countryCode = normalize(countryCode);
        if (active != null) {
            this.active = active;
        }
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidSchoolException("schoolName은(는) 필수입니다.");
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
