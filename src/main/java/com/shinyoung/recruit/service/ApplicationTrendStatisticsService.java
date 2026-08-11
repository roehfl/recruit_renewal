package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.repository.JobApplicationRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.response.ApplicationDailyCountRow;
import com.shinyoung.recruit.dto.response.ApplicationDailyPointResponse;
import com.shinyoung.recruit.dto.response.ApplicationDailyResponse;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 공고 단위 일자별 지원 접수 추이 산출(read-only, 집계값만).
 *
 * <p>funnel과 관심사가 달라(코호트×stage 교차 집계 vs 시계열) 별도 서비스로 둔다. 집계는 DB {@code GROUP BY}
 * 한 번이며 지원서 전체를 메모리에 올리지 않는다. statistics는 audit를 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ApplicationTrendStatisticsService {

    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;

    public ApplicationDailyResponse getDailySubmissions(Long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + jobPostingId));

        LocalDate from = jobPosting.getReceptionStartDateTime().toLocalDate();
        LocalDate to = resolveRangeEnd(jobPosting, from);

        Map<LocalDate, Long> countByDate = new HashMap<>();
        long outOfRangeCount = 0;

        for (ApplicationDailyCountRow row : jobApplicationRepository.findDailySubmittedCounts(jobPostingId)) {
            /*
             * 접수 구간 밖 제출은 데이터 이상이다. 양 끝으로 clamp 하면 그날 값이 부풀어 차트가 거짓말을 하므로
             * 집계에서 제외하고 건수만 로그로 남긴다.
             */
            if (row.date().isBefore(from) || row.date().isAfter(to)) {
                outOfRangeCount += row.submittedCount();
                continue;
            }
            countByDate.put(row.date(), row.submittedCount());
        }

        if (outOfRangeCount > 0) {
            log.warn("공고 접수 구간 밖 제출 {}건을 일자별 추이 집계에서 제외했다. jobPostingId={}, range={}~{}",
                    outOfRangeCount, jobPostingId, from, to);
        }

        List<ApplicationDailyPointResponse> days = new ArrayList<>();
        long cumulative = 0;

        // 제출이 없던 날도 0으로 채운다. 비우면 라인 차트가 끊기고 x축 간격이 왜곡된다.
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            long submittedCount = countByDate.getOrDefault(date, 0L);
            cumulative += submittedCount;
            days.add(new ApplicationDailyPointResponse(date, submittedCount, cumulative));
        }

        return new ApplicationDailyResponse(
                jobPostingId,
                jobPosting.getTitle(),
                from,
                to,
                cumulative,
                days
        );
    }

    /**
     * 구간 종료일은 접수 종료일과 오늘 중 이른 날이다. 아직 접수 시작 전이면 시작일 하루짜리 구간을 반환해
     * 빈 배열 대신 0인 점 하나를 준다(차트가 축을 그릴 수 있어야 한다).
     */
    private LocalDate resolveRangeEnd(JobPosting jobPosting, LocalDate from) {
        LocalDate receptionEnd = jobPosting.getReceptionEndDateTime().toLocalDate();
        LocalDate today = LocalDate.now();
        LocalDate end = receptionEnd.isBefore(today) ? receptionEnd : today;

        return end.isBefore(from) ? from : end;
    }
}
