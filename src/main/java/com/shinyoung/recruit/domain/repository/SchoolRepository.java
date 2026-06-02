package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.School;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SchoolRepository extends JpaRepository<School, Long> {

    boolean existsBySchoolCode(String schoolCode);

    Optional<School> findBySchoolCode(String schoolCode);

    /**
     * import fallback dedup 키: (schoolName, schoolType, region). null 필드는 IS NULL 로 매칭한다.
     */
    @Query("""
            select s from School s
            where s.schoolName = :schoolName
              and ((:schoolType is null and s.schoolType is null) or s.schoolType = :schoolType)
              and ((:region is null and s.region is null) or s.region = :region)
            order by s.id asc
            """)
    List<School> findByNaturalKey(
            @Param("schoolName") String schoolName,
            @Param("schoolType") String schoolType,
            @Param("region") String region);

    /**
     * public 자동완성: 활성 학교만, 이름 contains(대소문자 무시). prefix 일치를 우선 정렬하고 top-N(Pageable)로 제한한다.
     * {@code :q} 는 service 에서 LIKE 특수문자(%, _, \\)를 escape 한 값이라 {@code escape '\\'} 를 명시한다.
     */
    @Query("""
            select s from School s
            where s.active = true
              and lower(s.schoolName) like lower(concat('%', :q, '%')) escape '\\'
              and (:schoolType is null or s.schoolType = :schoolType)
            order by
              case when lower(s.schoolName) like lower(concat(:q, '%')) escape '\\' then 0 else 1 end,
              s.schoolName asc, s.id asc
            """)
    List<School> search(@Param("q") String q, @Param("schoolType") String schoolType, Pageable pageable);

    /**
     * admin 목록: 비활성 포함, q/schoolType 옵션 필터. {@code :q} 는 escape 된 값. 정렬은 Pageable 로 받는다.
     */
    @Query("""
            select s from School s
            where (:q is null or lower(s.schoolName) like lower(concat('%', :q, '%')) escape '\\')
              and (:schoolType is null or s.schoolType = :schoolType)
            """)
    Page<School> adminSearch(@Param("q") String q, @Param("schoolType") String schoolType, Pageable pageable);
}
