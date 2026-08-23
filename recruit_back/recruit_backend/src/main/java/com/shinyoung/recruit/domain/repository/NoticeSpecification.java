package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.Notice;
import com.shinyoung.recruit.enumeration.NoticeSearchType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class NoticeSpecification {
    private NoticeSpecification() {

    }

    public static Specification<Notice> search(NoticeSearchType searchType, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
//            predicates.add(cb.isTrue(root.get("visible")));
            if (keyword != null && !keyword.isBlank()) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
                if (searchType == null || searchType == NoticeSearchType.ALL) {
                    predicates.add(cb.or(cb.like(cb.lower(root.get("title")), likeKeyword), cb.like(cb.lower(root.get("contentText")), likeKeyword)));
                } else if (searchType == NoticeSearchType.TITLE) {
                    predicates.add(cb.like(cb.lower(root.get("title")), likeKeyword));
                } else if (searchType == NoticeSearchType.CONTENT) {
                    predicates.add(cb.like(cb.lower(root.get("contentText")), likeKeyword));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
