Blocking 1 — import row 길이 검증이 없어 DB 예외로 전체 import가 터질 수 있음

8c 문서는 import를 “행 단위 적용, 유효하지 않은 행만 skip”이라고 정의한다.

그런데 실제 SchoolImportService가 skip하는 건 formulaCellPresent와 blank schoolName뿐이다. 그 외 값은 길이 검증 없이 바로 School.create() 후 save()한다.

문제는 School 엔티티에는 컬럼 길이 제한이 있다.

schoolCode length = 100
schoolName length = 200
schoolType length = 50
educationMode length = 50
region length = 100
address length = 500
countryCode length = 10

Excel import는 admin DTO validation을 거치지 않는다. 그래서 schoolName 500자, countryCode 100자 같은 행이 들어오면 DB flush/commit 시점에 DataIntegrityViolationException이 날 수 있고, 현재 import service에는 catch가 없다. 그러면 “잘못된 행만 skip”이 아니라 전체 import rollback + 500/예외성 응답이 될 수 있다.

수정 필요:

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

그리고 에러가 있으면 해당 row만 skip해야 한다.

Blocking 2 — natural key 중복이 있으면 import가 임의의 첫 row를 update함

설계상 schoolCode가 없으면 (schoolName, schoolType, region) fallback으로 upsert한다.

repository는 natural key 조회 결과를 List<School>로 반환하고, service는 그중 첫 번째만 선택한다.

그런데 08b에서 schoolCode null인 학교는 여러 개 허용했다. 즉 관리자가 같은 schoolName/type/region을 중복 생성한 상태가 생길 수 있다. 그런 상태에서 import row에 schoolCode가 없으면 어느 학교를 update해야 하는지 모호한데, 현재는 id가 가장 작은 row를 조용히 update한다.

이건 master 데이터 오염 가능성이 있다. 최소한 중복 natural key가 2건 이상이면 skip해야 한다.

수정 방향:

private ExistingMatch findExisting(...) {
    if (schoolCode != null) {
        return schoolRepository.findBySchoolCode(schoolCode)
            .map(ExistingMatch::matched)
            .orElse(ExistingMatch.none());
    }

    List<School> matches = schoolRepository.findByNaturalKey(schoolName, schoolType, region);
    if (matches.size() > 1) {
        return ExistingMatch.ambiguous("natural key 중복: schoolName/schoolType/region");
    }
    return matches.stream().findFirst()
        .map(ExistingMatch::matched)
        .orElse(ExistingMatch.none());
}

ambiguous면 해당 row만 skip하고 errors에 남겨라.

Medium — import 핵심 방어 테스트가 부족함

현재 테스트는 blank schoolName skip, 확장자, header 정도만 본다.

그런데 문서상 file defense에는 formula cell, 크기/행수 한도도 포함된다.
SchoolImportParser에는 formula flag, maxRows, maxFileSize 로직이 실제로 있다.

추가하면 좋은 테스트:

- formula cell row → skipped + reason
- maxRows 초과 → 400
- maxFileSize 초과 → 400
- field length 초과 → 해당 row skipped
- natural key duplicate ambiguity → 해당 row skipped
Medium — ApplicationEducation.schoolId 인덱스가 없음

schoolId는 이후 SCHOOL funnel dimension의 기반이다. 그런데 ApplicationEducation 테이블 인덱스는 job_application_id, job_application_id,sort_order뿐이고 school_id 인덱스가 없다.

지금 당장 통계 구현은 08c 범위 밖이라 blocking은 아니지만, schoolId를 추가하는 김에 아래 인덱스를 넣는 게 낫다.

@Index(name = "idx_application_education_school", columnList = "school_id")