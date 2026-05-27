보완 필요
1. “not null” 검증은 실제로 커버됐다고 보기 어렵다

문서의 Validation Matrix에서는 다음 항목을 커버했다고 적고 있다.

pageNo not null/duplicated
page sortOrder not null/duplicated
item sectionType not null
item sortOrder not null

그런데 실제 테스트를 보면 duplicate_page_number_or_sort_order_fails()는 중복만 검증하고, unsupported_or_disabled_section_fails()는 unsupported/disabled enum만 검증한다. getPageNo() == null, getSortOrder() == null, item.getSectionType() == null, item.getSortOrder() == null을 직접 만드는 테스트는 없다.

따라서 문서의 21/21 fully covered 표현은 현재 테스트 코드 기준으로는 과장이다. 둘 중 하나로 정리해야 한다.

@Test
void pageNo가_null이면_실패() { ... }

@Test
void page_sortOrder가_null이면_실패() { ... }

@Test
void item_sectionType이_null이면_실패() { ... }

@Test
void item_sortOrder가_null이면_실패() { ... }

또는 실제 Entity/DTO 구조상 null이 불가능하다면 문서에서 not null 항목을 제거하고 duplicated만 검증한다고 바꿔야 한다.

2. invalid saveLayout이 기존 레이아웃을 삭제하지 않는지 검증이 없다

saveLayout_비활성_섹션_배치시_검증_실패()는 예외 발생만 확인한다. 그런데 이 케이스에서 진짜 중요한 건 검증 실패 시 replace-all delete가 수행되지 않는 것이다. 지금 테스트는 구현이 실수로 deleteByJobPostingId()를 먼저 호출하고 이후 validator에서 실패해도 잡아내지 못한다.

아래 검증을 추가하는 게 맞다.

verify(applicationFormPageRepository, never()).deleteByJobPostingId(any());
verify(applicationFormPageRepository, never()).saveAll(anyList());

이건 비차단이지만, 저장 API의 데이터 손실 방어 관점에서는 꽤 중요한 테스트다.

3. question/attachment “placed” 검증이 문서보다 약하다

문서에는 question/attachment enabled 케이스에서 “Both placed in layout”이라고 되어 있다. 그런데 getLayout_질문_첨부_활성_시_섹션_포함()은 availableSections의 enabled/required/source만 확인하고, 실제 pages.items()에 QUESTION_ANSWER, ATTACHMENT가 배치됐는지는 직접 확인하지 않는다.

최소한 아래 정도는 추가하는 게 맞다.

assertThat(qa.placed()).isTrue();
assertThat(att.placed()).isTrue();

assertThat(response.pages().stream()
        .flatMap(p -> p.items().stream())
        .map(AdminApplicationFormLayoutResponse.ItemResponse::sectionType))
        .contains(ApplicationSectionType.QUESTION_ANSWER, ApplicationSectionType.ATTACHMENT);

특히 availableSections.enabled=true인데 default layout 생성이 누락되는 버그는 지금 테스트로는 완전히 막지 못한다.