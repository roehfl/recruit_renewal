package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.condition.AdminApplicationSearchCondition;
import com.shinyoung.recruit.dto.request.AdminApplicationSearchRequest;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.FinalSchoolCondition;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 관리자 지원현황 검색 조건을 만든다. 목록 조회와 엑셀 export 가 <b>같은 조건 객체</b>를 쓰도록 한 곳에 둔다.
 *
 * <p>과거에는 목록만 이 로직을 갖고 export 는 {@code jobPostingId}/{@code jobPositionId}/{@code status}
 * 3개만 받았다. 그래서 화면에서 이름으로 검색한 뒤 엑셀을 받으면 필터가 적용되지 않은 전체 결과가 내려갔다.
 * 조건 생성을 공유해 그 불일치가 다시 생기지 않게 한다.
 */
@Component
public class AdminApplicationSearchConditionFactory {

    public AdminApplicationSearchCondition create(Long jobPostingId, AdminApplicationSearchRequest request) {
        if (request == null) {
            return new AdminApplicationSearchCondition(
                    jobPostingId, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null);
        }
        if (request.birthDateFrom() != null && request.birthDateTo() != null
                && request.birthDateFrom().isAfter(request.birthDateTo())) {
            throw new InvalidJobApplicationException("생년월일 검색 범위가 올바르지 않습니다.");
        }
        return new AdminApplicationSearchCondition(
                jobPostingId,
                request.jobPositionId(),
                parseStatus(request.status()),
                parseSearchEnum(JobPositionApplicationType.class, request.applicationType(), "지원구분"),
                normalizeSearchText(request.workLocation()),
                normalizeSearchText(request.name()),
                normalizePhoneNumber(request.phoneNumber()),
                request.birthDateFrom(),
                request.birthDateTo(),
                parseSearchEnum(EducationLevel.class, request.finalEducationLevel(), "최종학력"),
                normalizeSearchText(request.schoolName()),
                parseSearchEnum(GraduationStatus.class, request.graduationStatus(), "졸업여부"),
                parseSearchEnum(FinalSchoolCondition.class, request.finalSchoolCondition(), "최종학교조건"),
                normalizeSearchText(request.certificateName()),
                normalizeSearchText(request.languageName()),
                normalizeSearchText(request.languageLevel()),
                parseSearchEnum(StageType.class, request.stageType(), "전형단계"),
                parseSearchEnum(StageResultStatus.class, request.stageResultStatus(), "전형결과")
        );
    }

    public JobApplicationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return JobApplicationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidJobApplicationException("지원서 상태 값이 올바르지 않습니다. status=" + status);
        }
    }

    private <E extends Enum<E>> E parseSearchEnum(Class<E> type, String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidJobApplicationException(label + " 값이 올바르지 않습니다. value=" + value);
        }
    }

    /**
     * 휴대폰 검색어에서 숫자만 남긴다. 저장값의 하이픈/공백 유무가 제각각이라 양쪽을 같은 방식으로
     * 정규화해 비교한다.
     */
    private String normalizePhoneNumber(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    private String normalizeSearchText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
