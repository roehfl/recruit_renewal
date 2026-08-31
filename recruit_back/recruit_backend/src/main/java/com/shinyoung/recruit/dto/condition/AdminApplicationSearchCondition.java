package com.shinyoung.recruit.dto.condition;

import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.FinalSchoolCondition;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;

import java.time.LocalDate;

public record AdminApplicationSearchCondition(
        Long jobPostingId,
        Long jobPositionId,
        JobApplicationStatus status,
        JobPositionApplicationType applicationType,
        String workLocation,
        String name,
        /** 숫자만 남긴 휴대폰 검색어. 저장값도 같은 방식으로 정규화해 비교한다. */
        String phoneNumber,
        LocalDate birthDateFrom,
        LocalDate birthDateTo,
        EducationLevel finalEducationLevel,
        String schoolName,
        GraduationStatus graduationStatus,
        FinalSchoolCondition finalSchoolCondition,
        String certificateName,
        String languageName,
        String languageLevel,
        StageType stageType,
        StageResultStatus stageResultStatus
) {

    /**
     * 최종학력 비교용 rank. EducationLevel 은 선언 순서(HIGH_SCHOOL→DOCTOR)가 학력 서열이므로 ordinal 을 그대로 쓴다.
     * repository 쿼리의 CASE rank 매핑과 반드시 일치해야 한다.
     */
    public Integer finalEducationRank() {
        return finalEducationLevel == null ? null : finalEducationLevel.ordinal();
    }

    /** JPQL 문자열 비교용(enum 파라미터로 5분기 비교가 불가해 name 을 넘긴다). */
    public String finalSchoolConditionName() {
        return finalSchoolCondition == null ? null : finalSchoolCondition.name();
    }
}
