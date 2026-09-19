package com.shinyoung.recruit.dto.response;

import java.util.List;

public record MessageTestSendResponse(
        Long sendId,
        List<MessageTestSendResultResponse> results
) {
}
