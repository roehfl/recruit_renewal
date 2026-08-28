package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.response.SchoolSearchResponse;
import com.shinyoung.recruit.enumeration.EducationLevel;
import com.shinyoung.recruit.enumeration.SchoolSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 학교 검색 라우팅/중복제거 단위 테스트. 외부 OpenAPI 클라이언트는 mock 으로 대체한다(네트워크 미사용).
 */
class SchoolSearchServiceTest {

    private final NeisSchoolClient neisSchoolClient = mock(NeisSchoolClient.class);
    private final UnivInfoSchoolClient univInfoSchoolClient = mock(UnivInfoSchoolClient.class);
    private final SchoolSearchService schoolSearchService =
            new SchoolSearchService(neisSchoolClient, univInfoSchoolClient);

    @Test
    void 검색어가_비면_외부호출_없이_빈목록() {
        assertThat(schoolSearchService.search("   ", EducationLevel.HIGH_SCHOOL)).isEmpty();

        verify(neisSchoolClient, never()).search(any(), any());
        verify(univInfoSchoolClient, never()).search(any(), any());
    }

    @Test
    void 고등학교는_NEIS로_라우팅() {
        when(neisSchoolClient.search("서울", "고등학교"))
                .thenReturn(List.of(neisSchool("7010001", "서울고등학교")));

        List<SchoolSearchResponse> result = schoolSearchService.search(" 서울 ", EducationLevel.HIGH_SCHOOL);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).schoolSource()).isEqualTo(SchoolSource.NEIS);
        verify(univInfoSchoolClient, never()).search(any(), any());
    }

    @Test
    void 대학_대학원은_학교구분값으로_라우팅() {
        when(univInfoSchoolClient.search(eq("한국"), any())).thenReturn(List.of());

        schoolSearchService.search("한국", EducationLevel.COLLEGE);
        schoolSearchService.search("한국", EducationLevel.UNIVERSITY);
        schoolSearchService.search("한국", EducationLevel.MASTER);
        schoolSearchService.search("한국", EducationLevel.DOCTOR);

        verify(univInfoSchoolClient).search("한국", "전문대학");
        verify(univInfoSchoolClient).search("한국", "대학");
        // 대학원도 별도 행으로 제공되므로 석사·박사는 같은 값을 쓴다(2회 호출).
        verify(univInfoSchoolClient, org.mockito.Mockito.times(2)).search("한국", "대학원");
        verify(neisSchoolClient, never()).search(any(), any());
    }

    @Test
    void 같은_학교코드는_한번만_남는다() {
        // 본교/분교 등으로 같은 코드가 중복돼 와도 한 번만 남아야 한다.
        when(univInfoSchoolClient.search(any(), any())).thenReturn(List.of(
                univSchool("U001", "한국대학교"),
                univSchool("U001", "한국대학교"),
                univSchool("U002", "한국공업대학교")));

        List<SchoolSearchResponse> result = schoolSearchService.search("한국", EducationLevel.UNIVERSITY);

        assertThat(result).extracting(SchoolSearchResponse::schoolCode)
                .containsExactly("U001", "U002");
    }

    private static SchoolSearchResponse neisSchool(String code, String name) {
        return new SchoolSearchResponse(code, name, SchoolSource.NEIS, "서울특별시");
    }

    private static SchoolSearchResponse univSchool(String code, String name) {
        return new SchoolSearchResponse(code, name, SchoolSource.UNIV_INFO, "서울특별시");
    }
}
