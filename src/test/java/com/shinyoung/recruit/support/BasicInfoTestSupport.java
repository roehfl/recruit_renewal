package com.shinyoung.recruit.support;

import com.shinyoung.recruit.domain.entity.ApplicationBasicInfo;
import com.shinyoung.recruit.domain.entity.JobApplication;
import com.shinyoung.recruit.domain.repository.ApplicationBasicInfoRepository;
import com.shinyoung.recruit.enumeration.DisabilityStatus;
import com.shinyoung.recruit.enumeration.NationalityType;
import com.shinyoung.recruit.enumeration.VeteranStatus;

import java.time.LocalDate;

/**
 * 테스트 전용: submit 가능한 최소 유효 BasicInfo 를 리포지토리에 직접 시드한다.
 * 서비스 검증(쓰기 가능 기간)을 우회하므로 공고 상태/기간을 닫은 뒤에도 호출할 수 있다.
 * 이미 행이 있으면 no-op.
 */
public final class BasicInfoTestSupport {

    private BasicInfoTestSupport() {
    }

    public static void seedValidBasicInfo(ApplicationBasicInfoRepository repository, JobApplication application) {
        if (repository.existsByJobApplicationId(application.getId())) {
            return;
        }
        repository.save(ApplicationBasicInfo.create(
                application,
                "홍길동", null, NationalityType.DOMESTIC, null,
                LocalDate.of(1995, 1, 1), "01012345678", null, "test@example.com",
                VeteranStatus.NOT_SUBJECT, DisabilityStatus.NOT_SUBJECT,
                null, null, null, null, null));
    }
}
