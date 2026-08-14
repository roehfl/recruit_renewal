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
        name = "application_certificate",
        indexes = {
                @Index(name = "idx_application_certificate_application", columnList = "job_application_id"),
                @Index(name = "idx_application_certificate_sort", columnList = "job_application_id,sort_order")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationCertificate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false)
    private JobApplication jobApplication;

    @Column(nullable = false)
    private String certificateName;

    @Column(nullable = false)
    private String issuingOrganization;

    // 파기(ALTER_NULLABLE+NULLIFY, PII 인벤토리 §5) 대상이라 nullable — 입력 필수는 request 검증이 보장한다(9d-1).
    private LocalDate acquiredDate;

    private String certificateNumber;

    private LocalDate expiredDate;

    private String scoreOrGrade;

    @Column(nullable = false)
    private Integer sortOrder;

    private ApplicationCertificate(
            JobApplication jobApplication,
            String certificateName,
            String issuingOrganization,
            LocalDate acquiredDate,
            String certificateNumber,
            LocalDate expiredDate,
            String scoreOrGrade,
            Integer sortOrder
    ) {
        this.jobApplication = jobApplication;
        this.certificateName = certificateName;
        this.issuingOrganization = issuingOrganization;
        this.acquiredDate = acquiredDate;
        this.certificateNumber = certificateNumber;
        this.expiredDate = expiredDate;
        this.scoreOrGrade = scoreOrGrade;
        this.sortOrder = sortOrder;
    }

    public static ApplicationCertificate create(
            JobApplication jobApplication,
            String certificateName,
            String issuingOrganization,
            LocalDate acquiredDate,
            String certificateNumber,
            LocalDate expiredDate,
            String scoreOrGrade,
            Integer sortOrder
    ) {
        return new ApplicationCertificate(
                jobApplication,
                certificateName,
                issuingOrganization,
                acquiredDate,
                certificateNumber,
                expiredDate,
                scoreOrGrade,
                sortOrder
        );
    }
}
