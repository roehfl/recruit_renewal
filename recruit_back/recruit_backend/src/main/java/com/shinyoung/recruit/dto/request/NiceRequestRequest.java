package com.shinyoung.recruit.dto.request;

import com.shinyoung.recruit.enumeration.NiceVerificationPurpose;
import jakarta.validation.constraints.NotNull;

/**
 * 본인확인 요청 발급. 용도는 서버가 레코드에 기록해 소비 시점에 대조한다.
 *
 * <p>기본값을 두지 않는다 — 호출부가 용도를 빠뜨리면 조용히 가입용이 되기 때문이다.
 * 모르는 값은 역직렬화 단계에서 400 이 난다.
 */
public record NiceRequestRequest(
        @NotNull(message = "purpose는 필수입니다.")
        NiceVerificationPurpose purpose
) {
}
