package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.School;
import com.shinyoung.recruit.domain.repository.SchoolRepository;
import com.shinyoung.recruit.dto.request.SchoolImportRowRequest;
import com.shinyoung.recruit.dto.response.SchoolImportResponse;
import com.shinyoung.recruit.dto.response.SchoolImportRowError;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * School xlsx 일괄 import(upsert)(Phase 08c). 행 단위로 적용하고(전체 거부 아님), 유효하지 않은 행만 skip 한다.
 *
 * <p>upsert 키: {@code schoolCode}(있으면) 우선, 없으면 {@code (schoolName, schoolType, region)} fallback.
 * 기존이면 서술 필드 update(활성 상태 보존), 신규면 insert. 결과는 insert/update/skip 카운트 + skip 사유로 반환한다.
 */
@Service
@RequiredArgsConstructor
public class SchoolImportService {

    private final SchoolImportParser parser;
    private final SchoolRepository schoolRepository;

    @Transactional
    public SchoolImportResponse importSchools(MultipartFile file) {
        List<SchoolImportRowRequest> rows = parser.parse(file);

        int inserted = 0;
        int updated = 0;
        List<SchoolImportRowError> errors = new ArrayList<>();

        for (SchoolImportRowRequest row : rows) {
            if (row.formulaCellPresent()) {
                errors.add(new SchoolImportRowError(row.rowNumber(), "수식(formula) 셀은 허용되지 않습니다."));
                continue;
            }
            String schoolName = blankToNull(row.schoolName());
            if (schoolName == null) {
                errors.add(new SchoolImportRowError(row.rowNumber(), "schoolName은(는) 필수입니다."));
                continue;
            }

            String schoolCode = blankToNull(row.schoolCode());
            String schoolType = blankToNull(row.schoolType());
            String educationMode = blankToNull(row.educationMode());
            String region = blankToNull(row.region());
            String address = blankToNull(row.address());
            String countryCode = blankToNull(row.countryCode());

            Optional<School> existing = findExisting(schoolCode, schoolName, schoolType, region);
            if (existing.isPresent()) {
                // active 는 보존(import 가 비활성화하지 않는다 → School.update 에 null 전달).
                existing.get().update(schoolName, schoolType, educationMode, region, address, countryCode, null);
                updated++;
            } else {
                schoolRepository.save(School.create(
                        schoolCode, schoolName, schoolType, educationMode, region, address, countryCode, true));
                inserted++;
            }
        }

        return new SchoolImportResponse(rows.size(), inserted, updated, errors.size(), errors);
    }

    private Optional<School> findExisting(String schoolCode, String schoolName, String schoolType, String region) {
        if (schoolCode != null) {
            return schoolRepository.findBySchoolCode(schoolCode);
        }
        return schoolRepository.findByNaturalKey(schoolName, schoolType, region).stream().findFirst();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
