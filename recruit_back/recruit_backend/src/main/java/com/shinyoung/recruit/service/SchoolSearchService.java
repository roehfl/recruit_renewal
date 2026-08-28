package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.response.SchoolSearchResponse;
import com.shinyoung.recruit.enumeration.EducationLevel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 학교 자동완성 검색. 학교 구분에 따라 외부 OpenAPI 를 골라 호출하고, 중복 제거 후 상위 N 건만 돌려준다.
 *
 * <p>고등학교는 NEIS 학교기본정보, 그 외(전문대학·대학교·대학원)는 전국대학및전문대학정보 표준데이터를 쓴다.
 * 대학원은 대학구분명이 "대학원"인 행으로 따로 제공된다.
 */
@Service
public class SchoolSearchService {

    /** 자동완성 결과 상한(cardinality/성능). */
    private static final int SEARCH_LIMIT = 20;

    /** NEIS 학교종류명({@code SCHUL_KND_SC_NM}). */
    private static final String NEIS_KIND_HIGH_SCHOOL = "고등학교";

    private final NeisSchoolClient neisSchoolClient;
    private final UnivInfoSchoolClient univInfoSchoolClient;

    public SchoolSearchService(NeisSchoolClient neisSchoolClient, UnivInfoSchoolClient univInfoSchoolClient) {
        this.neisSchoolClient = neisSchoolClient;
        this.univInfoSchoolClient = univInfoSchoolClient;
    }

    /**
     * 학교를 검색한다. 검색어가 비면 외부 호출 없이 빈 목록을 준다(기존 동작 유지).
     *
     * @param q              학교명 검색어
     * @param educationLevel 학교 구분. 호출 대상 API 를 결정한다.
     */
    public List<SchoolSearchResponse> search(String q, EducationLevel educationLevel) {
        if (!StringUtils.hasText(q)) {
            return List.of();
        }
        String keyword = q.trim();

        List<SchoolSearchResponse> found = educationLevel == EducationLevel.HIGH_SCHOOL
                ? neisSchoolClient.search(keyword, NEIS_KIND_HIGH_SCHOOL)
                : univInfoSchoolClient.search(keyword, univSchoolKind(educationLevel));

        return distinctByCode(found);
    }

    /**
     * 대학 표준데이터의 대학구분명({@code UNIV_SE_NM}) 값. 대학원도 별도 행으로 제공되므로
     * 석사·박사는 대학원 목록에서 찾는다(예: "○○대학교 대학원", "○○대학교 교육대학원").
     */
    private static String univSchoolKind(EducationLevel educationLevel) {
        return switch (educationLevel) {
            case COLLEGE -> "전문대학";
            case UNIVERSITY -> "대학";
            case MASTER, DOCTOR -> "대학원";
            case HIGH_SCHOOL -> null;
        };
    }

    private static List<SchoolSearchResponse> distinctByCode(List<SchoolSearchResponse> schools) {
        Set<String> seen = new HashSet<>();
        List<SchoolSearchResponse> result = new ArrayList<>();
        for (SchoolSearchResponse school : schools) {
            if (result.size() >= SEARCH_LIMIT) {
                break;
            }
            if (seen.add(school.schoolCode())) {
                result.add(school);
            }
        }
        return result;
    }
}
