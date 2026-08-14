package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingImageMetaRequest;
import com.shinyoung.recruit.dto.response.JobPostingImageResponse;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.posting-image.storage-root=build/test-posting-images"
})
@Transactional
class JobPostingImageServiceTest {

    private static final byte[] PNG_HEAD = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG_HEAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingImageService jobPostingImageService;

    private Long createPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "2026 채용",
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest("백엔드", 0)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        ));
    }

    private Long createPostingWithoutContent() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "2026 채용",
                null,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest("백엔드", 0)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        ));
    }

    private MultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG_HEAD);
    }

    @Test
    void 이미지를_추가하면_목록에서_정렬순으로_조회된다() {
        Long postingId = createPosting();

        jobPostingImageService.addImage(postingId, png("b.png"), "포스터 2", 1);
        jobPostingImageService.addImage(postingId, png("a.png"), "포스터 1", 0);

        List<JobPostingImageResponse> images = jobPostingImageService.getImages(postingId);
        assertThat(images).hasSize(2);
        assertThat(images.get(0).altText()).isEqualTo("포스터 1");
        assertThat(images.get(1).altText()).isEqualTo("포스터 2");
    }

    @Test
    void sortOrder_생략시_맨뒤에_붙는다() {
        Long postingId = createPosting();
        jobPostingImageService.addImage(postingId, png("a.png"), "포스터 1", 0);

        jobPostingImageService.addImage(postingId, png("b.png"), "포스터 2", null);

        List<JobPostingImageResponse> images = jobPostingImageService.getImages(postingId);
        assertThat(images.get(1).altText()).isEqualTo("포스터 2");
        assertThat(images.get(1).sortOrder()).isEqualTo(1);
    }

    @Test
    void altText_없으면_실패한다() {
        Long postingId = createPosting();

        assertThatThrownBy(() -> jobPostingImageService.addImage(postingId, png("a.png"), " ", 0))
                .isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("대체 텍스트");
    }

    @Test
    void 허용되지_않은_contentType은_실패한다() {
        Long postingId = createPosting();
        MultipartFile gif = new MockMultipartFile("file", "a.gif", "image/gif", PNG_HEAD);

        assertThatThrownBy(() -> jobPostingImageService.addImage(postingId, gif, "포스터", 0))
                .isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void contentType과_시그니처가_다르면_실패한다() {
        Long postingId = createPosting();
        MultipartFile fake = new MockMultipartFile("file", "a.png", "image/png", JPEG_HEAD);

        assertThatThrownBy(() -> jobPostingImageService.addImage(postingId, fake, "포스터", 0))
                .isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("시그니처");
    }

    @Test
    void 최대_장수를_넘으면_실패한다() {
        Long postingId = createPosting();
        for (int i = 0; i < 10; i++) {
            jobPostingImageService.addImage(postingId, png("p" + i + ".png"), "포스터 " + i, i);
        }

        assertThatThrownBy(() -> jobPostingImageService.addImage(postingId, png("over.png"), "초과", 10))
                .isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("최대");
    }

    @Test
    void 생성시_메타와_파일수가_다르면_실패한다() {
        Long postingId = createPosting();
        // createImages는 JobPostingService.create 경로에서 쓰이지만 단독 검증도 가능해야 한다.
        assertThatThrownBy(() -> jobPostingImageService.createImages(
                postingId,
                List.of(new JobPostingImageMetaRequest("포스터", 0)),
                List.of(png("a.png"), png("b.png"))
        )).isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("일치");
    }

    @Test
    void altText를_수정한다() {
        Long postingId = createPosting();
        Long imageId = jobPostingImageService.addImage(postingId, png("a.png"), "이전", 0);

        jobPostingImageService.updateAltText(postingId, imageId, "이후");

        assertThat(jobPostingImageService.getImages(postingId).get(0).altText()).isEqualTo("이후");
    }

    @Test
    void 이미지를_삭제한다() {
        Long postingId = createPosting();
        Long imageId = jobPostingImageService.addImage(postingId, png("a.png"), "포스터", 0);

        jobPostingImageService.deleteImage(postingId, imageId);

        assertThat(jobPostingImageService.getImages(postingId)).isEmpty();
    }

    @Test
    void 게시중_공고의_마지막_본문_이미지는_삭제할_수_없다() {
        Long postingId = createPostingWithoutContent();
        Long imageId = jobPostingImageService.addImage(postingId, png("a.png"), "포스터", 0);
        jobPostingService.publish(postingId);

        assertThatThrownBy(() -> jobPostingImageService.deleteImage(postingId, imageId))
                .isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("마지막 본문 이미지");
    }

    @Test
    void 게시중이라도_이미지가_더_남으면_삭제할_수_있다() {
        Long postingId = createPostingWithoutContent();
        Long first = jobPostingImageService.addImage(postingId, png("a.png"), "포스터 1", 0);
        jobPostingImageService.addImage(postingId, png("b.png"), "포스터 2", 1);
        jobPostingService.publish(postingId);

        jobPostingImageService.deleteImage(postingId, first);

        assertThat(jobPostingImageService.getImages(postingId)).hasSize(1);
    }

    @Test
    void 게시중이라도_레거시_contentHtml이_있으면_마지막_이미지를_삭제할_수_있다() {
        Long postingId = createPosting();
        Long imageId = jobPostingImageService.addImage(postingId, png("a.png"), "포스터", 0);
        jobPostingService.publish(postingId);

        jobPostingImageService.deleteImage(postingId, imageId);

        assertThat(jobPostingImageService.getImages(postingId)).isEmpty();
    }

    @Test
    void draft_공고는_마지막_이미지도_삭제할_수_있다() {
        Long postingId = createPostingWithoutContent();
        Long imageId = jobPostingImageService.addImage(postingId, png("a.png"), "포스터", 0);

        jobPostingImageService.deleteImage(postingId, imageId);

        assertThat(jobPostingImageService.getImages(postingId)).isEmpty();
    }

    @Test
    void 다른_공고의_이미지는_삭제할_수_없다() {
        Long postingA = createPosting();
        Long postingB = createPosting();
        Long imageId = jobPostingImageService.addImage(postingA, png("a.png"), "포스터", 0);

        assertThatThrownBy(() -> jobPostingImageService.deleteImage(postingB, imageId))
                .isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void 순서를_재지정한다() {
        Long postingId = createPosting();
        Long first = jobPostingImageService.addImage(postingId, png("a.png"), "포스터 1", 0);
        Long second = jobPostingImageService.addImage(postingId, png("b.png"), "포스터 2", 1);

        jobPostingImageService.reorder(postingId, List.of(second, first));

        List<JobPostingImageResponse> images = jobPostingImageService.getImages(postingId);
        assertThat(images.get(0).id()).isEqualTo(second);
        assertThat(images.get(1).id()).isEqualTo(first);
    }

    @Test
    void 순서_재지정시_전체_id가_일치하지_않으면_실패한다() {
        Long postingId = createPosting();
        Long first = jobPostingImageService.addImage(postingId, png("a.png"), "포스터 1", 0);
        jobPostingImageService.addImage(postingId, png("b.png"), "포스터 2", 1);

        assertThatThrownBy(() -> jobPostingImageService.reorder(postingId, List.of(first)))
                .isInstanceOf(InvalidJobPostingException.class);
    }
}
