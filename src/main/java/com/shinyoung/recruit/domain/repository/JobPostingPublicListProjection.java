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

    Boolean getUseEducation();

    Boolean getRequireEducation();

    Boolean getUseCareer();

    Boolean getRequireCareer();

    Boolean getUseCertificate();

    Boolean getRequireCertificate();

    Boolean getUseLanguage();

    Boolean getRequireLanguage();

    Boolean getUseMilitary();

    Boolean getRequireMilitary();

    Boolean getUseAward();

    Boolean getRequireAward();

    Boolean getUseGapPeriod();

    Boolean getRequireGapPeriod();
}
