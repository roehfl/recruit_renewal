package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BasicInfoSaveRequest(
        @NotBlank @Size(max = 50) String nameKorean,
        @Size(max = 100) String nameEnglish,
        @NotNull NationalityType nationalityType,
        @Size(max = 50) String countryCode,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Size(max = 20) String mobilePhone,
        @Size(max = 20) String emergencyPhone,
        @NotBlank @Email @Size(max = 100) String email,
        @NotNull VeteranStatus veteranStatus,
        @NotNull DisabilityStatus disabilityStatus,
        @Size(max = 50) String disabilityGradeCode,
        @Size(max = 50) String disabilityTypeCode,
        @Size(max = 10) String zipCode,
        @Size(max = 200) String addressBasic,
        @Size(max = 200) String addressDetail
) {
}
