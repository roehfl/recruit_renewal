package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Interview;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.Stage;

import java.util.Objects;

/** 수신자 1명의 변수 계산 입력. jobPosting은 필수이고, stage·interview 는 null 일 수 있다. */
public record MessageVariableContext(
        String name,
        JobPosting jobPosting,
        Stage stage,
        Interview interview
) {
    public MessageVariableContext {
        Objects.requireNonNull(jobPosting, "jobPosting is required");
    }
}
