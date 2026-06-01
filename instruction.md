Medium 1 — POSITION dimension 정렬/라벨 기준이 애매함

현재 POSITION dimension은 다음처럼 grouping합니다.

Map<Long, List<FunnelCohortRow>> byPosition = cohort.stream()
        .collect(Collectors.groupingBy(FunnelCohortRow::jobPositionId, LinkedHashMap::new, Collectors.toList()));

return byPosition.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())

그리고 groupName은 group.get(0).jobPositionName()입니다. 그런데 FunnelCohortRow의 jobPositionName은 현재 application.jobPositionNameSnapshot입니다.

문제는 두 가지입니다.

1. 정렬이 JobPosition.sortOrder가 아니라 jobPositionId 기준이다.
2. 그룹 기준은 FK인데, 표시명은 application snapshot의 첫 번째 값이다.

현실적으로 대부분 문제 없겠지만, 운영 화면의 “분야별 통계”라면 보통 공고에 등록된 모집분야 순서(sortOrder)대로 보여주는 게 맞습니다.

권고:

FunnelCohortRow에 jobPositionSortOrder와 현재 jobPosition.positionName을 추가하는 편이 낫습니다.

select new FunnelCohortRow(
    application.id,
    application.status,
    application.jobPosition.id,
    application.jobPosition.positionName,
    application.jobPosition.sortOrder
)

그리고 dimension 그룹은 다음 기준으로 정렬하세요.

.sorted(
    Comparator
        .comparing((PositionGroup group) -> group.sortOrder())
        .thenComparing(PositionGroup::groupId)
)

만약 snapshot 표시명을 의도적으로 쓰려는 거라면 문서에 명확히 남겨야 합니다.

POSITION dimension의 groupName은 현재 JobPosition명이 아니라 지원 당시 snapshot명을 사용한다.

내 판단은 현재 JobPosition명 + sortOrder 정렬이 더 맞습니다.

Medium 2 — “raw passed와 sequential passed가 달라지는 케이스” 테스트가 없음

07c 설계의 핵심은 이겁니다.

raw distribution.passed != funnelPassedCount 일 수 있다.
비율은 raw passed가 아니라 funnelPassedCount 기준이다.

그런데 현재 테스트는 stage2에서 distribution.passed == funnelPassedCount == 1인 케이스만 검증합니다.

반드시 아래 케이스를 추가해야 합니다.

P = app1, app2, app3

stage1:
- app1 PASSED
- app2 FAILED
- app3 NO_RESULT

stage2:
- app1 PASSED
- app2 PASSED  // 비정상/보정 데이터: 이전 단계 FAILED인데 후속 PASSED
- app3 PASSED  // 이전 단계 NO_RESULT인데 후속 PASSED

기대값:
stage2.distribution.passed = 3
stage2.funnelPassedCount = 1
stage2.stepConversionRate = 1 / 1 = 1.0
stage2.cumulativeRate = 1 / 3

이 테스트가 없으면 07c의 가장 중요한 설계 보정이 회귀로 깨져도 못 잡습니다.

Medium 3 — PENDING / ABSENT / HOLD bucket 테스트가 빠져 있음

구현은 switch로 모든 StageResultStatus를 처리하고 있어서 코드 자체는 맞습니다.

case PASSED -> passed++;
case FAILED -> failed++;
case ABSENT -> absent++;
case HOLD -> hold++;
case PENDING -> pending++;
case WITHDRAWN -> withdrawn++;

하지만 현재 테스트는 PASSED, FAILED, WITHDRAWN, NO_RESULT만 검증합니다.

07c의 7-bucket 정의를 고정하려면 아래 값도 테스트에 포함하세요.

stage1:
- app1 PENDING
- app2 ABSENT
- app3 HOLD
- app4 NO_RESULT

기대값:
pending = 1
absent = 1
hold = 1
noResult = 1
sum = |P|

특히 PENDING과 NO_RESULT 구분은 설계상 중요하므로 반드시 테스트로 고정해야 합니다.

Medium 4 — population.withdrawnCount와 distribution.withdrawn 분리 테스트가 약함

현재 테스트에서는 withdrawn application이 stage result도 WITHDRAWN입니다.

application status = WITHDRAWN
stage result = WITHDRAWN

이러면 population.withdrawnCount와 distribution.withdrawn이 우연히 같은 값이 됩니다.
하지만 설계상 둘은 다릅니다.

추가해야 할 케이스:

app1: application WITHDRAWN, stage result PASSED
app2: application SUBMITTED, stage result WITHDRAWN

기대값:
population.withdrawnCount = 1
distribution.withdrawn = 1
distribution.passed = 1

또는 더 명확히:

app1: application WITHDRAWN, stage result 없음

기대값:
population.withdrawnCount = 1
distribution.noResult = 1
distribution.withdrawn = 0

이게 있어야 “application-level withdrawn”과 “stage result withdrawn”을 덮어쓰지 않는다는 보장이 생깁니다.

Medium 5 — DRAFT 제외 테스트가 없음

모집단 P는 submittedAt != null입니다. 현재 repository query도 그렇게 되어 있습니다.

where application.jobPosting.id = :jobPostingId
  and application.submittedAt is not null

하지만 테스트에는 DRAFT 지원서가 없습니다. P 정의를 고정하려면 DRAFT를 하나 넣고 population.p와 NO_RESULT에 포함되지 않는지 확인해야 합니다.

app1 SUBMITTED
app2 WITHDRAWN, submittedAt 있음
app3 DRAFT, submittedAt 없음

기대값:
population.p = 2
stage distribution sum = 2

이건 07c에서 가장 기본적인 회귀 테스트입니다.

Low — topN 파라미터는 받지만 07c에서는 완전히 미사용

현재 controller/service 모두 topN을 받지만 POSITION에는 적용하지 않습니다. 문서에 “free-text 축 대비 파라미터”라고 적혀 있으므로 기능 오류는 아닙니다.

다만 API 사용자 입장에서는 dimension=POSITION&topN=1이 무시되는 게 어색할 수 있습니다.

둘 중 하나로 정리하세요.

안 1. 07c에서는 topN 제거
- SCHOOL/CERTIFICATE 활성화 시 다시 추가

안 2. 유지하되 문서/API 주석에 명확히 표시
- topN은 POSITION에서는 무시된다.
- SCHOOL/CERTIFICATE 활성화 전까지 동작하지 않는다.

현재 구현을 유지하려면 안 2로 충분합니다.

Low — POSITION dimension에서 0명인 모집분야는 응답에 안 나옴

현재 dimension은 P 코호트에 존재하는 jobPositionId만 group으로 생성합니다. 그래서 제출자가 0명인 모집분야는 dimensions에 없습니다.

이게 수학적으로는 “P를 그룹별로 나눈다”는 정의와 맞습니다.
다만 운영 화면에서는 “프론트엔드 분야 지원자 0명”도 보여주는 게 더 유용할 수 있습니다.

정책을 정하세요.

정책 A — P에 존재하는 group만 응답
현재 구현 유지. 문서에 명시.

정책 B — 공고의 모든 JobPosition을 응답
지원자 0명인 group도 population.p=0, stages 각 분포 0으로 응답.

운영 통계 화면이면 정책 B가 더 친절합니다. 단, 구현 복잡도는 조금 늘어납니다.