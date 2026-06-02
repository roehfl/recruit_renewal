Medium — LIKE wildcard escape 없음

문서에도 known limitation으로 적혀 있듯이 검색 q의 %, _ 같은 LIKE 특수문자를 escape하지 않는다.

현재는 public 검색이 top-N 20이라 치명적이지는 않다. 다만 q=%가 사실상 전체 active 학교 top 20 조회가 될 수 있으므로, 자동완성 API 품질을 더 엄격히 보려면 08c나 08f에서 escape 처리하는 게 좋다.

권장 수정:

private static String escapeLike(String value) {
    return value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
}

그리고 JPQL에 escape '\\'를 붙이는 방식으로 정리하면 된다.

Low — admin page size 상한 없음

admin 목록은 page/size를 그대로 PageRequest.of(page, size, ...)에 넣는다.

admin endpoint라 blocking은 아니지만, size=100000 같은 요청을 막으려면 controller/service에서 max size를 둬라. School master는 수천 건 정도라 당장은 큰 문제는 아니다.

Low — schoolCode 불변 extra-field 테스트 없음

SchoolUpdateRequest에는 schoolCode가 없기 때문에 구조상 불변이다. 그래도 8a에서 CommonCode에 추가했던 것처럼, update body에 schoolCode를 넣어도 기존 값이 유지되는 회귀 테스트를 추가하면 더 좋다. 현재 테스트는 update 후 schoolCode 유지 정도만 확인한다.