package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationAnswerRepository extends JpaRepository<ApplicationAnswer, Long> {

    List<ApplicationAnswer> findByJobApplicationId(Long applicationId);

    List<ApplicationAnswer> findByJobApplicationIdOrderBySortOrderSnapshotAscIdAsc(Long applicationId);

    Optional<ApplicationAnswer> findByJobApplicationIdAndJobPostingQuestionId(Long applicationId, Long questionId);

    void deleteByJobApplicationId(Long applicationId);

    void deleteByJobApplicationIdAndJobPostingQuestionIdIn(Long applicationId, List<Long> questionIds);

    boolean existsByJobApplicationIdAndJobPostingQuestionId(Long applicationId, Long questionId);
}
