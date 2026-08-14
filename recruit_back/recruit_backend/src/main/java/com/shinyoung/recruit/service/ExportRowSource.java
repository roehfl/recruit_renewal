package com.shinyoung.recruit.service;

import java.util.List;

/**
 * Export writer가 page 단위로 row를 끌어오는 소스. writer가 0-based page와 page size를 전달하면
 * 구현체(보통 repository page 조회)가 해당 페이지를 반환하고, 더 이상 없으면 빈 리스트를 반환한다.
 *
 * <p>{@code Stream} 대신 명시적 page fetch를 써서 DB/JPA 메모리와 connection 점유를 bound한다.
 * writer를 Spring Data 타입에 결합시키지 않도록 page/size는 평범한 int로 노출한다.
 *
 * @param <T> export row 타입(projection DTO)
 */
@FunctionalInterface
public interface ExportRowSource<T> {

    List<T> fetch(int page, int pageSize);

    /**
     * 이미 materialize된 list를 page 단위로 노출하는 소스. stage/posting-scoped dataset처럼 기존 list
     * 쿼리를 재사용해 parity를 보장하는 export에 쓴다(대량 global dataset은 repository page 조회를 직접 쓴다).
     */
    static <T> ExportRowSource<T> ofList(List<T> rows) {
        List<T> snapshot = List.copyOf(rows);
        return (page, pageSize) -> {
            int fromIndex = page * pageSize;
            if (fromIndex >= snapshot.size()) {
                return List.of();
            }
            int toIndex = Math.min(fromIndex + pageSize, snapshot.size());
            return snapshot.subList(fromIndex, toIndex);
        };
    }
}
