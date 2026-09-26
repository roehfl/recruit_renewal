package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.InterviewSupplementAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InterviewSupplementAnswerRepository extends JpaRepository<InterviewSupplementAnswer, Long> {

    boolean existsByQuestionId(Long questionId);

    @Query("""
            select count(answer) > 0
            from InterviewSupplementAnswer answer
            where answer.question.supplement.id = :supplementId
            """)
    boolean existsBySupplementId(@Param("supplementId") Long supplementId);

    @Query("""
            select answer
            from InterviewSupplementAnswer answer
            join fetch answer.question question
            where question.supplement.id = :supplementId
            """)
    List<InterviewSupplementAnswer> findBySupplementId(@Param("supplementId") Long supplementId);

    @Query("""
            select answer
            from InterviewSupplementAnswer answer
            join fetch answer.question question
            where question.supplement.id = :supplementId
              and answer.jobApplication.id = :jobApplicationId
            """)
    List<InterviewSupplementAnswer> findBySupplementIdAndJobApplicationId(
            @Param("supplementId") Long supplementId,
            @Param("jobApplicationId") Long jobApplicationId
    );

    @Query("""
            select answer
            from InterviewSupplementAnswer answer
            join fetch answer.question question
            where question.supplement.id in :supplementIds
              and answer.jobApplication.id in :jobApplicationIds
            """)
    List<InterviewSupplementAnswer> findBySupplementIdInAndJobApplicationIdIn(
            @Param("supplementIds") Collection<Long> supplementIds,
            @Param("jobApplicationIds") Collection<Long> jobApplicationIds
    );
}
