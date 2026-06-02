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

/**
 * School xlsx 일괄 import(upsert)(Phase 08c). 행 단위로 적용하고(전체 거부 아님), 유효하지 않은 행만 skip 한다.
 *
 * <p>upsert 키: {@code schoolCode}(있으면) 우선, 없으면 {@code (schoolName, schoolType, region)} fallback.
 * 기존이면 서술 필드 update(활성 상태 보존), 신규면 insert. 결과는 insert/update/skip 카운트 + skip 사유로 반환한다.
 *
 * <p>행은 적용 전에 필수/길이를 검증해(엔티티 컬럼 길이와 일치) DB flush 시점의 {@code DataIntegrityViolationException}
 * 으로 import 전체가 rollback 되는 것을 막는다. natural key 매칭이 2건 이상이면 어느 행을 update 할지 모호하므로 skip 한다.
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
            List<String> rowErrors = validateRow(row);
            if (!rowErrors.isEmpty()) {
                errors.add(new SchoolImportRowError(row.rowNumber(), String.join(" ", rowErrors)));
                continue;
            }

            String schoolCode = blankToNull(row.schoolCode());
            String schoolName = blankToNull(row.schoolName());
            String schoolType = blankToNull(row.schoolType());
            String educationMode = blankToNull(row.educationMode());
            String region = blankToNull(row.region());
            String address = blankToNull(row.address());
            String countryCode = blankToNull(row.countryCode());

            ExistingMatch match = findExisting(schoolCode, schoolName, schoolType, region);
            if (match.ambiguous()) {
                errors.add(new SchoolImportRowError(row.rowNumber(), match.reason()));
                continue;
            }
            if (match.school() != null) {
                // active 는 보존(import 가 비활성화하지 않는다 → School.update 에 null 전달).
                match.school().update(schoolName, schoolType, educationMode, region, address, countryCode, null);
                updated++;
            } else {
                schoolRepository.save(School.create(
                        schoolCode, schoolName, schoolType, educationMode, region, address, countryCode, true));
                inserted++;
            }
        }

        return new SchoolImportResponse(rows.size(), inserted, updated, errors.size(), errors);
    }

    /** 필수/길이 검증(엔티티 컬럼 길이와 일치). 길이는 trim 후 기준. */
    private List<String> validateRow(SchoolImportRowRequest row) {
        List<String> errors = new ArrayList<>();
        if (blankToNull(row.schoolName()) == null) {
            errors.add("schoolName은(는) 필수입니다.");
        }
        validateMax(errors, "schoolCode", row.schoolCode(), 100);
        validateMax(errors, "schoolName", row.schoolName(), 200);
        validateMax(errors, "schoolType", row.schoolType(), 50);
        validateMax(errors, "educationMode", row.educationMode(), 50);
        validateMax(errors, "region", row.region(), 100);
        validateMax(errors, "address", row.address(), 500);
        validateMax(errors, "countryCode", row.countryCode(), 10);
        return errors;
    }

    private static void validateMax(List<String> errors, String field, String value, int max) {
        if (value != null && value.trim().length() > max) {
            errors.add(field + "은(는) " + max + "자 이하여야 합니다.");
        }
    }

    private ExistingMatch findExisting(String schoolCode, String schoolName, String schoolType, String region) {
        if (schoolCode != null) {
            return schoolRepository.findBySchoolCode(schoolCode)
                    .map(ExistingMatch::matched)
                    .orElseGet(ExistingMatch::none);
        }
        List<School> matches = schoolRepository.findByNaturalKey(schoolName, schoolType, region);
        if (matches.size() > 1) {
            return ExistingMatch.ambiguous(
                    "natural key 중복(schoolName/schoolType/region) " + matches.size() + "건이라 upsert 대상이 모호합니다.");
        }
        return matches.stream().findFirst()
                .map(ExistingMatch::matched)
                .orElseGet(ExistingMatch::none);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** upsert 대상 매칭 결과: 매칭/없음/모호(중복) 구분. */
    private record ExistingMatch(School school, boolean ambiguous, String reason) {
        static ExistingMatch matched(School school) {
            return new ExistingMatch(school, false, null);
        }

        static ExistingMatch none() {
            return new ExistingMatch(null, false, null);
        }

        static ExistingMatch ambiguous(String reason) {
            return new ExistingMatch(null, true, reason);
        }
    }
}
