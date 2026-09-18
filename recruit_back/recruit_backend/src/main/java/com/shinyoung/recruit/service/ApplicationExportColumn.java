package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.response.ApplicationExportColumnGroupResponse;
import com.shinyoung.recruit.exception.InvalidJobApplicationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 지원현황 엑셀 컬럼 카탈로그 — 모달 체크박스와 엑셀 헤더의 단일 출처. 엑셀 컬럼 순서 = 선언 순서.
 *
 * <p>항목을 추가·삭제하면 {@link ApplicationExportRowAssembler} 의 값 계산 switch 가 컴파일 오류로 알려 준다
 * (switch 가 모든 상수를 다뤄야 한다). 프론트는 카탈로그 API 응답으로 그리므로 고칠 필요가 없다.
 *
 * <p>{@code section} 이 null 인 컬럼은 base projection({@code ApplicationExportRow})만으로 값을 만든다.
 */
public enum ApplicationExportColumn {

    APPLICATION_ID(Group.APPLICATION, "수험번호", true, null, false),
    JOB_POSTING_TITLE(Group.APPLICATION, "공고명", false, null, false),
    APPLICATION_TYPE(Group.APPLICATION, "지원구분", false, null, false),
    JOB_POSITION_NAME(Group.APPLICATION, "지원분야", true, null, false),
    JOB_TITLE(Group.APPLICATION, "직무", false, null, false),
    WORK_LOCATION(Group.APPLICATION, "근무지", true, null, false),
    STATUS(Group.APPLICATION, "지원상태", true, null, false),
    SUBMITTED_AT(Group.APPLICATION, "최종제출일시", true, null, false),
    CREATED_AT(Group.APPLICATION, "작성시작일시", false, null, false),
    UPDATED_AT(Group.APPLICATION, "최종수정일시", false, null, false),
    WITHDRAWN_AT(Group.APPLICATION, "철회일시", false, null, false),

    LATEST_STAGE_RESULT(Group.STAGE_RESULT, "최신 전형결과", true, ApplicationExportSection.STAGE_RESULT, false),
    STAGE_RESULTS(Group.STAGE_RESULT, "전형별 결과", false, ApplicationExportSection.STAGE_RESULT, true),

    NAME(Group.BASIC_INFO, "이름", true, ApplicationExportSection.BASIC_INFO, false),
    NAME_ENGLISH(Group.BASIC_INFO, "영문이름", false, ApplicationExportSection.BASIC_INFO, false),
    NATIONALITY(Group.BASIC_INFO, "국적", false, ApplicationExportSection.BASIC_INFO, false),
    BIRTH_DATE(Group.BASIC_INFO, "생년월일", true, ApplicationExportSection.BASIC_INFO, false),
    AGE(Group.BASIC_INFO, "나이", true, ApplicationExportSection.BASIC_INFO, false),
    MOBILE_PHONE(Group.BASIC_INFO, "휴대폰", true, ApplicationExportSection.BASIC_INFO, false),
    EMAIL(Group.BASIC_INFO, "이메일", true, ApplicationExportSection.BASIC_INFO, false),
    EMERGENCY_PHONE(Group.BASIC_INFO, "비상연락처", false, ApplicationExportSection.BASIC_INFO, false),
    ADDRESS(Group.BASIC_INFO, "주소", false, ApplicationExportSection.BASIC_INFO, false),
    VETERAN(Group.BASIC_INFO, "보훈", false, ApplicationExportSection.BASIC_INFO, false),
    DISABILITY(Group.BASIC_INFO, "장애", false, ApplicationExportSection.BASIC_INFO, false),
    APPLICATION_ROUTE(Group.BASIC_INFO, "지원경로", false, ApplicationExportSection.BASIC_INFO, false),

    MILITARY(Group.MILITARY, "병역", false, ApplicationExportSection.MILITARY, false),

    FINAL_EDUCATION_LEVEL(Group.FINAL_EDUCATION, "최종학력", true, ApplicationExportSection.EDUCATION, false),
    FINAL_SCHOOL_NAME(Group.FINAL_EDUCATION, "최종학교", true, ApplicationExportSection.EDUCATION, false),
    FINAL_MAJOR(Group.FINAL_EDUCATION, "전공", false, ApplicationExportSection.EDUCATION, false),
    FINAL_ADDITIONAL_MAJOR(Group.FINAL_EDUCATION, "부·복수전공", false, ApplicationExportSection.EDUCATION, false),
    FINAL_GRADUATION_STATUS(Group.FINAL_EDUCATION, "졸업구분", false, ApplicationExportSection.EDUCATION, false),
    FINAL_ADMISSION_DATE(Group.FINAL_EDUCATION, "입학년월", false, ApplicationExportSection.EDUCATION, false),
    FINAL_GRADUATION_DATE(Group.FINAL_EDUCATION, "졸업년월", true, ApplicationExportSection.EDUCATION, false),
    FINAL_GPA(Group.FINAL_EDUCATION, "평점", false, ApplicationExportSection.EDUCATION, false),
    FINAL_MAJOR_GPA(Group.FINAL_EDUCATION, "전공평점", false, ApplicationExportSection.EDUCATION, false),
    FINAL_SCHOOL_LOCATION(Group.FINAL_EDUCATION, "국내/해외", false, ApplicationExportSection.EDUCATION, false),
    FINAL_SCHOOL_TYPE(Group.FINAL_EDUCATION, "편입·분교·야간", false, ApplicationExportSection.EDUCATION, false),

