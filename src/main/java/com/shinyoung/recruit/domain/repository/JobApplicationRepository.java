package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.dto.response.ApplicationDailyCountRow;
import com.shinyoung.recruit.dto.response.ApplicationExportRow;
import com.shinyoung.recruit.dto.response.FunnelCohortRow;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.PurgeResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    Optional<JobApplication> findByIdAndApplicantId(Long id, Long applicantId);

    /** reconciliation(09e) — 바이너리 삭제 미완(PURGE_PENDING) 잔여 건 재처리 대상(chunk 단위, 09e 리뷰 Medium 1). */
    List<JobApplication> findByPurgeResultOrderByIdAsc(PurgeResult purgeResult, Pageable pageable);

    /** health-scan 치명탐지(09e §6.1) — 후보 applicationId 중 최종 PURGED 인 것만(파기 후 파일 잔존 판정). */
    @Query("select j.id from JobApplication j where j.id in :ids and j.purgeResult = :purgeResult")
    List<Long> findIdsByIdInAndPurgeResult(
            @Param("ids") List<Long> ids,
            @Param("purgeResult") PurgeResult purgeResult);

    Optional<JobApplication> findByApplicantIdAndJobPostingId(Long applicantId, Long jobPostingId);

    boolean existsByApplicantIdAndJobPostingId(Long applicantId, Long jobPostingId);

    @EntityGraph(attributePaths = {
            "jobPosting",
            "jobPosting.applicationFormConfig",
            "jobPosition",
            "jobPosition.jobPosting"
    })
    @Query("""
            select application
            from JobApplication application
            where application.id = :applicationId
              and application.applicant.id = :applicantId
            """)
    Optional<JobApplication> findFormPageByIdAndApplicantId(
            @Param("applicationId") Long applicationId,
            @Param("applicantId") Long applicantId
    );

    @EntityGraph(attributePaths = {"applicant", "jobPosting", "jobPosition"})
    List<JobApplication> findByJobPostingId(Long jobPostingId);

    @EntityGraph(attributePaths = {"jobPosting", "jobPosition"})
    @Query("""
            select application
            from JobApplication application
            where application.applicant.id = :applicantId
            """)
    Page<JobApplication> findMyApplications(@Param("applicantId") Long applicantId, Pageable pageable);

    @EntityGraph(attributePaths = {"jobPosting", "jobPosting.applicationFormConfig", "jobPosition"})
    @Query("""
            select application
            from JobApplication application
            where application.id = :applicationId
              and application.applicant.id = :applicantId
            """)
    Optional<JobApplication> findDashboardByIdAndApplicantId(
            @Param("applicationId") Long applicationId,
            @Param("applicantId") Long applicantId
    );

    @EntityGraph(attributePaths = {"applicant", "jobPosting", "jobPosition"})
    @Query("""
            select application
            from JobApplication application
            where (:jobPostingId is null or application.jobPosting.id = :jobPostingId)
              and (:jobPositionId is null or application.jobPosition.id = :jobPositionId)
              and (:status is null or application.status = :status)
            """)
    Page<JobApplication> searchForAdmin(
            @Param("jobPostingId") Long jobPostingId,
            @Param("jobPositionId") Long jobPositionId,
            @Param("status") JobApplicationStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"applicant", "jobPosting", "jobPosition"})
    @Query("""
            select application
            from JobApplication application
            where application.jobPosting.id = :jobPostingId
              and (:jobPositionId is null or application.jobPosition.id = :jobPositionId)
              and (:status is null or application.status = :status)
            """)
    Page<JobApplication> searchByJobPostingForAdmin(
            @Param("jobPostingId") Long jobPostingId,
            @Param("jobPositionId") Long jobPositionId,
            @Param("status") JobApplicationStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"applicant", "jobPosting", "jobPosition"})
    @Query("select application from JobApplication application where application.id = :applicationId")
    Optional<JobApplication> findAdminDetailById(@Param("applicationId") Long applicationId);

    @Query("""
            select new com.shinyoung.recruit.dto.response.FunnelCohortRow(
                application.id,
                application.status,
                application.jobPosition.id,
                application.jobPosition.positionName,
                application.jobPosition.sortOrder,
                application.submittedAt)
            from JobApplication application
            where application.jobPosting.id = :jobPostingId
              and application.submittedAt is not null
            """)
    List<FunnelCohortRow> findFunnelCohort(@Param("jobPostingId") Long jobPostingId);

    /**
     * 일자별 제출 건수. 구간 제한 없이 전체를 날짜로 접어 반환하므로 결과 행 수는 제출이 있었던 날짜 수만큼이다.
     * 공고 접수 구간 밖 제출(데이터 이상)의 탐지를 서비스 계층에서 함께 처리하려고 범위를 좁히지 않는다.
     */
    @Query("""
            select new com.shinyoung.recruit.dto.response.ApplicationDailyCountRow(
                cast(application.submittedAt as LocalDate),
                count(application.id))
            from JobApplication application
            where application.jobPosting.id = :jobPostingId
              and application.submittedAt is not null
            group by cast(application.submittedAt as LocalDate)
            order by cast(application.submittedAt as LocalDate)
            """)
    List<ApplicationDailyCountRow> findDailySubmittedCounts(@Param("jobPostingId") Long jobPostingId);

    @Query("""
            select count(application)
            from JobApplication application
            where (:jobPostingId is null or application.jobPosting.id = :jobPostingId)
              and (:jobPositionId is null or application.jobPosition.id = :jobPositionId)
              and (:status is null or application.status = :status)
            """)
    long countExportApplications(
            @Param("jobPostingId") Long jobPostingId,
            @Param("jobPositionId") Long jobPositionId,
            @Param("status") JobApplicationStatus status
    );

    @Query("""
            select new com.shinyoung.recruit.dto.response.ApplicationExportRow(
                application.id,
                application.applicantNameSnapshot,
                applicant.phoneNumber,
                applicant.email,
                application.jobPostingTitleSnapshot,
                application.jobPositionNameSnapshot,
                application.status,
                application.submittedAt,
                application.withdrawnAt,
                application.createdAt,
                application.updatedAt)
            from JobApplication application
            join application.applicant applicant
            where (:jobPostingId is null or application.jobPosting.id = :jobPostingId)
              and (:jobPositionId is null or application.jobPosition.id = :jobPositionId)
              and (:status is null or application.status = :status)
            order by application.createdAt desc, application.id desc
            """)
    List<ApplicationExportRow> findExportApplications(
            @Param("jobPostingId") Long jobPostingId,
            @Param("jobPositionId") Long jobPositionId,
            @Param("status") JobApplicationStatus status,
            Pageable pageable
    );
}
