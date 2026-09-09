package com.shinyoung.recruit.service;

import com.shinyoung.recruit.enumeration.CampusType;
import com.shinyoung.recruit.enumeration.DayNightType;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.GapType;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.MilitaryBranch;
import com.shinyoung.recruit.enumeration.MilitaryRank;
import com.shinyoung.recruit.enumeration.MilitaryServiceType;
import com.shinyoung.recruit.enumeration.MilitarySubjectType;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application PDF 표시용 enum 한글 라벨. 도메인 enum은 표시 문구를 갖지 않으므로 PDF 전용 매핑으로 격리한다.
 *
 * <p>문구는 관리자 화면(<code>Application.vue</code> + <code>applicationSections.ts</code>의 SECTION_MAP)과
 * 동일하게 맞춰 화면과 인쇄물의 용어를 일치시킨다. 매핑에 없는 값은 enum 이름을 그대로 노출한다
 * (누락을 감추지 않고 드러내기 위함).
 */
final class ApplicationPdfLabels {

    private static final Map<Enum<?>, String> LABELS = build();

    private ApplicationPdfLabels() {
    }

    /** enum을 한글 표시 문구로 바꾼다. null 이면 빈 문자열. */
    static String label(Enum<?> value) {
        if (value == null) {
            return "";
        }
        return LABELS.getOrDefault(value, value.name());
    }

    private static Map<Enum<?>, String> build() {
        Map<Enum<?>, String> map = new LinkedHashMap<>();

        // 학력
        map.put(EducationLevel.HIGH_SCHOOL, "고등학교");
        map.put(EducationLevel.COLLEGE, "전문대학교");
        map.put(EducationLevel.UNIVERSITY, "대학교");
        map.put(EducationLevel.MASTER, "대학원(석사)");
        map.put(EducationLevel.DOCTOR, "대학원(박사)");

        map.put(GraduationStatus.GRADUATED, "졸업");
        map.put(GraduationStatus.EXPECTED, "졸업예정");
        map.put(GraduationStatus.ENROLLED, "재학");
        map.put(GraduationStatus.LEAVE_OF_ABSENCE, "휴학");
        map.put(GraduationStatus.DROPPED_OUT, "중퇴");
        map.put(GraduationStatus.COMPLETED, "수료");

        map.put(DayNightType.DAY, "주간");
        map.put(DayNightType.NIGHT, "야간");
        map.put(DayNightType.CYBER, "사이버");
        map.put(DayNightType.UNKNOWN, "");

        map.put(CampusType.MAIN, "본교");
        map.put(CampusType.BRANCH, "분교");
        map.put(CampusType.UNKNOWN, "");

        // 경력
        map.put(EmploymentType.FULL_TIME, "정규직");
        map.put(EmploymentType.CONTRACT, "계약");
        map.put(EmploymentType.INTERN, "인턴");
        map.put(EmploymentType.FREELANCE, "프리랜서");
        map.put(EmploymentType.PART_TIME, "파트");
        map.put(EmploymentType.ETC, "기타");

        // 병역
        map.put(MilitarySubjectType.SUBJECT, "미필");
        map.put(MilitarySubjectType.NOT_SUBJECT, "대상아님");
        map.put(MilitarySubjectType.COMPLETED, "필");
        map.put(MilitarySubjectType.EXEMPTED, "면제");

        map.put(MilitaryBranch.ARMY, "육군");
        map.put(MilitaryBranch.NAVY, "해군");
        map.put(MilitaryBranch.AIR_FORCE, "공군");
        map.put(MilitaryBranch.MARINE, "해병대");
        map.put(MilitaryBranch.POLICE, "의무경찰");
        map.put(MilitaryBranch.FIRE_SERVICE, "의무소방");
        map.put(MilitaryBranch.ETC, "기타");

        map.put(MilitaryServiceType.ACTIVE_DUTY, "현역복무");
        map.put(MilitaryServiceType.SUPPLEMENTARY, "보충역");
        map.put(MilitaryServiceType.PUBLIC_SERVICE, "공익근무요원");
        map.put(MilitaryServiceType.INDUSTRIAL_TECHNICAL, "산업기능요원");
        map.put(MilitaryServiceType.PROFESSIONAL_RESEARCH, "전문연구요원");
        map.put(MilitaryServiceType.SOCIAL_SERVICE, "사회복무요원");
        map.put(MilitaryServiceType.OFFICER, "장교");
        map.put(MilitaryServiceType.NON_COMMISSIONED_OFFICER, "부사관");
        map.put(MilitaryServiceType.ETC, "기타");

        map.put(MilitaryRank.PRIVATE, "이병");
        map.put(MilitaryRank.PRIVATE_FIRST_CLASS, "일병");
        map.put(MilitaryRank.CORPORAL, "상병");
        map.put(MilitaryRank.SERGEANT, "병장");
        map.put(MilitaryRank.STAFF_SERGEANT, "하사");
        map.put(MilitaryRank.SERGEANT_FIRST_CLASS, "중사");
        map.put(MilitaryRank.MASTER_SERGEANT, "상사");
        map.put(MilitaryRank.WARRANT_OFFICER, "준위");
        map.put(MilitaryRank.SECOND_LIEUTENANT, "소위");
        map.put(MilitaryRank.FIRST_LIEUTENANT, "중위");
        map.put(MilitaryRank.CAPTAIN, "대위");
        map.put(MilitaryRank.MAJOR, "소령");
        map.put(MilitaryRank.ETC, "기타");

        // 기본 정보
        map.put(NationalityType.DOMESTIC, "내국인");
        map.put(NationalityType.FOREIGN, "외국인");
        map.put(VeteranStatus.SUBJECT, "대상");
        map.put(VeteranStatus.NOT_SUBJECT, "비대상");
        map.put(DisabilityStatus.SUBJECT, "대상");
        map.put(DisabilityStatus.NOT_SUBJECT, "비대상");

        // 지원사항
        map.put(JobPositionApplicationType.NEW_GRADUATE, "신입");
        map.put(JobPositionApplicationType.EXPERIENCED, "경력");
        map.put(JobPositionApplicationType.NEW_GRADUATE_OR_EXPERIENCED, "신입/경력");

        // 공백기간
        map.put(GapType.EDUCATION, "학업");
        map.put(GapType.CAREER, "경력");
        map.put(GapType.OTHER, "기타");

        return Collections.unmodifiableMap(map);
    }
}
