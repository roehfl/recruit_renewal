package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.enumeration.JobPostingType;

import java.time.LocalDateTime;

public interface JobPostingPublicListProjection {

    Long getId();

    String getTitle();

    JobPostingType getPostingType();

    String getSummary();

    LocalDateTime getReceptionStartDateTime();

    LocalDateTime getReceptionEndDateTime();

    Boolean getPinned();
}
