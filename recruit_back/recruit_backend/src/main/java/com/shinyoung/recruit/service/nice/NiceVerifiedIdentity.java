package com.shinyoung.recruit.service.nice;

import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;

import java.io.Serializable;
import java.time.Instant;

/**
 * 본인확인 결과 중 세션에 보관하는 부분.
 *
 * <p>생년월일·성별이 들어 있으므로 <b>이 객체를 그대로 응답에 담지 않는다.</b>
 * 화면에는 {@code name}, {@code phoneNumber} 만 내려간다.
 *
 * <p>{@code birthDate}·{@code gender} 는 NICE 원값 그대로다({@code BIRTHDATE} = {@code yyyyMMdd},
 * {@code GENDER} = NICE 코드). 해석·변환하지 않는다 — 가입자 중복 판정 키의 재료로만 쓴다.
 *
 * <p>HTTP 세션 속성으로 저장되므로 {@link Serializable} 이어야 한다.
 */
public record NiceVerifiedIdentity(
        NiceVerificationPurpose purpose,
        String name,
        String phoneNumber,
        String birthDate,
        String gender,
        Instant verifiedAt
) implements Serializable {
}
