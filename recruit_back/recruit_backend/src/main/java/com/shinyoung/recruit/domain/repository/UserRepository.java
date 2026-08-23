package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findUserByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    List<User> findByLoginIdIn(Collection<String> loginIds);
}
