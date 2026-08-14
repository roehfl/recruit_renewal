package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.CommonCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Long> {

    /** public read: 활성 코드만 정렬. */
    List<CommonCode> findByGroupCodeAndActiveTrueOrderBySortOrderAscIdAsc(String groupCode);

    /** admin: 비활성 포함 group 단위 정렬. */
    List<CommonCode> findByGroupCodeOrderBySortOrderAscIdAsc(String groupCode);

    /** admin: groupCode 미지정 시 전체(그룹/정렬 순). */
    List<CommonCode> findAllByOrderByGroupCodeAscSortOrderAscIdAsc();

    boolean existsByGroupCodeAndCode(String groupCode, String code);

    boolean existsByGroupCodeAndCodeAndActiveTrue(String groupCode, String code);
}
