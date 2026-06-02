1. High — stageResultUpdatedAt 동시성 토큰이 lost update를 완전히 막지 못한다

설계는 commit 직전 검증에서 stageResultUpdatedAt을 비교해 lost update를 막는다고 정의한다.
그런데 실제 구현은 DB 조건부 update가 아니라, 메모리에서 currentToken.equals(rowToken)만 비교한다.

문제는 그 다음 실제 변경이 bulkUpdateResults()로 넘어가면서 단순히 stageId + id in으로 다시 조회한 뒤 엔티티 필드만 변경한다는 점이다. updatedAt 조건이 WHERE에 들어가지 않고, update count 검증도 없고, @Version도 없다.

즉 이런 레이스가 가능하다.

관리자 A, B가 같은 템플릿을 받음.
A commit, B commit이 거의 동시에 들어옴.
둘 다 같은 구버전 token을 읽고 stale check 통과.
A가 먼저 update.
B가 나중에 update하면서 A 변경을 덮어씀.

이건 Phase 7d의 핵심 보장인 “낙관적 동시성”이 깨진다.

수정 방향은 둘 중 하나다.

1안: StageResult에 @Version 컬럼을 추가하고 OptimisticLockException을 409로 매핑한다.
2안: migration 없이 가려면 commit 시 변경 대상 StageResult를 PESSIMISTIC_WRITE로 잠근 뒤, lock 획득 후 DB 최신 updatedAt 기준으로 다시 token 비교한다.

단순히 지금처럼 조회 후 비교만 하는 방식은 부족하다.

2. High — upload-template이 기존 comment 값을 변형해서 no-op 업로드가 데이터 변경으로 오판될 수 있다

upload-template은 현재 StageResult.comment를 그대로 row 값으로 넣는다.
그런데 실제 xlsx writer는 모든 문자열에 대해 formula injection 방어를 하면서 =, +, -, @, 탭, 줄바꿈으로 시작하는 값 앞에 apostrophe를 붙인다.

이건 일반 export에서는 괜찮다. 하지만 upload-template은 round-trip source라서 문제가 된다.

예를 들어 기존 comment가 아래처럼 저장되어 있다고 하자.

- 보류 사유 확인 필요

템플릿 다운로드 시 writer가 이 값을 아래처럼 바꿀 수 있다.

'- 보류 사유 확인 필요

그 파일을 그대로 다시 업로드하면 서비스는 현재 DB comment와 업로드 comment를 직접 비교한다.
결과적으로 사용자가 아무것도 바꾸지 않았는데도 CHANGED로 판정되고, commit 시 comment 앞에 '가 붙은 값으로 오염될 수 있다.

이건 실제 운영에서 충분히 터진다. comment가 -, @, +, 줄바꿈으로 시작하는 케이스는 흔하다.

수정 방향:

upload-template에는 일반 ExcelExportWriter를 그대로 쓰지 말 것.
round-trip용 writer를 분리하거나, parser에서 export sanitizer가 붙인 leading apostrophe를 안전하게 역정규화해야 한다.
단, 사용자가 실제로 apostrophe로 시작하는 값을 입력한 경우와 구분해야 하므로 단순 replace는 위험하다.
가장 안전한 방향은 upload-template writer를 별도로 두고, xlsx string cell만 사용하며 값을 변형하지 않는 것이다.
3. Medium — 변경 행이 0건이면 Stage IN_PROGRESS guard와 actor 검증을 우회한다

설계는 commit 선행 검증에 Stage IN_PROGRESS guard와 actor 필수 검증이 포함된다고 되어 있다.
하지만 현재 upload commit은 CHANGED 행만 bulkUpdateResults()로 위임하고, 변경 행이 없으면 아예 호출하지 않는다.

문제는 StageResultService의 actor/stage guard가 bulkUpdateResults() 내부에 있다는 점이다.
따라서 전부 UNCHANGED인 파일이나 header-only 파일은 stage가 CLOSED여도 APPLIED로 떨어질 수 있다.

데이터 변경은 없으니 치명적 오염은 아니지만, commit API 의미가 설계와 다르고 audit에도 성공처럼 남을 수 있다. 수정하려면 upload service에서 bulkUpdateResults() 호출 여부와 무관하게 actor/stage editable 검증을 먼저 수행해야 한다.

4. Low — token cell “문자열 강제”가 문서보다 느슨하다

설계는 stageResultUpdatedAt이 Excel string cell이어야 하고, date/numeric/formula이면 row error라고 정의한다.
그런데 parser는 token column이 NUMERIC일 때만 tokenNotString = true로 처리한다.

BOOLEAN, BLANK, 기타 타입은 명시적 “non-string token” 오류가 아니다. 특히 unchanged row는 stale check 대상에서도 제외되므로, token cell 타입 검증이 흐려진다. 설계대로 가려면 token column은 STRING 또는 blank 정책을 명확히 하고, 그 외 타입은 전부 row error로 처리하는 게 맞다.

좋은 점