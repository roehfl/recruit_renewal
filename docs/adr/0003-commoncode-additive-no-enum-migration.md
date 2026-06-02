# CommonCode는 추가형 lookup master로 도입하고, 기존 enum을 전환하지 않는다

Phase 08의 `CommonCode`(groupCode/code/displayName/sortOrder/active)는 관리자가 런타임에 관리하는 코드성 lookup master로, **프론트 드롭다운 소스**로만 쓰고 백엔드 도메인 필드 validation 에 결합하지 않는다. 기존 enum(`JobApplicationStatus`, `StageResultStatus`, `EducationLevel`, `EmploymentType` 등)은 **하나도 CommonCode로 전환하지 않는다** — 전환은 컴파일 타임 안전성과 분기 보증을 잃고 entity/DTO/validation/test 에 광범위한 변경을 만들기 때문에, "관리자가 런타임에 값을 추가해야 한다"는 구체 요구가 생긴 group 에 한해서만 나중에 진행한다.

## Status

accepted (2026-06-02, Phase 08 design / grill-with-docs Q1·Q2).

## Considered Options

- **일부 admin-editable enum 전환** — `EmploymentType`/`JobPositionApplicationType`/병역 분류 등 표시 전용 enum 을 CommonCode 로 교체. 거부: 현재 "런타임 값 추가" 요구가 없고 blast radius(entity/DTO/test) 와 타입 안전성 상실이 정당화되지 않음.
- **추가형 + 카탈로그만** — 채택. CommonCode 는 신규 lookup 으로만 두고, 전환 후보는 STAY/CANDIDATE 로 카탈로그(설계 §7)해 미래 판단에 남긴다.

## Consequences

- `CommonCode` 와 enum 이 공존한다. 같은 값을 둘 다로 두지 않는다(중복 진실원 회피).
- 전환이 필요해지면 group 단위로 별도 작업하며, 그 자체가 명시적 결정(요구 + blast radius 검토)을 동반한다.
- CANDIDATE 분류(`MilitaryBranch/Rank/ServiceType`, `EmploymentType`, `JobPositionApplicationType`, `DayNightType`, `CampusType`)는 전환 예약이 아니라 후보 목록일 뿐이다.
