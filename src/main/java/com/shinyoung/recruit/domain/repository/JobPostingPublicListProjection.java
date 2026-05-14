package com.shinyoung.recruit.domain.repository;

import java.time.LocalDateTime;

public interface JobPostingPublicListProjection {

    Long getId();

    String getTitle();

    LocalDateTime getReceptionStartDateTime();

    LocalDateTime getReceptionEndDateTime();
}
