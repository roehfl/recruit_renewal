package com.shinyoung.recruit.service;

/** 지원서 최종 제출(재제출 포함) 성공. 커밋 뒤 ApplicationSubmittedMailListener 가 제출 완료 메일을 보낸다. */
public record ApplicationSubmittedEvent(Long applicationId) {
}