    EDUCATIONS(Group.SUMMARY, "학력", false, ApplicationExportSection.EDUCATION, true),
    CAREERS(Group.SUMMARY, "경력", false, ApplicationExportSection.CAREER, true),
    CURRENT_SALARY(Group.SUMMARY, "현재연봉(만원)", false, ApplicationExportSection.CAREER, true),
    CERTIFICATES(Group.SUMMARY, "자격증", false, ApplicationExportSection.CERTIFICATE, true),
    LANGUAGES(Group.SUMMARY, "어학", false, ApplicationExportSection.LANGUAGE, true),
    AWARDS(Group.SUMMARY, "수상", false, ApplicationExportSection.AWARD, true),
    GAP_PERIODS(Group.SUMMARY, "공백기간", false, ApplicationExportSection.GAP_PERIOD, true);

    /** 모달 체크박스 묶음. 순서 = 선언 순서. */
    public enum Group {
        APPLICATION("지원사항"),
        STAGE_RESULT("전형결과"),
        BASIC_INFO("기본정보"),
        MILITARY("병역"),
        FINAL_EDUCATION("최종학력"),
        SUMMARY("다건 요약");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Group group;
    private final String label;
    private final boolean defaultSelected;
    private final ApplicationExportSection section;
    private final boolean wrapText;

    ApplicationExportColumn(
            Group group,
            String label,
            boolean defaultSelected,
            ApplicationExportSection section,
            boolean wrapText
    ) {
        this.group = group;
        this.label = label;
        this.defaultSelected = defaultSelected;
        this.section = section;
        this.wrapText = wrapText;
    }

    public String label() {
        return label;
    }

    public boolean wrapText() {
        return wrapText;
    }

    /** 모달을 열 때 체크되는 컬럼(= {@code columns} 미지정 요청의 컬럼). */
    public static List<ApplicationExportColumn> defaults() {
        return Arrays.stream(values()).filter(column -> column.defaultSelected).toList();
    }

    /**
     * 요청 {@code columns} 를 카탈로그 순서의 컬럼 목록으로 바꾼다. 빈 요청은 기본 컬럼(기존 호출 하위호환),
     * 대소문자·앞뒤 공백은 무시, 중복은 제거, 정의 밖 key 는 400.
     */
    public static List<ApplicationExportColumn> parse(List<String> keys) {
        List<String> requested = keys == null ? List.of() : keys.stream()
                .filter(key -> key != null && !key.isBlank())
                .map(key -> key.trim().toUpperCase(Locale.ROOT))
                .toList();
        if (requested.isEmpty()) {
            return defaults();
        }
        Set<ApplicationExportColumn> selected = EnumSet.noneOf(ApplicationExportColumn.class);
        for (String key : requested) {
            try {
                selected.add(valueOf(key));
            } catch (IllegalArgumentException e) {
                throw new InvalidJobApplicationException("엑셀 컬럼 값이 올바르지 않습니다. columns=" + key);
            }
        }
        // EnumSet 은 선언 순서로 순회한다 — 출력 순서가 체크 순서와 무관하게 고정된다.
        return List.copyOf(selected);
    }

    /** 선택 컬럼이 값을 만들기 위해 배치 조회해야 하는 섹션. */
    public static Set<ApplicationExportSection> requiredSections(Collection<ApplicationExportColumn> columns) {
        Set<ApplicationExportSection> sections = EnumSet.noneOf(ApplicationExportSection.class);
        for (ApplicationExportColumn column : columns) {
            if (column.section != null) {
                sections.add(column.section);
            }
        }
        return sections;
    }

    /** 카탈로그 조회 API 응답. 그룹·컬럼 모두 선언 순서. */
    public static List<ApplicationExportColumnGroupResponse> catalog() {
        Map<Group, List<ApplicationExportColumnGroupResponse.Column>> grouped = new LinkedHashMap<>();
        for (ApplicationExportColumn column : values()) {
            grouped.computeIfAbsent(column.group, group -> new ArrayList<>())
                    .add(new ApplicationExportColumnGroupResponse.Column(
                            column.name(), column.label, column.defaultSelected));
        }
        return grouped.entrySet().stream()
                .map(entry -> new ApplicationExportColumnGroupResponse(entry.getKey().label(), entry.getValue()))
                .toList();
    }
}
