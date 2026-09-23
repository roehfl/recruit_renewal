package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.MessageSend;
import com.shinyoung.recruit.enumeration.MessageOrigin;
import com.shinyoung.recruit.enumeration.MessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface MessageSendRepository extends JpaRepository<MessageSend, Long> {

    /** 발송 이력 검색. 조건이 null 이면 적용하지 않는다. requestedAt 은 [from, to) 범위, 최신순 고정 정렬. */
    @EntityGraph(attributePaths = {"jobPosting", "stage"})
    @Query("""
            select s
            from MessageSend s
            where s.requestedAt >= :from and s.requestedAt < :to
              and (:type is null or s.type = :type)
              and (:jobPostingId is null or s.jobPosting.id = :jobPostingId)
              and (:test is null or s.test = :test)
              and (:origin is null or s.origin = :origin)
            order by s.requestedAt desc, s.id desc
            """)
    Page<MessageSend> search(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("type") MessageType type,
            @Param("jobPostingId") Long jobPostingId,
            @Param("test") Boolean test,
            @Param("origin") MessageOrigin origin,
            Pageable pageable
    );
}
