package com.shinyoung.recruit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모집분야(JobPosition)가 제시하는 후보 근무지 1건.
 *
 * <p>{@code code} 는 CommonCode 그룹 {@code WORK_LOCATION} 의 코드이고, {@code name} 은 공고 저장 시점의
 * {@code displayName} 스냅샷이다(공고는 시점 문서라 라벨을 굳혀 둔다 — 지원서의 snapshot 규약과 동일).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPositionWorkLocation {

    @Column(name = "work_location_code", nullable = false, length = 100)
    private String code;

    @Column(name = "work_location_name", nullable = false, length = 200)
    private String name;

    private JobPositionWorkLocation(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static JobPositionWorkLocation of(String code, String name) {
        return new JobPositionWorkLocation(code, name);
    }
}
