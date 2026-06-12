package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;

import java.time.LocalDate;

public record AdminBasicInfoResponse(
        Long basicInfoId,
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

    public static AdminBasicInfoResponse from(ApplicationBasicInfo basicInfo) {
        return new AdminBasicInfoResponse(
                basicInfo.getId(),
                basicInfo.getNameKorean(), basicInfo.getNameEnglish(),
                basicInfo.getNationalityType(), basicInfo.getCountryCode(), basicInfo.getBirthDate(),
                basicInfo.getMobilePhone(), basicInfo.getEmergencyPhone(), basicInfo.getEmail(),
                basicInfo.getVeteranStatus(), basicInfo.getDisabilityStatus(),
                basicInfo.getDisabilityGradeCode(), basicInfo.getDisabilityTypeCode(),
                basicInfo.getZipCode(), basicInfo.getAddressBasic(), basicInfo.getAddressDetail());
    }
}
