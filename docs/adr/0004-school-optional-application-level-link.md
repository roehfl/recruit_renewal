# School은 ApplicationEducation에 optional·application-level로만 연결한다(강한 FK 없음)

Phase 08의 `School` master 는 지원자 학력 입력의 자동완성/검색과 학교별 통계 grouping 의 기준이다. `ApplicationEducation` 은 기존 자유입력 `schoolName`(snapshot)을 **그대로 유지**하고, 지원자가 자동완성에서 고른 경우에만 nullable `schoolId`(application-level 참조, **DB FK 제약 없음**)를 채운다. 직접입력(미매칭)은 `schoolId == null` 이다. 강한 FK 를 두지 않는 이유: 해외/미등록 학교와 과거 free-text 데이터를 깨지 않으면서(비파괴) 매칭된 건에 한해 정확한 통계 grouping 을 확보하기 위해서다.

## Status

accepted (2026-06-02, Phase 08 design / grill-with-docs Q6·Q8).

## Considered Options

- **강한 FK `schoolId NOT NULL`** — 거부: 기존 free-text 학력과 master 미등록 학교를 모두 깨고, 지원 시점에 master 강제가 비현실적.
- **미연결(독립 검색 master)** — 거부: 자동완성만 제공하고 학교별 통계(07c 보류)를 끝내 못 푼다.
- **optional nullable schoolId(app-level)** — 채택. 비파괴 + 매칭 건 통계 동시 확보.

## Consequences

- 학교별 통계는 `schoolId` 매칭 건만 정확하고, 미매칭(free-text)은 '기타' 버킷이다. 과거 데이터는 소급 매칭하지 않는다.
- `School` 식별은 외부 `schoolCode`(있으면 unique) 우선, 없으면 `(schoolName, schoolType, region)` fallback 으로 re-import 멱등을 보장한다.
- `ApplicationEducation` 은 필드 추가(비파괴)이며 기존 쓰기/조회 계약을 바꾸지 않는다.
