package com.shinyoung.recruit.dto.response;

/**
 * CERTIFICATE dimension 산출 입력(projection): 제출 이력 코호트의 자격 한 건(applicationId, certificateName).
 * 응답 DTO가 아니라 집계 입력이며 JPA 생성자 표현식으로 직접 조회된다. 자격명은 free-text 라 service 에서
 * 정규화(trim + 공백 압축)해 그룹 키로 쓰며, "자격명별 보유 지원자 distinct"로 센다(지원자는 여러 그룹에 중복 가능).
 */
public record FunnelCertificateRow(
        Long applicationId,
        String certificateName
) {
}
