package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.School;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SchoolRepository extends JpaRepository<School, Long> {

    boolean existsBySchoolCode(String schoolCode);

    /**
     * public 자동완성: 활성 학교만, 이름 contains(대소문자 무시). prefix 일치를 우선 정렬하고 top-N(Pageable)로 제한한다.
     */
    @Query("""
            select s from School s
            where s.active = true
              and lower(s.schoolName) like lower(concat('%', :q, '%'))
              and (:schoolType is null or s.schoolType = :schoolType)
            order by
              case when lower(s.schoolName) like lower(concat(:q, '%')) then 0 else 1 end,
              s.schoolName asc, s.id asc
            """)
    List<School> search(@Param("q") String q, @Param("schoolType") String schoolType, Pageable pageable);

    /**
     * admin 목록: 비활성 포함, q/schoolType 옵션 필터. 정렬은 Pageable 로 받는다.
     */
    @Query("""
            select s from School s
            where (:q is null or lower(s.schoolName) like lower(concat('%', :q, '%')))
              and (:schoolType is null or s.schoolType = :schoolType)
            """)
    Page<School> adminSearch(@Param("q") String q, @Param("schoolType") String schoolType, Pageable pageable);
}
