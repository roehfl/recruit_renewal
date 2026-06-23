package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;

import java.time.LocalDate;

public record BasicInfoResponse(
        Long basicInfoId,
        boolean persisted,
        String nameKorean,
        String nameEnglish,
        NationalityType nationalityType,
        String countryCode,
        LocalDate birthDate,
        String mobilePhone,
        String emergencyPhone,
        String email,
        VeteranStatus veteranStatus,
        String veteranType,
        DisabilityStatus disabilityStatus,
        String disabilityGradeCode,
        String disabilityTypeCode,
        String zipCode,
        String addressBasic,
        String addressDetail
) {

    public static BasicInfoResponse of(ApplicationBasicInfo basicInfo) {
        return new BasicInfoResponse(
                basicInfo.getId(), true,
                basicInfo.getNameKorean(), basicInfo.getNameEnglish(),
                basicInfo.getNationalityType(), basicInfo.getCountryCode(), basicInfo.getBirthDate(),
                basicInfo.getMobilePhone(), basicInfo.getEmergencyPhone(), basicInfo.getEmail(),
                basicInfo.getVeteranStatus(), basicInfo.getVeteranType(), basicInfo.getDisabilityStatus(),
                basicInfo.getDisabilityGradeCode(), basicInfo.getDisabilityTypeCode(),
                basicInfo.getZipCode(), basicInfo.getAddressBasic(), basicInfo.getAddressDetail());
    }

    /** 미저장 시 Applicant 기반 prefill projection(B안). 저장 가능 필드만 채우고 basicInfoId=null, persisted=false. */
    public static BasicInfoResponse prefill(Applicant applicant) {
        return new BasicInfoResponse(
                null, false,
                applicant.getUserName(), null,
                null, null, null,
                applicant.getPhoneNumber(), null, applicant.getEmail(),
                null, null, null,
                null, null,
                null, null, null);
    }
}
