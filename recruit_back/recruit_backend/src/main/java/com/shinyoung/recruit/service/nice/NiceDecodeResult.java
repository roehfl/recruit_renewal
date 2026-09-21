package com.shinyoung.recruit.service.nice;

/**
 * 복호화 결과. 평문과 암호문 생성 시각을 함께 담는다.
 *
 * <p>둘을 따로 얻을 수 없다. 모듈의 {@code getCipherDateTime()} 은 인자가 없고
 * {@code fnDecode} 가 채운 인스턴스 상태를 읽기 때문이다(2026-09-21 실측).
 *
 * @param cipherEpochSeconds 암호문 생성 시각(epoch 초). 읽을 수 없으면 {@code null}
 */
public record NiceDecodeResult(String plaindata, Long cipherEpochSeconds) {
}
