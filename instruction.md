Medium — overflow 기타와 top 그룹의 중복 허용 테스트 추가 권장

현재 구현상 어떤 지원자가 topN 자격과 overflow 자격을 모두 갖고 있으면, 그 지원자는 top 자격 그룹에도 들어가고 기타에도 들어간다. 현재 로직은 그렇게 동작한다.

이건 “그룹 중복 허용” 정의와 일관된다. 다만 테스트가 이 케이스를 직접 고정하지는 않는다. 추가하면 좋다.

app1: Common + Rare1
app2: Common
topN=1
=> Common p=2, 기타 p=1

이 테스트가 있으면 “기타도 overflow 자격 보유자 distinct이며, top 그룹과의 중복은 허용”이라는 정의가 더 단단해진다.

Medium — 자격명 정규화 한계는 후속 master에서 해결

현재 정규화는 trim + 공백 압축만 한다. "정보처리기사"와 "정보 처리 기사" 같은 의미적 동치나 대소문자/한영 표기 차이는 다른 그룹으로 남는다. 문서도 known limitation으로 명시하고 있다.

이건 지금 phase에서는 문제 아니다. 자격 master/표준화 phase를 따로 잡는 게 맞다.

Low — future enum 추가 시 allowlist 유지 권장

parseSupportedDimension()은 현재 enum 파싱만 하고 그대로 반환한다. 지금 enum은 POSITION/SCHOOL/CERTIFICATE뿐이라 문제 없다. 다만 나중에 enum 값이 추가되면 service dispatch에 빠져도 400이 아니라 빈 dimensions로 나갈 수 있다.

지금 당장 수정 필요는 없지만, 안전하게 하려면 switch expression으로 exhaustiveness를 강제하는 편이 낫다.
