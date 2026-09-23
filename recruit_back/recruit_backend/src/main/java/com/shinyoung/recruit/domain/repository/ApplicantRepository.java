package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.Applicant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {
    Optional<Applicant> findApplicantByCiHash(String ciHash);

    Optional<Applicant> findByLoginId(String loginId);

    Optional<Applicant> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCiHash(String ciHash);

    Optional<Applicant> findByCiHash(String ciHash);

    /**
     * 파기 대상자 검색(Phase 10). 세 조건 AND, 전부 부분 일치. 휴대폰은 저장값과 입력값 모두
     * 하이픈·공백을 제거해 비교한다(기존 JobApplicationRepository 검색 규칙과 동일).
     * 이미 익명화된 계정(ciHash 가 PURGED: 접두)은 제외한다.
     */
    @Query("""
            select a from Applicant a
             where (:name is null or a.userName like concat('%', :name, '%'))
               and (:phoneNumber is null
                    or replace(replace(a.phoneNumber, '-', ''), ' ', '') like concat('%', :phoneNumber, '%'))
               and (:email is null or a.email like concat('%', :email, '%'))
               and (a.ciHash is null or a.ciHash not like 'PURGED:%')
             order by a.id desc""")
    List<Applicant> searchDataSubjects(
            @Param("name") String name,
            @Param("phoneNumber") String phoneNumber,
            @Param("email") String email,
            Pageable pageable);
}
