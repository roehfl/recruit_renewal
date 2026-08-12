package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.JobPostingImageProperties;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.JobPostingImage;
import com.shinyoung.recruit.domain.repository.JobPostingImageRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.JobPostingImageMetaRequest;
import com.shinyoung.recruit.dto.response.JobPostingImageResponse;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingImageService {

    private static final int ALT_TEXT_MAX_LENGTH = 200;
    private static final int SIGNATURE_HEAD_LENGTH = 12;

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingImageRepository jobPostingImageRepository;
    private final JobPostingImageStorageService storageService;
    private final JobPostingImageProperties properties;
    private final Clock clock;

    public List<JobPostingImageResponse> getImages(Long jobPostingId) {
        return jobPostingImageRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(jobPostingId).stream()
                .map(JobPostingImageResponse::from)
                .toList();
    }

    public long countImages(Long jobPostingId) {
        return jobPostingImageRepository.countByJobPostingId(jobPostingId);
    }

    /** 공고 생성(multipart) 경로. 파일 전체를 먼저 검증한 뒤 저장해 부분 저장을 최소화한다. */
    @Transactional
    public void createImages(Long jobPostingId, List<JobPostingImageMetaRequest> metas, List<MultipartFile> files) {
        boolean hasFiles = files != null && !files.isEmpty();
        boolean hasMetas = metas != null && !metas.isEmpty();
        if (!hasFiles) {
            if (hasMetas) {
                throw new InvalidJobPostingException("이미지 파일 없이 이미지 정보만 전달할 수 없습니다.");
            }
            return;
        }
        if (!hasMetas || metas.size() != files.size()) {
            throw new InvalidJobPostingException("이미지 파일 수와 이미지 정보 수가 일치해야 합니다.");
        }

        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateTotalCount(jobPostingId, files.size());
        validateDistinctSortOrders(metas);
        for (int i = 0; i < files.size(); i++) {
            validateFile(files.get(i));
            validateAltText(metas.get(i).altText());
            validateSortOrder(metas.get(i).sortOrder());
        }
        for (int i = 0; i < files.size(); i++) {
            saveImage(jobPosting, files.get(i), metas.get(i).altText(), metas.get(i).sortOrder());
        }
    }

    /** 수정 화면 diff 경로. sortOrder 생략 시 맨 뒤에 붙인다. */
    @Transactional
    public Long addImage(Long jobPostingId, MultipartFile file, String altText, Integer sortOrder) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        rejectClosed(jobPosting);
        validateTotalCount(jobPostingId, 1);
        validateFile(file);
        validateAltText(altText);
        Integer resolvedSortOrder = sortOrder != null ? sortOrder : nextSortOrder(jobPostingId);
        validateSortOrder(resolvedSortOrder);
        return saveImage(jobPosting, file, altText, resolvedSortOrder);
    }

    @Transactional
    public Long updateAltText(Long jobPostingId, Long imageId, String altText) {
        rejectClosed(findJobPosting(jobPostingId));
        validateAltText(altText);
        JobPostingImage image = findImage(jobPostingId, imageId);
        image.updateAltText(altText.trim());
        return image.getId();
    }

    @Transactional
    public void deleteImage(Long jobPostingId, Long imageId) {
        rejectClosed(findJobPosting(jobPostingId));
        JobPostingImage image = findImage(jobPostingId, imageId);
        String storagePath = image.getStoragePath();
        jobPostingImageRepository.delete(image);
        storageService.deleteIfExists(storagePath);
    }

    /** imageIds 배열 index가 새 sortOrder가 된다. 해당 공고 이미지 전체와 정확히 일치해야 한다. */
    @Transactional
    public void reorder(Long jobPostingId, List<Long> imageIds) {
        rejectClosed(findJobPosting(jobPostingId));
        if (imageIds == null || imageIds.isEmpty()) {
            throw new InvalidJobPostingException("이미지 순서 목록이 비어 있습니다.");
        }
        List<JobPostingImage> images = jobPostingImageRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(jobPostingId);
        Set<Long> existingIds = new HashSet<>(images.stream().map(JobPostingImage::getId).toList());
        Set<Long> requestedIds = new HashSet<>(imageIds);
        if (imageIds.size() != images.size() || !existingIds.equals(requestedIds)) {
            throw new InvalidJobPostingException("이미지 순서 목록은 공고의 전체 이미지와 일치해야 합니다.");
        }
        for (JobPostingImage image : images) {
            image.changeSortOrder(imageIds.indexOf(image.getId()));
        }
    }

    public PostingImageResource loadAdminImage(Long jobPostingId, Long imageId) {
        JobPostingImage image = findImage(jobPostingId, imageId);
        return storageService.load(image.getStoragePath(), image.getContentType());
    }

    /** 공개 서빙: 발행+공개조건 충족 공고만. draft 유출 차단의 2차 방어선. */
    public PostingImageResource loadPublicImage(Long jobPostingId, Long imageId) {
        LocalDateTime now = LocalDateTime.now(clock);
        jobPostingRepository.findPublicDetailById(jobPostingId, JobPostingStatus.PUBLISHED, now)
                .orElseThrow(() -> new JobPostingNotFoundException("공고 이미지를 찾을 수 없습니다."));
        return loadAdminImage(jobPostingId, imageId);
    }

    private Long saveImage(JobPosting jobPosting, MultipartFile file, String altText, Integer sortOrder) {
        String extension = extractExtension(file.getOriginalFilename());
        StoredPostingImageFile stored = storageService.store(jobPosting.getId(), file, extension);
        JobPostingImage image = JobPostingImage.create(
                jobPosting,
                sanitizeFileName(file.getOriginalFilename()),
                stored.storagePath(),
                stored.contentType(),
                stored.fileSize(),
                sortOrder,
                altText.trim()
        );
        return jobPostingImageRepository.save(image).getId();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidJobPostingException("이미지 파일이 없습니다.");
        }
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw new InvalidJobPostingException(
                    "이미지 크기는 장당 " + properties.getMaxFileSize().toMegabytes() + "MB 이하이어야 합니다.");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (contentType == null || !properties.getAllowedContentTypes().contains(contentType)) {
            throw new InvalidJobPostingException("허용되지 않은 이미지 형식입니다.");
        }
        String extension = extractExtension(file.getOriginalFilename());
        if (!properties.getAllowedExtensions().contains(extension)) {
            throw new InvalidJobPostingException("허용되지 않은 이미지 확장자입니다.");
        }
        byte[] head = readHead(file);
        if (!ImageSignatureValidator.matches(contentType, head)) {
            throw new InvalidJobPostingException("이미지 형식이 올바르지 않습니다(시그니처 불일치).");
        }
    }

    private byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SIGNATURE_HEAD_LENGTH);
        } catch (IOException e) {
            throw new InvalidJobPostingException("이미지 파일을 읽지 못했습니다.");
        }
    }

    private String normalizeContentType(String contentType) {
        return "image/jpg".equals(contentType) ? "image/jpeg" : contentType;
    }

    private String extractExtension(String originalFileName) {
        String name = sanitizeFileName(originalFileName);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new InvalidJobPostingException("이미지 확장자를 확인할 수 없습니다.");
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new InvalidJobPostingException("이미지 파일명이 없습니다.");
        }
        String name = originalFileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private void validateAltText(String altText) {
        if (altText == null || altText.isBlank()) {
            throw new InvalidJobPostingException("이미지 대체 텍스트는 필수입니다.");
        }
        if (altText.trim().length() > ALT_TEXT_MAX_LENGTH) {
            throw new InvalidJobPostingException("이미지 대체 텍스트는 " + ALT_TEXT_MAX_LENGTH + "자 이하이어야 합니다.");
        }
    }

    private void validateSortOrder(Integer sortOrder) {
        if (sortOrder == null || sortOrder < 0) {
            throw new InvalidJobPostingException("이미지 정렬 순서는 0 이상이어야 합니다.");
        }
    }

    private void validateDistinctSortOrders(List<JobPostingImageMetaRequest> metas) {
        Set<Integer> seen = new HashSet<>();
        for (JobPostingImageMetaRequest meta : metas) {
            if (meta.sortOrder() != null && !seen.add(meta.sortOrder())) {
                throw new InvalidJobPostingException("이미지 정렬 순서는 중복될 수 없습니다.");
            }
        }
    }

    private void validateTotalCount(Long jobPostingId, int adding) {
        long current = jobPostingImageRepository.countByJobPostingId(jobPostingId);
        if (current + adding > properties.getMaxImagesPerPosting()) {
            throw new InvalidJobPostingException(
                    "공고 이미지는 최대 " + properties.getMaxImagesPerPosting() + "장까지 등록할 수 있습니다.");
        }
    }

    private int nextSortOrder(Long jobPostingId) {
        return jobPostingImageRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(jobPostingId).stream()
                .mapToInt(JobPostingImage::getSortOrder)
                .max()
                .orElse(-1) + 1;
    }

    private void rejectClosed(JobPosting jobPosting) {
        if (jobPosting.getStatus() == JobPostingStatus.CLOSED) {
            throw new InvalidJobPostingException("마감된 공고의 이미지는 수정할 수 없습니다.");
        }
    }

    private JobPosting findJobPosting(Long id) {
        return jobPostingRepository.findById(id)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + id));
    }

    private JobPostingImage findImage(Long jobPostingId, Long imageId) {
        return jobPostingImageRepository.findByIdAndJobPostingId(imageId, jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("공고 이미지를 찾을 수 없습니다. id=" + imageId));
    }
}
