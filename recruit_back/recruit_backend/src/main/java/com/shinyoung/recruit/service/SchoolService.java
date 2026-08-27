package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.School;
import com.shinyoung.recruit.domain.repository.SchoolRepository;
import com.shinyoung.recruit.dto.request.SchoolCreateRequest;
import com.shinyoung.recruit.dto.request.SchoolUpdateRequest;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.dto.response.SchoolResponse;
import com.shinyoung.recruit.exception.SchoolNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * School 관리(Phase 08b). admin CRUD(비활성 포함 페이지 목록).
 *
 * <p>public 자동완성은 외부 OpenAPI 프록시({@link SchoolSearchService})로 이관했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolService {

    /** admin 목록 페이지 크기 상한(과대 size 요청 방어). */
    private static final int MAX_ADMIN_PAGE_SIZE = 200;

    private final SchoolRepository schoolRepository;

    /** admin: 비활성 포함 페이지 목록(q/schoolType 옵션). */
    public PageResponse<SchoolResponse> getAdminSchools(String q, String schoolType, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_ADMIN_PAGE_SIZE);
        String query = blankToNull(q);
        Page<SchoolResponse> result = schoolRepository.adminSearch(
                        query == null ? null : escapeLike(query),
                        blankToNull(schoolType),
                        PageRequest.of(safePage, safeSize,
                                Sort.by("schoolName").ascending().and(Sort.by("id").ascending())))
                .map(SchoolResponse::from);
        return PageResponse.from(result);
    }

    @Transactional
    public SchoolResponse create(SchoolCreateRequest request) {
        School saved = schoolRepository.save(School.create(
                request.schoolName(),
                request.schoolType(),
                request.schoolCategory(),
                request.educationMode(),
                request.region(),
                request.address(),
                request.countryCode(),
                request.active()
        ));
        return SchoolResponse.from(saved);
    }

    @Transactional
    public SchoolResponse update(Long id, SchoolUpdateRequest request) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new SchoolNotFoundException("학교를 찾을 수 없습니다. id=" + id));
        school.update(
                request.schoolName(),
                request.schoolType(),
                request.schoolCategory(),
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

    /**
     * LIKE 특수문자를 escape 한다(escape char = {@code \}). 쿼리는 {@code escape '\'} 를 사용하므로
     * {@code q="%"} 같은 입력이 전체 매칭으로 새지 않는다.
     */
    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
