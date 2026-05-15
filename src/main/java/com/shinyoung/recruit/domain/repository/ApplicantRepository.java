package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.Applicant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {
    Optional<Applicant> findApplicantByCiHash(String ciHash);

    Optional<Applicant> findByLoginId(String loginId);
}
