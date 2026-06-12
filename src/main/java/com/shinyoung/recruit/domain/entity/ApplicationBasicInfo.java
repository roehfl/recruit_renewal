package com.shinyoung.recruit.domain.entity;

import com.shinyoung.recruit.common.crypto.AesAttributeConverter;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
        name = "application_basic_info",
        indexes = {
                @Index(name = "idx_application_basic_info_application", columnList = "job_application_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationBasicInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_application_id", nullable = false, unique = true)
    private JobApplication jobApplication;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String nameKorean;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String nameEnglish;

    @Enumerated(EnumType.STRING)
    private NationalityType nationalityType;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String countryCode;

    private LocalDate birthDate;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String mobilePhone;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String emergencyPhone;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String email;

    @Enumerated(EnumType.STRING)
    private VeteranStatus veteranStatus;

    @Enumerated(EnumType.STRING)
    private DisabilityStatus disabilityStatus;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String disabilityGradeCode;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String disabilityTypeCode;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 500)
    private String zipCode;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 1000)
    private String addressBasic;

    @Convert(converter = AesAttributeConverter.class)
    @Column(length = 1000)
    private String addressDetail;

    private ApplicationBasicInfo(
            JobApplication jobApplication,
            String nameKorean,
            String nameEnglish,
            NationalityType nationalityType,
            String countryCode,
            LocalDate birthDate,
            String mobilePhone,
            String emergencyPhone,
            String email,
            VeteranStatus veteranStatus,
            DisabilityStatus disabilityStatus,
            String disabilityGradeCode,
            String disabilityTypeCode,
            String zipCode,
            String addressBasic,
            String addressDetail
    ) {
        this.jobApplication = jobApplication;
        this.nameKorean = nameKorean;
        this.nameEnglish = nameEnglish;
        this.nationalityType = nationalityType;
        this.countryCode = countryCode;
        this.birthDate = birthDate;
        this.mobilePhone = mobilePhone;
        this.emergencyPhone = emergencyPhone;
        this.email = email;
        this.veteranStatus = veteranStatus;
        this.disabilityStatus = disabilityStatus;
        this.disabilityGradeCode = disabilityGradeCode;
        this.disabilityTypeCode = disabilityTypeCode;
        this.zipCode = zipCode;
        this.addressBasic = addressBasic;
        this.addressDetail = addressDetail;
    }

    public static ApplicationBasicInfo create(
            JobApplication jobApplication,
            String nameKorean,
            String nameEnglish,
            NationalityType nationalityType,
            String countryCode,
            LocalDate birthDate,
            String mobilePhone,
            String emergencyPhone,
            String email,
            VeteranStatus veteranStatus,
            DisabilityStatus disabilityStatus,
            String disabilityGradeCode,
            String disabilityTypeCode,
            String zipCode,
            String addressBasic,
            String addressDetail
    ) {
        return new ApplicationBasicInfo(
                jobApplication, nameKorean, nameEnglish, nationalityType, countryCode, birthDate,
                mobilePhone, emergencyPhone, email, veteranStatus, disabilityStatus,
                disabilityGradeCode, disabilityTypeCode, zipCode, addressBasic, addressDetail
        );
    }

    public void update(
            String nameKorean,
            String nameEnglish,
            NationalityType nationalityType,
            String countryCode,
            LocalDate birthDate,
            String mobilePhone,
            String emergencyPhone,
            String email,
            VeteranStatus veteranStatus,
            DisabilityStatus disabilityStatus,
            String disabilityGradeCode,
            String disabilityTypeCode,
            String zipCode,
            String addressBasic,
            String addressDetail
    ) {
        this.nameKorean = nameKorean;
        this.nameEnglish = nameEnglish;
        this.nationalityType = nationalityType;
        this.countryCode = countryCode;
        this.birthDate = birthDate;
        this.mobilePhone = mobilePhone;
        this.emergencyPhone = emergencyPhone;
        this.email = email;
        this.veteranStatus = veteranStatus;
        this.disabilityStatus = disabilityStatus;
        this.disabilityGradeCode = disabilityGradeCode;
        this.disabilityTypeCode = disabilityTypeCode;
        this.zipCode = zipCode;
        this.addressBasic = addressBasic;
        this.addressDetail = addressDetail;
    }
}
