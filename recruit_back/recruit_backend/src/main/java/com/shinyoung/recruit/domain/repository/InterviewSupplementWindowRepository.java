package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.InterviewSupplementWindow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface InterviewSupplementWindowRepository extends JpaRepository<InterviewSupplementWindow, Long> {

    List<InterviewSupplementWindow> findBySupplementId(Long supplementId);

    List<InterviewSupplementWindow> findBySupplementIdAndJobApplicationIdIn(
            Long supplementId,
            Collection<Long> jobApplicationIds
    );

    List<InterviewSupplementWindow> findBySupplementIdInAndJobApplicationIdIn(
            Collection<Long> supplementIds,
            Collection<Long> jobApplicationIds
    );
}
