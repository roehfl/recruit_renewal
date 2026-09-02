package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.JobPostingStatus;

import java.time.LocalDateTime;

/**
 * 지원서 설정(양식·폼 구성)을 수정할 수 있는 구간.
 * 잠그는 이유는 이미 제출된 지원서와 어긋나지 않게 하기 위함이며, 지원서는 접수 시작 후에만 생긴다.
 * 따라서 작성 중(DRAFT) 공고는 접수 시작일이 지났더라도 수정할 수 있다.
 * 규칙이 여러 서비스에 흩어져 어긋나지 않도록 이 클래스를 단일 출처로 둔다.
 */
public final class ApplicationFormEditWindow {

    private ApplicationFormEditWindow() {
    }

    public static boolean isEditable(JobPosting jobPosting, LocalDateTime now) {
        if (jobPosting.getStatus() == JobPostingStatus.CLOSED) {
            return false;
        }
        if (jobPosting.getStatus() == JobPostingStatus.DRAFT) {
            return true;
        }
        return now.isBefore(jobPosting.getReceptionStartDateTime());
    }
}
