Medium — dangling schoolId 처리

현재 schoolId가 School 테이블에 없으면 groupId는 존재하지만 groupName=null로 나올 수 있다. 문서에도 known limitation으로 남겨져 있다.

08c에서 schoolId 존재 검증을 하지 않기로 한 설계와는 일관되지만, 프론트 화면에서는 groupName=null이 깨질 수 있다. 후속에서 아래 중 하나로 정리하는 게 낫다.

1. dangling schoolId는 기타로 합산
2. groupName = "알 수 없는 학교"로 표시
3. 통계 전 health check로 dangling schoolId 진단

개인적으로는 dangling schoolId는 기타로 합산이 가장 안전하다.

Low — 같은 EducationLevel tie-break 테스트 추가 권장

구현에는 같은 educationLevel이면 schoolId가 있는 row를 우선하는 로직이 있다.

문서에도 이 정책이 적혀 있지만, 현재 테스트는 고졸+대학 케이스라 같은 레벨 tie-break를 직접 검증하지 않는다.

추가 테스트 추천:

UNIVERSITY 학력 2개:
- 하나는 schoolId=null
- 하나는 schoolId=Alpha
=> SCHOOL dimension에서 Alpha로 그룹핑되는지 검증
Low — topN clamp 테스트 추가 권장

topN은 null/0 이하이면 기본 10, 최대 100으로 clamp된다.
현재 topN=1 테스트는 있지만 topN=0, topN>100 경계 테스트는 없다. 필수는 아니지만 통계 API 안정성을 높이려면 추가할 만하다.