package com.shinyoung.recruit.domain.repository;

public interface JobPositionCountProjection {

    Long getJobPostingId();

    long getPositionCount();
}
