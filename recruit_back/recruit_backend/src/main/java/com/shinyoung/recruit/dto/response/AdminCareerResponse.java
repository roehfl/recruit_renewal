package com.shinyoung.recruit.dto.response;

import java.util.List;

public record AdminCareerResponse(
        List<AdminCareerItemResponse> careers
) {
}
