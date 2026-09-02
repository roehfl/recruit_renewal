package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    Page<JobPosting> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"jobPositions", "applicationFormConfig"})
    @Query("select jobPosting from JobPosting jobPosting where jobPosting.id = :id")
    Optional<JobPosting> findDetailById(@Param("id") Long id);

    @Query("""
            select jobPosting.id as id,
                   jobPosting.title as title,
                   jobPosting.postingType as postingType,
                   jobPosting.summary as summary,
                   jobPosting.receptionStartDateTime as receptionStartDateTime,
                   jobPosting.receptionEndDateTime as receptionEndDateTime,
                   jobPosting.pinned as pinned,
                   applicationFormConfig.useEducation as useEducation,
                   applicationFormConfig.requireEducation as requireEducation,
                   applicationFormConfig.useCareer as useCareer,
                   applicationFormConfig.requireCareer as requireCareer,
                   applicationFormConfig.useCertificate as useCertificate,
                   applicationFormConfig.requireCertificate as requireCertificate,
                   applicationFormConfig.useLanguage as useLanguage,
                   applicationFormConfig.requireLanguage as requireLanguage,
                   applicationFormConfig.useMilitary as useMilitary,
                   applicationFormConfig.requireMilitary as requireMilitary,
                   applicationFormConfig.useAward as useAward,
                   applicationFormConfig.requireAward as requireAward,
                   applicationFormConfig.useGapPeriod as useGapPeriod,
                   applicationFormConfig.requireGapPeriod as requireGapPeriod,
                   applicationFormConfig.useAttachment as useAttachment
            from JobPosting jobPosting
            left join jobPosting.applicationFormConfig applicationFormConfig
            where jobPosting.status = :status
              and jobPosting.visible = true
              and (jobPosting.displayStartDateTime is null or jobPosting.displayStartDateTime <= :now)
              and (jobPosting.displayEndDateTime is null or jobPosting.displayEndDateTime >= :now)
            order by jobPosting.pinned desc,
                     case
                         when :now between jobPosting.receptionStartDateTime and jobPosting.receptionEndDateTime then 1
                         when :now < jobPosting.receptionStartDateTime then 2
                         else 3
                     end asc,
                     jobPosting.displayOrder asc,
                     jobPosting.receptionEndDateTime asc,
                     jobPosting.publishedAt desc,
                     jobPosting.id desc
            """)
    Page<JobPostingPublicListProjection> findPublicList(
            @Param("status") JobPostingStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    /**
     * 지원서 설정 현황판용 조회. 설정 상태는 질문·첨부·레이아웃을 합쳐 계산해야 하므로
     * SQL 로 좁힐 수 있는 조건(상태·제목)만 여기서 처리하고 나머지는 서비스에서 거른다.
     */
    @Query("""
            select jobPosting
            from JobPosting jobPosting
            left join fetch jobPosting.applicationFormConfig
            where (:status is null or jobPosting.status = :status)
              and (:keyword is null or lower(jobPosting.title) like lower(concat('%', :keyword, '%')))
            order by jobPosting.receptionStartDateTime asc, jobPosting.id desc
            """)
    List<JobPosting> findAllForApplicationFormSummary(
            @Param("status") JobPostingStatus status,
            @Param("keyword") String keyword
    );

    @EntityGraph(attributePaths = {"jobPositions", "applicationFormConfig"})
    Optional<JobPosting> findByIdAndStatus(Long id, JobPostingStatus status);

    @EntityGraph(attributePaths = {"jobPositions", "applicationFormConfig"})
    @Query("""
            select jobPosting
            from JobPosting jobPosting
            where jobPosting.id = :id
              and jobPosting.status = :status
              and jobPosting.visible = true
              and (jobPosting.displayStartDateTime is null or jobPosting.displayStartDateTime <= :now)
              and (jobPosting.displayEndDateTime is null or jobPosting.displayEndDateTime >= :now)
            """)
    Optional<JobPosting> findPublicDetailById(
            @Param("id") Long id,
            @Param("status") JobPostingStatus status,
            @Param("now") LocalDateTime now
    );

    /** findPublicDetailById와 동일 공개조건의 경량 exists — 이미지 서빙처럼 발행 여부만 필요할 때 사용한다. */
    @Query("""
            select count(jobPosting.id) > 0
            from JobPosting jobPosting
            where jobPosting.id = :id
              and jobPosting.status = :status
              and jobPosting.visible = true
              and (jobPosting.displayStartDateTime is null or jobPosting.displayStartDateTime <= :now)
              and (jobPosting.displayEndDateTime is null or jobPosting.displayEndDateTime >= :now)
            """)
    boolean existsPublicById(
            @Param("id") Long id,
            @Param("status") JobPostingStatus status,
            @Param("now") LocalDateTime now
    );
}
