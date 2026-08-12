package com.shinyoung.recruit.dto.request;

/** multipart 생성 시 imageFiles와 index로 짝을 이루는 이미지 메타. */
public record JobPostingImageMetaRequest(String altText, Integer sortOrder) {
}
