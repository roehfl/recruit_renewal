# Phase 05e - Layout Stabilization / Test Hardening

## Phase Summary

- Phase name: Phase 05e - Layout Stabilization / Test Hardening
- Date: 2026-05-27
- Purpose: harden the layout validation matrix, cover fallback edge cases, verify attachment/question required policy regression, and add `validateLayoutForPublish()` unit tests.
- Status: completed
- Scope type: test hardening, no production code changes, documentation.

Phase 05e closes the Phase 05 layout feature by hardening the test coverage. No production source code was modified. Only test files and documentation were changed.

## Purpose

- Fill test gaps identified in the validation matrix (boundary values, negative cases).
- Add unit tests for `validateLayoutForPublish()` (previously only tested via integration in `JobPostingServiceTest`).
- Verify attachment/question required policy does not drift from the layout system.
- Add edge case tests for the default layout factory and section policy.

## Scope

### Implemented

- **ApplicationFormLayoutValidatorTest**: +12 tests
  - `pageNo가_null이면_실패` - null pageNo rejection
  - `page_sortOrder가_null이면_실패` - null page sortOrder rejection
  - `item_sectionType이_null이면_실패` - null sectionType rejection
  - `item_sortOrder가_null이면_실패` - null item sortOrder rejection
  - `pageNo가_0이면_실패` - pageNo must be positive
  - `page_sortOrder가_음수이면_실패` - page sortOrder must be >= 0
  - `item_sortOrder가_음수이면_실패` - item sortOrder must be >= 0
  - `page_title이_100자_초과시_실패` - title max length enforcement
  - `page_title이_blank이면_실패` - blank title rejection
  - `page_description이_500자_초과시_실패` - description max length enforcement
  - `ETC_enum은_레이아웃에서_거부` - ETC enum not allowed in layout
  - `추가_배치된_섹션이_있으면_실패` - extra placed section (disabled) detected

- **ApplicationFormLayoutServiceTest**: +8 tests + 기존 테스트 보강
  - `validateLayoutForPublish_유효한_폴백이면_통과` - fallback passes
  - `validateLayoutForPublish_formConfig_없으면_실패` - null config blocked
  - `validateLayoutForPublish_저장된_레이아웃이_유효하면_통과` - valid stored passes
  - `validateLayoutForPublish_저장된_레이아웃이_부실하면_실패` - stale stored blocked
  - `validateLayoutForPublish_질문_활성인데_레이아웃에_없으면_실패` - QUESTION_ANSWER missing detected
  - `validateLayoutForPublish_첨부_활성인데_레이아웃에_없으면_실패` - ATTACHMENT missing detected
  - `getLayout_질문_활성_필수아닌_경우_required_false로_표시` - policy regression: enabled but not required
  - `getLayout_첨부_활성_필수아닌_경우_required_false로_표시` - policy regression: enabled but not required
  - 기존 `saveLayout_비활성_섹션_배치시_검증_실패`에 `verify(never()).deleteByJobPostingId()` / `saveAll()` 추가 (데이터 손실 방어)
  - 기존 `getLayout_질문_첨부_활성_시_섹션_포함`에 `placed()` 및 실제 items 배치 검증 추가

- **ApplicationFormLayoutDefaultFactoryTest**: +4 tests
  - `jobPosting_null이면_IllegalArgumentException` - null guard
  - `effectiveEnabledSections_null이면_IllegalArgumentException` - null guard
  - `effectiveEnabledSections_빈_집합이면_IllegalArgumentException` - empty guard
  - `부분_그룹만_활성이면_페이지_번호가_연속된다` - sparse section page renumbering

- **ApplicationFormLayoutSectionPolicyTest**: +3 tests
  - `config_null이고_질문_첨부_활성이면_BASIC_INFO와_외부_섹션만` - null config + externals
  - `모든_config_false이고_외부_정책_없으면_BASIC_INFO만` - minimal config
  - `질문_활성이지만_필수_아닌_경우_enabled에만_포함` - enabled != required separation

### Out-of-Scope Items

- Production source code changes.
- New API endpoints.
- DB schema changes.
- Frontend/Vue/static resources.
- Full-suite test execution.

## Changed Files

### New Files

| File | Purpose |
| --- | --- |
| `docs/codex/implementation/phase-05e-layout-stabilization-test-hardening.md` | Implementation document. |
| `docs/codex/reports/phase-05e-layout-stabilization-test-hardening.html` | Human-readable status report. |

### Modified Files

| File | Change | Purpose |
| --- | --- | --- |
| `src/test/.../ApplicationFormLayoutValidatorTest.java` | +8 tests | Boundary/negative value validation matrix hardening. |
| `src/test/.../ApplicationFormLayoutServiceTest.java` | +8 tests | `validateLayoutForPublish()` unit tests and policy regression. |
| `src/test/.../ApplicationFormLayoutDefaultFactoryTest.java` | +4 tests | Edge case coverage (null, empty, sparse). |
| `src/test/.../ApplicationFormLayoutSectionPolicyTest.java` | +3 tests | Null config and policy separation edge cases. |
| `docs/codex/07-implementation-history.md` | Added Phase 05e entry | History update. |

