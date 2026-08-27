package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.ApplicationEducation;
import com.shinyoung.recruit.dto.response.FunnelSchoolEducationRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ApplicationEducationRepository extends JpaRepository<ApplicationEducation, Long> {

    List<ApplicationEducation> findByJobApplicationIdOrderBySortOrderAscIdAsc(Long applicationId);

    List<ApplicationEducation> findByJobApplicationIdIn(Collection<Long> applicationIds);

    List<ApplicationEducation> findByJobApplicationId(Long applicationId);

    boolean existsByJobApplicationId(Long applicationId);

    void deleteByJobApplicationId(Long applicationId);

    /**
     * SCHOOL funnel dimension 입력: 공고의 제출 이력(submittedAt != null) 코호트 학력들
     * (applicationId/level/schoolCode/schoolName).
     * 지원자별 최종학력 1교 산출은 service 가 in-memory 로 수행한다.
     */
    @Query("""
            select new com.shinyoung.recruit.dto.response.FunnelSchoolEducationRow(
                e.jobApplication.id, e.educationLevel, e.schoolCode, e.schoolName)
            from ApplicationEducation e
            where e.jobApplication.jobPosting.id = :jobPostingId
              and e.jobApplication.submittedAt is not null
            """)
    List<FunnelSchoolEducationRow> findFunnelSchoolEducations(@Param("jobPostingId") Long jobPostingId);
}
