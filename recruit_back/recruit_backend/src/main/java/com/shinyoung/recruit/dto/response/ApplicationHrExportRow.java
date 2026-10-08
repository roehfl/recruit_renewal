package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.Gender;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;

/**
 * 인사팀 양식 지원현황 엑셀 전용 projection row. {@link ApplicationExportRow} 와 같은 원칙(평탄한 값만,
 * ci/ciHash/password 미포함)이고, 양식에 필요한 계정 성별을 더한다.
 * {@code applicantName}/{@code phoneNumber}/{@code email} 은 기본정보가 없는 지원서의 fallback 이다.
 */
public record ApplicationHrExportRow(
        Long applicationId,
        String applicantName,
        String phoneNumber,
        String email,
        JobPositionApplicationType applicationType,
        String jobPositionName,
        String jobTitle,
        String workLocationName,
        Gender gender
) {
}
