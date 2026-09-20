package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.Notice;
import com.shinyoung.recruit.enumeration.NoticeSearchType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class NoticeSpecification {
    private NoticeSpecification() {

    }

    /** 지원자 공개 조회. 삭제되지 않은 공지만 본다. */
    public static Specification<Notice> search(NoticeSearchType searchType, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            addKeyword(predicates, root, cb, searchType, keyword);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 관리자 조회. deleted 가 null 이면 삭제 여부를 가리지 않는다.
     */
    public static Specification<Notice> adminSearch(
            NoticeSearchType searchType,
            String keyword,
            Boolean deleted,
            boolean pinnedOnly
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (deleted != null) {
                predicates.add(cb.equal(root.get("deleted"), deleted));
            }
            if (pinnedOnly) {
                predicates.add(cb.isTrue(root.get("pinned")));
            }
            addKeyword(predicates, root, cb, searchType, keyword);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addKeyword(
            List<Predicate> predicates,
            Root<Notice> root,
            CriteriaBuilder cb,
            NoticeSearchType searchType,
            String keyword
    ) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
        if (searchType == null || searchType == NoticeSearchType.ALL) {
            predicates.add(cb.or(cb.like(cb.lower(root.get("title")), likeKeyword), cb.like(cb.lower(root.get("contentText")), likeKeyword)));
        } else if (searchType == NoticeSearchType.TITLE) {
            predicates.add(cb.like(cb.lower(root.get("title")), likeKeyword));
        } else if (searchType == NoticeSearchType.CONTENT) {
            predicates.add(cb.like(cb.lower(root.get("contentText")), likeKeyword));
        }
    }
}
