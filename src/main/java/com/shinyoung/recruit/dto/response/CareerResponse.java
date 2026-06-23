package com.shinyoung.recruit.dto.response;

import java.util.List;

public record CareerResponse(
        List<CareerItemResponse> careers
) {
}
