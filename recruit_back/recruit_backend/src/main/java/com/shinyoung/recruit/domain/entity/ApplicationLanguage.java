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

import java.time.LocalDate;

@Entity
@Table(
        name = "application_language",
        indexes = {
                @Index(name = "idx_application_language_application", columnList = "job_application_id"),
                @Index(name = "idx_application_language_sort", columnList = "job_application_id,sort_order")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationLanguage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    // 공통코드 LANGUAGE_TYPE 코드값(직접입력은 ETC). 파기 대상이라 nullable — 입력 필수는 request 검증이 보장한다.
    private String languageCode;

    @Column(nullable = false)
    private String languageName;

    // 공통코드 LANGUAGE_TEST_{languageCode} 코드값(직접입력은 ETC). nullable 사유는 languageCode 와 동일하다.
    private String testCode;

    @Column(nullable = false)
    private String testName;

    private String scoreOrGrade;

    private String conversationalAbility;

    // 파기(ALTER_NULLABLE+NULLIFY, PII 인벤토리 §5) 대상이라 nullable — 입력 필수는 request 검증이 보장한다(9d-1).
    private LocalDate examDate;

    private LocalDate expiredDate;

    private String issuingOrganization;

    @Column(nullable = false)
    private Integer sortOrder;

    private ApplicationLanguage(
            JobApplication jobApplication,
            String languageCode,
            String languageName,
            String testCode,
            String testName,
            String scoreOrGrade,
            String conversationalAbility,
            LocalDate examDate,
            LocalDate expiredDate,
            String issuingOrganization,
            Integer sortOrder
    ) {
        this.jobApplication = jobApplication;
        this.languageCode = languageCode;
        this.languageName = languageName;
        this.testCode = testCode;
        this.testName = testName;
        this.scoreOrGrade = scoreOrGrade;
        this.conversationalAbility = conversationalAbility;
        this.examDate = examDate;
        this.expiredDate = expiredDate;
        this.issuingOrganization = issuingOrganization;
        this.sortOrder = sortOrder;
    }

    public static ApplicationLanguage create(
            JobApplication jobApplication,
            String languageCode,
            String languageName,
            String testCode,
            String testName,
            String scoreOrGrade,
            String conversationalAbility,
            LocalDate examDate,
            LocalDate expiredDate,
            String issuingOrganization,
            Integer sortOrder
    ) {
        return new ApplicationLanguage(
                jobApplication,
                languageCode,
                languageName,
                testCode,
                testName,
                scoreOrGrade,
                conversationalAbility,
                examDate,
                expiredDate,
                issuingOrganization,
                sortOrder
        );
    }
}
