package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.response.AdminAttachmentResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * 지원서 PDF에 넣을 증명사진을 읽어 base64 data URI로 만든다.
 *
 * <p>렌더러가 외부 resource 로드를 금지하므로 이미지는 문서에 인라인으로 심는 방법밖에 없다.
 * 사진 식별 규칙은 관리자 화면(<code>Application.vue</code>의 <code>loadAttachmentFile</code>)과 같다.
 * {@code attachmentType=ETC} + {@code sectionType=BASIC_INFO} 중 마지막 항목을 쓴다.
 *
 * <p>사진은 부가 정보이므로 조회/변환 실패가 PDF 생성 전체를 실패시키지 않는다(경고 로그 후 생략).
 */
@Component
@RequiredArgsConstructor
public class ApplicationPhotoLoader {

    private static final Logger log = LoggerFactory.getLogger(ApplicationPhotoLoader.class);

    /** PDFBox가 처리할 수 있는 포맷만 허용한다. 지원서 첨부 허용 확장자도 이 둘뿐이다. */
    private static final List<String> SUPPORTED_CONTENT_TYPES = List.of("image/jpeg", "image/png");

    /** 일괄 다운로드 시 메모리를 방어하기 위한 사진 용량 상한. 초과하면 사진을 생략한다. */
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

    private final ApplicationAttachmentDownloadService downloadService;

    /**
     * @return 사진 data URI. 사진이 없거나 읽을 수 없으면 null
     */
    public String loadPhotoDataUri(Long applicationId, List<AdminAttachmentResponse> attachments) {
        AdminAttachmentResponse photo = findPhoto(attachments);
        if (photo == null) {
            return null;
        }
        if (!isSupported(photo)) {
            log.warn("지원서 사진 포맷을 PDF에 넣을 수 없어 생략합니다. applicationId={}, contentType={}",
                    applicationId, photo.contentType());
            return null;
        }
        if (photo.fileSize() != null && photo.fileSize() > MAX_PHOTO_BYTES) {
            log.warn("지원서 사진이 상한({} bytes)을 넘어 생략합니다. applicationId={}, fileSize={}",
                    MAX_PHOTO_BYTES, applicationId, photo.fileSize());
            return null;
        }

        try {
            AttachmentDownloadResource resource =
                    downloadService.downloadForAdmin(applicationId, photo.attachmentId());
            byte[] bytes = readAtMost(resource);
            if (bytes == null) {
                log.warn("지원서 사진이 상한({} bytes)을 넘어 생략합니다. applicationId={}",
                        MAX_PHOTO_BYTES, applicationId);
                return null;
            }
            return "data:" + normalizeContentType(photo.contentType()) + ";base64,"
                    + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            // 사진은 부가 정보다. 읽지 못해도 나머지 지원서는 정상 출력해야 한다.
            log.warn("지원서 사진을 읽지 못해 생략합니다. applicationId={}, attachmentId={}",
                    applicationId, photo.attachmentId(), e);
            return null;
        }
    }

    /** 화면과 동일하게 BASIC_INFO 섹션에 올린 ETC 첨부 중 마지막 항목을 사진으로 본다. */
    private AdminAttachmentResponse findPhoto(List<AdminAttachmentResponse> attachments) {
        AdminAttachmentResponse found = null;
        for (AdminAttachmentResponse attachment : attachments) {
            if (attachment.attachmentType() == AttachmentType.ETC
                    && attachment.sectionType() == ApplicationSectionType.BASIC_INFO) {
                found = attachment;
            }
        }
        return found;
    }

    private boolean isSupported(AdminAttachmentResponse photo) {
        return SUPPORTED_CONTENT_TYPES.contains(normalizeContentType(photo.contentType()));
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        // "image/jpeg; charset=..." 같은 파라미터는 data URI 에 넣지 않는다.
        int separator = normalized.indexOf(';');
        return separator < 0 ? normalized : normalized.substring(0, separator).trim();
    }

    /**
     * 메타데이터의 fileSize를 믿지 않고 실제 스트림에서도 상한을 확인한다.
     *
     * @return 상한을 넘으면 null
     */
    private byte[] readAtMost(AttachmentDownloadResource resource) throws Exception {
        if (resource.contentLength() > MAX_PHOTO_BYTES) {
            return null;
        }
        try (InputStream input = resource.resource().getInputStream()) {
            byte[] bytes = input.readNBytes((int) MAX_PHOTO_BYTES + 1);
            return bytes.length > MAX_PHOTO_BYTES ? null : bytes;
        }
    }
}
