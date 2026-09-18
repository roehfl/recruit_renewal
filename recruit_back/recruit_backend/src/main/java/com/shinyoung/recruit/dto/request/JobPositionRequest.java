package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record JobPositionRequest(
        @NotBlank @Size(max = 100) String positionName,
        JobPositionApplicationType applicationType,
        @Size(max = 100) String jobTitle,
        /** CommonCode 그룹 {@code WORK_LOCATION} 의 code 목록. 비면 근무지 선택 없는 모집분야다. */
        List<String> workLocationCodes,
        EmploymentType employmentType,
        @NotNull @Min(0) Integer sortOrder,
        /** 공고 수정 시 기존 모집분야 id. 있으면 제자리 수정, 없으면 새로 추가한다. 공고 생성에서는 무시한다. */
        Long id
) {
    public JobPositionRequest(
            String positionName,
            JobPositionApplicationType applicationType,
            String jobTitle,
            List<String> workLocationCodes,
            EmploymentType employmentType,
            Integer sortOrder
    ) {
        this(positionName, applicationType, jobTitle, workLocationCodes, employmentType, sortOrder, null);
    }

    public JobPositionRequest(String positionName, Integer sortOrder) {
        this(positionName, null, null, null, null, sortOrder);
    }
}
