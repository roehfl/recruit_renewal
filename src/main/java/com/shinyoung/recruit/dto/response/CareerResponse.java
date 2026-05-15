package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.enumeration.CareerType;

import java.util.List;

public record CareerResponse(
        CareerType careerType,
        List<CareerItemResponse> careers
) {
}
