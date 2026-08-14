package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationEducationSemesterGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationEducationSemesterGradeRepository extends JpaRepository<ApplicationEducationSemesterGrade, Long> {

    List<ApplicationEducationSemesterGrade> findByEducationIdInOrderBySchoolYearAscSemesterAscIdAsc(List<Long> educationIds);

    void deleteByEducationIdIn(List<Long> educationIds);
}
