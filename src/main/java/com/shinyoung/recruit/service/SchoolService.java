package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.School;
import com.shinyoung.recruit.domain.repository.SchoolRepository;
import com.shinyoung.recruit.dto.request.SchoolCreateRequest;
import com.shinyoung.recruit.dto.request.SchoolUpdateRequest;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.dto.response.SchoolResponse;
import com.shinyoung.recruit.dto.response.SchoolSearchResponse;
import com.shinyoung.recruit.exception.InvalidSchoolException;
import com.shinyoung.recruit.exception.SchoolNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * School 관리(Phase 08b). public 자동완성(활성 top-N) + admin CRUD(비활성 포함 페이지 목록).
 * {@code schoolCode} 는 식별 키라 생성 후 불변이고, 중복은 선검사 + DB unique 제약(동시성)으로 막아 400 으로 변환한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolService {

    /** public 자동완성 결과 상한(cardinality/성능). */
    private static final int SEARCH_LIMIT = 20;

    private final SchoolRepository schoolRepository;

    /** public: 활성 학교 이름 검색(prefix 우선 + contains), top-N. q 가 비면 빈 목록. */
    public List<SchoolSearchResponse> search(String q, String schoolType) {
        String query = blankToNull(q);
        if (query == null) {
            return List.of();
        }
        return schoolRepository.search(query, blankToNull(schoolType), PageRequest.of(0, SEARCH_LIMIT))
                .stream()
                .map(SchoolSearchResponse::from)
                .toList();
    }

    /** admin: 비활성 포함 페이지 목록(q/schoolType 옵션). */
    public PageResponse<SchoolResponse> getAdminSchools(String q, String schoolType, int page, int size) {
        Page<SchoolResponse> result = schoolRepository.adminSearch(
                        blankToNull(q),
                        blankToNull(schoolType),
                        PageRequest.of(page, size, Sort.by("schoolName").ascending().and(Sort.by("id").ascending())))
                .map(SchoolResponse::from);
        return PageResponse.from(result);
    }

    @Transactional
    public SchoolResponse create(SchoolCreateRequest request) {
        String schoolCode = blankToNull(request.schoolCode());
        if (schoolCode != null && schoolRepository.existsBySchoolCode(schoolCode)) {
            throw new InvalidSchoolException("이미 존재하는 schoolCode입니다. " + schoolCode);
        }
        try {
            // 동시 생성 race 의 unique violation 은 flush 시점에 400 으로 변환한다.
            School saved = schoolRepository.saveAndFlush(School.create(
                    schoolCode,
                    request.schoolName(),
                    request.schoolType(),
                    request.educationMode(),
                    request.region(),
                    request.address(),
                    request.countryCode(),
                    request.active()
            ));
            return SchoolResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new InvalidSchoolException("이미 존재하는 schoolCode입니다. " + schoolCode);
        }
    }

    @Transactional
    public SchoolResponse update(Long id, SchoolUpdateRequest request) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException("학교를 찾을 수 없습니다. id=" + id));
        school.update(
                request.schoolName(),
                request.schoolType(),
                request.educationMode(),
                request.region(),
                request.address(),
                request.countryCode(),
                request.active()
        );
        return SchoolResponse.from(school);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
