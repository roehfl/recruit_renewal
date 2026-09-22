package com.shinyoung.recruit.dto.response;

/** 아이디 찾기 결과. 원문 아이디는 담지 않는다 — 부분 마스킹한 값만. */
public record ApplicantFindEmailResponse(String maskedEmail) {
}
