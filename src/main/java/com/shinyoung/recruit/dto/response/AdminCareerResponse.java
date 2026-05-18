package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.CareerType;

import java.util.List;

public record AdminCareerResponse(
        CareerType careerType,
        List<AdminCareerItemResponse> careers
) {
}