## Test Coverage Summary

### Before Phase 05e

| Test Class | Count |
| --- | --- |
| ApplicationFormLayoutValidatorTest | 10 |
| ApplicationFormLayoutServiceTest | 18 |
| ApplicationFormLayoutDefaultFactoryTest | 3 |
| ApplicationFormLayoutSectionPolicyTest | 3 |
| AdminApplicationFormLayoutControllerTest | 11 |
| **Total** | **45** |

### After Phase 05e

| Test Class | Count | Added |
| --- | --- | --- |
| ApplicationFormLayoutValidatorTest | 22 | +12 |
| ApplicationFormLayoutServiceTest | 26 | +8 (+ 기존 2개 보강) |
| ApplicationFormLayoutDefaultFactoryTest | 7 | +4 |
| ApplicationFormLayoutSectionPolicyTest | 6 | +3 |
| AdminApplicationFormLayoutControllerTest | 11 | - |
| **Total** | **72** | **+27** |

### JobPostingServiceTest (publish guard)

| Test Class | Count |
| --- | --- |
| JobPostingServiceTest | 27 |

### Test Commands

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "*ApplicationFormLayout*" --tests "*JobPostingServiceTest" --no-daemon
```

Result: BUILD SUCCESSFUL - 99 tests, 0 failures.

## Validation Matrix Coverage

| Rule | Description | Test |
| --- | --- | --- |
| 1 | At least one page | `pages_null_or_empty_fails` |
| 2 | Each page has at least one item | `page_without_items_fails` |
| 3a | pageNo not null | `pageNo가_null이면_실패` |
| 3b | pageNo not duplicated | `duplicate_page_number_or_sort_order_fails` |
| 4 | pageNo > 0 | `pageNo가_0이면_실패` |
| 5a | page sortOrder not null | `page_sortOrder가_null이면_실패` |
| 5b | page sortOrder not duplicated | `duplicate_page_number_or_sort_order_fails` |
| 6 | page sortOrder >= 0 | `page_sortOrder가_음수이면_실패` |
| 7 | page title nonblank | `page_title이_blank이면_실패` |
| 8 | page title max 100 | `page_title이_100자_초과시_실패` |
| 9 | page description max 500 | `page_description이_500자_초과시_실패` |
| 10a | item sectionType not null | `item_sectionType이_null이면_실패` |
| 10b | item sectionType in layout subset | `unsupported_or_disabled_section_fails`, `ETC_enum은_레이아웃에서_거부` |
| 11a | item sortOrder not null | `item_sortOrder가_null이면_실패` |
| 11b | item sortOrder >= 0 | `item_sortOrder가_음수이면_실패` |
| 12 | item sortOrder unique within page | `duplicate_item_sort_order_or_section_type_fails` |
| 13 | section type unique across layout | `duplicate_item_sort_order_or_section_type_fails` |
| 14 | disabled section not placed | `unsupported_or_disabled_section_fails`, `추가_배치된_섹션이_있으면_실패` |
| 15 | enabled section must be placed | `enabled_or_required_missing_fails` |
| 16 | required section must be placed | `enabled_or_required_missing_fails` |
| 17 | BASIC_INFO in enabled | `basic_info_missing_from_enabled_sections_fails` |
| 18 | BASIC_INFO in required | `basic_info_missing_from_required_sections_fails` |
| 19 | required subset of enabled | `required_sections_must_be_enabled_subset` |
| 20 | save validation failure does not delete | `saveLayout_비활성_섹션_배치시_검증_실패` (verify never) |

## Attachment/Question Required Policy Regression

| Scenario | Test | Expected |
| --- | --- | --- |
| Question enabled, not required | `getLayout_질문_활성_필수아닌_경우_required_false로_표시` | QUESTION_ANSWER: enabled=true, required=false |
| Attachment enabled, not required | `getLayout_첨부_활성_필수아닌_경우_required_false로_표시` | ATTACHMENT: enabled=true, required=false |
| Question enabled, layout missing | `validateLayoutForPublish_질문_활성인데_레이아웃에_없으면_실패` | Publish blocked |
| Attachment enabled, layout missing | `validateLayoutForPublish_첨부_활성인데_레이아웃에_없으면_실패` | Publish blocked |
| Question/attachment both enabled | `getLayout_질문_첨부_활성_시_섹션_포함` (existing) | Both placed in layout |

## Remaining Issues

- No performance test for large layouts (100+ pages).
- Concurrent save/publish race condition not tested (requires multi-thread test infrastructure).
- Applicant layout read service (`ApplicationFormPageService`) not hardened in this phase.
- Full `clean test` not executed (local timeout concerns).

## Next Phase Recommendation

Phase 05 (Application Form Page Layout) is now complete. All slices 05a through 05e are implemented and tested.

Recommended next direction:
- Phase 06 - Interview Evaluation (interviewer evaluation write/save/submit, admin evaluation read, StageResult reflection)
- Or backlog items based on project priority.
