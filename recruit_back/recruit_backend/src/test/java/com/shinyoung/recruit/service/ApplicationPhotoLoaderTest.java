package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.response.AdminAttachmentResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.exception.JobApplicationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 사진 선별/변환 규칙 회귀(S2). 화면(Application.vue)과 같은 규칙을 쓰는지 고정한다. */
class ApplicationPhotoLoaderTest {

    private static final Long APPLICATION_ID = 10L;

    private ApplicationAttachmentDownloadService downloadService;
    private ApplicationPhotoLoader loader;

    @BeforeEach
    void setUp() {
        downloadService = mock(ApplicationAttachmentDownloadService.class);
        loader = new ApplicationPhotoLoader(downloadService);
    }

    @Test
    void BASIC_INFO_섹션의_ETC_첨부_중_마지막_것을_사진으로_쓴다() {
        givenStoredFile(new byte[]{1, 2, 3});

        String dataUri = loader.loadPhotoDataUri(APPLICATION_ID, List.of(
                photo(1L, "image/jpeg"),
                photo(2L, "image/jpeg")));

        assertThat(dataUri).startsWith("data:image/jpeg;base64,");
        verify(downloadService).downloadForAdmin(APPLICATION_ID, 2L);
    }

    @Test
    void 다른_섹션이나_다른_타입의_첨부는_사진으로_쓰지_않는다() {
        String dataUri = loader.loadPhotoDataUri(APPLICATION_ID, List.of(
                new AdminAttachmentResponse(1L, AttachmentType.CAREER_DESCRIPTION,
                        ApplicationSectionType.BASIC_INFO, null, "career.pdf", "application/pdf", 100L, 0),
                new AdminAttachmentResponse(2L, AttachmentType.ETC,
                        ApplicationSectionType.CAREER, null, "etc.jpg", "image/jpeg", 100L, 1)));

        assertThat(dataUri).isNull();
        verify(downloadService, never()).downloadForAdmin(anyLong(), anyLong());
    }

    @Test
    void 첨부가_없으면_null을_돌려준다() {
        assertThat(loader.loadPhotoDataUri(APPLICATION_ID, List.of())).isNull();
    }

    @Test
    void PDFBox가_처리할_수_없는_포맷은_생략한다() {
        String dataUri = loader.loadPhotoDataUri(APPLICATION_ID, List.of(photo(1L, "image/webp")));

        assertThat(dataUri).isNull();
        verify(downloadService, never()).downloadForAdmin(anyLong(), anyLong());
    }

    @Test
    void 용량_상한을_넘는_사진은_생략한다() {
        AdminAttachmentResponse huge = new AdminAttachmentResponse(1L, AttachmentType.ETC,
                ApplicationSectionType.BASIC_INFO, null, "big.jpg", "image/jpeg", 6L * 1024 * 1024, 0);

        assertThat(loader.loadPhotoDataUri(APPLICATION_ID, List.of(huge))).isNull();
        verify(downloadService, never()).downloadForAdmin(anyLong(), anyLong());
    }

    @Test
    void contentType의_파라미터는_data_URI에_넣지_않는다() {
        givenStoredFile(new byte[]{1});

        String dataUri = loader.loadPhotoDataUri(APPLICATION_ID, List.of(photo(1L, "image/JPEG; charset=binary")));

        assertThat(dataUri).startsWith("data:image/jpeg;base64,");
    }

    @Test
    void 사진_조회에_실패해도_PDF_생성을_막지_않는다() {
        given(downloadService.downloadForAdmin(anyLong(), anyLong()))
                .willThrow(new JobApplicationNotFoundException("Attachment was not found."));

        assertThat(loader.loadPhotoDataUri(APPLICATION_ID, List.of(photo(1L, "image/png")))).isNull();
    }

    private void givenStoredFile(byte[] content) {
        Resource resource = new ByteArrayResource(content);
        given(downloadService.downloadForAdmin(anyLong(), anyLong()))
                .willReturn(new AttachmentDownloadResource(
                        resource, content.length, "image/jpeg", "photo.jpg"));
    }

    private AdminAttachmentResponse photo(Long attachmentId, String contentType) {
        return new AdminAttachmentResponse(
                attachmentId,
                AttachmentType.ETC,
                ApplicationSectionType.BASIC_INFO,
                null,
                "photo",
                contentType,
                1024L,
                0);
    }
}
