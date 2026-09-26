package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.InterviewSupplementQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewSupplementQuestionRepository extends JpaRepository<InterviewSupplementQuestion, Long> {

    List<InterviewSupplementQuestion> findBySupplementIdOrderBySortOrderAscIdAsc(Long supplementId);

    List<InterviewSupplementQuestion> findBySupplementIdIn(Collection<Long> supplementIds);

    Optional<InterviewSupplementQuestion> findByIdAndSupplementId(Long id, Long supplementId);
}
