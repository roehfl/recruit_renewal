package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.dto.response.ApplicationDailyCountRow;
import com.shinyoung.recruit.dto.response.ApplicationExportRow;
import com.shinyoung.recruit.dto.response.FunnelCohortRow;
import com.shinyoung.recruit.enumeration.GraduationStatus;
import com.shinyoung.recruit.enumeration.JobApplicationStatus;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.PurgeResult;
import com.shinyoung.recruit.enumeration.StageResultStatus;
import com.shinyoung.recruit.enumeration.StageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    /**
     * 관리자 지원현황 조회 통합 검색. 모든 조건은 null 이면 미적용(null-guard) — 공고 지정/비지정 목록이 이 쿼리 하나를 공유한다.
     *
     * <p>{@code phoneNumber} 는 숫자만 남긴 값으로 들어오며, 저장값도 하이픈/공백을 제거해 비교한다.
     *
     * <p>최종학력 판정은 지원서 학력 행들의 최고 레벨 기준이며, CASE rank(HIGH_SCHOOL=0…DOCTOR=4)는
     * {@code EducationLevel} 선언 순서(= {@code AdminApplicationSearchCondition.finalEducationRank()})와 일치해야 한다.
     * 졸업여부/최종학교조건도 같은 최종학력 행 기준으로 판정한다. {@code finalSchoolCondition} 은 enum 5분기 비교를 위해
     * name 문자열로 받는다.
     */
    @EntityGraph(attributePaths = {"applicant", "jobPosting", "jobPosition"})
    @Query("""
            select application
            from JobApplication application
            where (:jobPostingId is null or application.jobPosting.id = :jobPostingId)
              and (:jobPositionId is null or application.jobPosition.id = :jobPositionId)
              and (:status is null or application.status = :status)
              and (:applicationType is null or application.jobPosition.applicationType = :applicationType)
              and (:workLocation is null or application.workLocationCode = :workLocation)
              and (:name is null or application.applicantNameSnapshot like concat('%', :name, '%'))
              and (:phoneNumber is null
                   or replace(replace(application.applicant.phoneNumber, '-', ''), ' ', '')
                      like concat('%', :phoneNumber, '%'))
              and ((:birthDateFrom is null and :birthDateTo is null) or exists (
                    select 1 from ApplicationBasicInfo basicInfo
                    where basicInfo.jobApplication = application
                      and (:birthDateFrom is null or basicInfo.birthDate >= :birthDateFrom)
                      and (:birthDateTo is null or basicInfo.birthDate <= :birthDateTo)))
              and (:finalEducationRank is null or (
                    select max(case
                        when education.educationLevel = com.shinyoung.recruit.enumeration.EducationLevel.HIGH_SCHOOL then 0
                        when education.educationLevel = com.shinyoung.recruit.enumeration.EducationLevel.COLLEGE then 1
                        when education.educationLevel = com.shinyoung.recruit.enumeration.EducationLevel.UNIVERSITY then 2
                        when education.educationLevel = com.shinyoung.recruit.enumeration.EducationLevel.MASTER then 3
                        else 4 end)
                    from ApplicationEducation education
                    where education.jobApplication = application) = :finalEducationRank)
              and (:schoolName is null or exists (
                    select 1 from ApplicationEducation schoolEducation
                    where schoolEducation.jobApplication = application
                      and schoolEducation.schoolName like concat('%', :schoolName, '%')))
              and ((:graduationStatus is null and :finalSchoolCondition is null) or exists (
                    select 1 from ApplicationEducation finalEducation
                    where finalEducation.jobApplication = application
                      and (case
                        when finalEducation.educationLevel = com.shinyoung.recruit.enumeration.EducationLevel.HIGH_SCHOOL then 0
                        when finalEducation.educationLevel = com.shinyoung.recruit.enumeration.EducationLevel.COLLEGE then 1
                        when finalEducation.educationLevel = com.shinyoung.recruit.enumeration.EducationLevel.UNIVERSITY then 2
                        when finalEducation.educationLevel = com.shinyoung.recruit.enumeration.EducationLevel.MASTER then 3
                        else 4 end) = (
                          select max(case
                            when other.educationLevel = com.shinyoung.recruit.enumeration.EducationLevel.HIGH_SCHOOL then 0
                            when other.educationLevel = com.shinyoung.recruit.enumeration.EducationLevel.COLLEGE then 1
                            when other.educationLevel = com.shinyoung.recruit.enumeration.EducationLevel.UNIVERSITY then 2
                            when other.educationLevel = com.shinyoung.recruit.enumeration.EducationLevel.MASTER then 3
                            else 4 end)
                          from ApplicationEducation other
                          where other.jobApplication = application)
                      and (:graduationStatus is null or finalEducation.graduationStatus = :graduationStatus)
                      and (:finalSchoolCondition is null
                           or (:finalSchoolCondition = 'DOMESTIC'
                               and (finalEducation.countryCode is null or finalEducation.countryCode = ''))
                           or (:finalSchoolCondition = 'OVERSEAS'
                               and finalEducation.countryCode is not null and finalEducation.countryCode <> '')
                           or (:finalSchoolCondition = 'TRANSFER' and finalEducation.transfer = true)
                           or (:finalSchoolCondition = 'BRANCH'
                               and finalEducation.campusType = com.shinyoung.recruit.enumeration.CampusType.BRANCH)
                           or (:finalSchoolCondition = 'NIGHT'
                               and finalEducation.dayNightType = com.shinyoung.recruit.enumeration.DayNightType.NIGHT))))
              and (:certificateName is null or exists (
                    select 1 from ApplicationCertificate certificate
                    where certificate.jobApplication = application
                      and certificate.certificateName like concat('%', :certificateName, '%')))
              and ((:languageName is null and :languageLevel is null) or exists (
                    select 1 from ApplicationLanguage applicationLanguage
                    where applicationLanguage.jobApplication = application
                      and (:languageName is null or applicationLanguage.languageName = :languageName)
                      and (:languageLevel is null or applicationLanguage.conversationalAbility = :languageLevel)))
              and ((:stageType is null and :stageResultStatus is null) or exists (
                    select 1 from StageResult stageResult
                    where stageResult.jobApplication = application
                      and (:stageType is null or stageResult.stage.stageType = :stageType)
                      and (:stageResultStatus is null or stageResult.resultStatus = :stageResultStatus)))
            """)
    Page<JobApplication> searchForAdmin(
            @Param("jobPostingId") Long jobPostingId,
            @Param("jobPositionId") Long jobPositionId,
            @Param("status") JobApplicationStatus status,
            @Param("applicationType") JobPositionApplicationType applicationType,
            @Param("workLocation") String workLocation,
            @Param("name") String name,
            @Param("phoneNumber") String phoneNumber,
            @Param("birthDateFrom") LocalDate birthDateFrom,
            @Param("birthDateTo") LocalDate birthDateTo,
            @Param("finalEducationRank") Integer finalEducationRank,
            @Param("schoolName") String schoolName,
            @Param("graduationStatus") GraduationStatus graduationStatus,
            @Param("finalSchoolCondition") String finalSchoolCondition,
            @Param("certificateName") String certificateName,
            @Param("languageName") String languageName,
            @Param("languageLevel") String languageLevel,
            @Param("stageType") StageType stageType,
            @Param("stageResultStatus") StageResultStatus stageResultStatus,
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
