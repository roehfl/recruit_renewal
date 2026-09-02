package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.JobPosition;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.JobPositionWorkLocation;
import com.shinyoung.recruit.domain.repository.CommonCodeRepository;
import com.shinyoung.recruit.domain.repository.JobPositionCountProjection;
import com.shinyoung.recruit.domain.repository.JobPositionRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingImageMetaRequest;
import com.shinyoung.recruit.dto.request.JobPostingUpdateRequest;
import com.shinyoung.recruit.dto.response.JobPostingDetailResponse;
import com.shinyoung.recruit.dto.response.JobPostingListResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.enumeration.JobPostingType;
import com.shinyoung.recruit.exception.InvalidApplicationFormLayoutException;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingService {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final int SUMMARY_MAX_LENGTH = 500;
    private static final int JOB_POSITION_TEXT_MAX_LENGTH = 100;
    /** 근무지 후보를 담는 CommonCode 그룹. */
    private static final String WORK_LOCATION_GROUP_CODE = "WORK_LOCATION";

    private final JobPostingRepository jobPostingRepository;
    private final JobPositionRepository jobPositionRepository;
    private final CommonCodeRepository commonCodeRepository;
    private final ApplicationFormLayoutService applicationFormLayoutService;
    private final ApplicationFormConfigService applicationFormConfigService;
    private final JobPostingImageService jobPostingImageService;
    private final Clock clock;

    public PageResponse<JobPostingListResponse> getJobPostings(int page, int size) {
        validatePageRequest(page, size);
        LocalDateTime now = LocalDateTime.now(clock);
        Page<JobPosting> result = jobPostingRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        Map<Long, Long> positionCounts = getPositionCounts(result.getContent());
        return PageResponse.from(result.map(jobPosting -> JobPostingListResponse.from(
                jobPosting,
                now,
                positionCounts.getOrDefault(jobPosting.getId(), 0L).intValue()
        )));
    }

    public JobPostingDetailResponse getJobPosting(Long id) {
        JobPosting jobPosting = findJobPostingDetail(id);
        return JobPostingDetailResponse.from(
                jobPosting,
                LocalDateTime.now(clock),
                jobPostingImageService.getImages(id)
        );
    }

    @Transactional
    public Long create(JobPostingCreateRequest request) {
        validateRequest(request);

        JobPosting jobPosting = JobPosting.create(
                request.title(),
                defaultPostingType(request.postingType()),
                request.summary(),
                request.contentHtml(),
                request.receptionStartDateTime(),
                request.receptionEndDateTime(),
                request.displayStartDateTime(),
                request.displayEndDateTime(),
                defaultVisible(request.visible()),
                defaultPinned(request.pinned()),
                defaultDisplayOrder(request.displayOrder())
        );

        jobPosting.replaceJobPositions(toJobPositions(request.jobPositions()));
        jobPosting.updateApplicationFormConfig(applicationFormConfigService.createFrom(request.applicationFormConfig()));

        JobPosting saved = jobPostingRepository.save(jobPosting);
        return saved.getId();
    }

    @Transactional
    public Long create(
            JobPostingCreateRequest request,
            List<JobPostingImageMetaRequest> imageMetas,
            List<MultipartFile> imageFiles
    ) {
        Long id = create(request);
        jobPostingImageService.createImages(id, imageMetas, imageFiles);
        return id;
    }

    @Transactional
    public Long update(Long id, JobPostingUpdateRequest request) {
        validateRequest(request);

        JobPosting jobPosting = findJobPosting(id);
        if (jobPosting.getStatus() == JobPostingStatus.CLOSED) {
            throw new InvalidJobPostingException("마감된 공고는 수정할 수 없습니다.");
        }

        jobPosting.updateBasicInfo(
                request.title(),
                defaultPostingType(request.postingType()),
                request.summary(),
                request.contentHtml(),
                request.receptionStartDateTime(),
                request.receptionEndDateTime(),
                request.displayStartDateTime(),
                request.displayEndDateTime(),
                defaultVisible(request.visible()),
                defaultPinned(request.pinned()),
                defaultDisplayOrder(request.displayOrder())
        );
        jobPosting.replaceJobPositions(toJobPositions(request.jobPositions()));
        // 지원서 양식은 전용 API(ApplicationFormConfigService)가 단일 출처다. 공고 수정은 건드리지 않는다.

        return jobPosting.getId();
    }

    @Transactional
    public Long publish(Long id) {
        JobPosting jobPosting = findJobPosting(id);

        if (jobPosting.getStatus() == JobPostingStatus.CLOSED) {
            throw new InvalidJobPostingException("마감된 공고는 다시 게시할 수 없습니다.");
        }
        if (jobPosting.getStatus() == JobPostingStatus.PUBLISHED) {
            throw new InvalidJobPostingException("이미 게시된 공고입니다.");
        }
        validateReceptionPeriod(jobPosting.getReceptionStartDateTime(), jobPosting.getReceptionEndDateTime());
        validateJobPositions(jobPosting.getJobPositions());
        validateLayoutForPublish(jobPosting);
        validateContentForPublish(jobPosting);

        jobPosting.publish(LocalDateTime.now(clock));
        return jobPosting.getId();
    }

    @Transactional
    public Long close(Long id) {
        JobPosting jobPosting = findJobPosting(id);

        if (jobPosting.getStatus() != JobPostingStatus.PUBLISHED) {
            throw new InvalidJobPostingException("게시 상태의 공고만 마감할 수 있습니다.");
        }

        jobPosting.close(LocalDateTime.now(clock));
        return jobPosting.getId();
    }

    private void validateLayoutForPublish(JobPosting jobPosting) {
        try {
            applicationFormLayoutService.validateLayoutForPublish(jobPosting);
        } catch (InvalidApplicationFormLayoutException e) {
            throw new InvalidJobPostingException("레이아웃 검증 실패: " + e.getMessage());
        }
    }

    /** 발행 조건: 이미지 ≥1장 또는 (레거시 데이터 호환) contentHtml 존재. */
    private void validateContentForPublish(JobPosting jobPosting) {
        boolean hasImages = jobPostingImageService.countImages(jobPosting.getId()) > 0;
        boolean hasLegacyContent = jobPosting.getContentHtml() != null && !jobPosting.getContentHtml().isBlank();
        if (!hasImages && !hasLegacyContent) {
            throw new InvalidJobPostingException("공고 본문 이미지가 최소 1장 필요합니다.");
        }
    }

    private JobPosting findJobPosting(Long id) {
        return jobPostingRepository.findById(id)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + id));
    }

    private JobPosting findJobPostingDetail(Long id) {
        return jobPostingRepository.findDetailById(id)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + id));
    }

    private Map<Long, Long> getPositionCounts(List<JobPosting> jobPostings) {
        List<Long> ids = jobPostings.stream()
                .map(JobPosting::getId)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jobPositionRepository.countByJobPostingIds(ids).stream()
                .collect(Collectors.toMap(
                        JobPositionCountProjection::getJobPostingId,
                        JobPositionCountProjection::getPositionCount
                ));
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0) {
            throw new InvalidJobPostingException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new InvalidJobPostingException("페이지 크기는 1 이상 100 이하이어야 합니다.");
        }
    }

    private void validateRequest(JobPostingCreateRequest request) {
        validateRequest(
                request.title(),
                request.summary(),
                request.receptionStartDateTime(),
                request.receptionEndDateTime(),
                request.displayStartDateTime(),
                request.displayEndDateTime(),
                request.displayOrder(),
                request.jobPositions()
        );
    }

    private void validateRequest(JobPostingUpdateRequest request) {
        validateRequest(
                request.title(),
                request.summary(),
                request.receptionStartDateTime(),
                request.receptionEndDateTime(),
                request.displayStartDateTime(),
                request.displayEndDateTime(),
                request.displayOrder(),
                request.jobPositions()
        );
    }

    private void validateRequest(
            String title,
            String summary,
            LocalDateTime receptionStart,
            LocalDateTime receptionEnd,
            LocalDateTime displayStart,
            LocalDateTime displayEnd,
            Integer displayOrder,
            List<JobPositionRequest> jobPositions
    ) {
        if (title == null || title.isBlank()) {
            throw new InvalidJobPostingException("공고 제목은 필수입니다.");
        }
        validateSummary(summary);
        validateDisplayOrder(displayOrder);
        validateReceptionPeriod(receptionStart, receptionEnd);
        validateDisplayPeriod(displayStart, displayEnd);
        validateJobPositions(jobPositions);
        validateJobPositionSortOrders(jobPositions);
    }

    private void validateReceptionPeriod(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new InvalidJobPostingException("접수 종료일시는 시작일시 이후여야 합니다.");
        }
    }

    private void validateDisplayPeriod(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new InvalidJobPostingException("노출 종료일시는 시작일시 이후이거나 같아야 합니다.");
        }
    }

    private void validateSummary(String summary) {
        if (summary != null && summary.length() > SUMMARY_MAX_LENGTH) {
            throw new InvalidJobPostingException("공고 요약은 500자 이하이어야 합니다.");
        }
        if (summary != null && HTML_TAG_PATTERN.matcher(summary).find()) {
            throw new InvalidJobPostingException("공고 요약에는 HTML 태그를 사용할 수 없습니다.");
        }
    }

    private void validateDisplayOrder(Integer displayOrder) {
        if (displayOrder != null && displayOrder < 0) {
            throw new InvalidJobPostingException("공고 표시 순서는 0 이상이어야 합니다.");
        }
    }

    private void validateJobPositions(List<?> jobPositions) {
        if (jobPositions == null || jobPositions.isEmpty()) {
            throw new InvalidJobPostingException("모집분야는 최소 1개 이상이어야 합니다.");
        }
    }

    private void validateJobPosition(JobPositionRequest request) {
        if (request.positionName() == null || request.positionName().isBlank()) {
            throw new InvalidJobPostingException("모집분야명은 필수입니다.");
        }
        validateMaxLength(request.positionName(), JOB_POSITION_TEXT_MAX_LENGTH, "모집분야명");
        validateMaxLength(request.jobTitle(), JOB_POSITION_TEXT_MAX_LENGTH, "담당 직무명");
        validateWorkLocationCodes(request.workLocationCodes());
        if (request.sortOrder() == null || request.sortOrder() < 0) {
            throw new InvalidJobPostingException("모집분야 정렬 순서는 0 이상이어야 합니다.");
        }
    }

    private void validateMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new InvalidJobPostingException(fieldName + "은 " + maxLength + "자 이하이어야 합니다.");
        }
    }

    /** 후보 근무지는 비어 있어도 되지만(근무지 선택 없는 모집분야), 있으면 중복이 없어야 한다. */
    private void validateWorkLocationCodes(List<String> workLocationCodes) {
        if (workLocationCodes == null) {
            return;
        }
        Set<String> codes = new HashSet<>();
        for (String code : workLocationCodes) {
            if (code == null || code.isBlank()) {
                throw new InvalidJobPostingException("근무지 코드는 비어 있을 수 없습니다.");
            }
            if (!codes.add(code)) {
                throw new InvalidJobPostingException("근무지는 중복될 수 없습니다. code=" + code);
            }
        }
    }

    private void validateJobPositionSortOrders(List<JobPositionRequest> jobPositions) {
        Set<Integer> sortOrders = new HashSet<>();
        for (JobPositionRequest jobPosition : jobPositions) {
            validateJobPosition(jobPosition);
            if (jobPosition.sortOrder() != null && !sortOrders.add(jobPosition.sortOrder())) {
                throw new InvalidJobPostingException("모집분야 정렬 순서는 중복될 수 없습니다.");
            }
        }
    }

    private List<JobPosition> toJobPositions(List<JobPositionRequest> requests) {
        Map<String, String> workLocationNames = loadWorkLocationNames();
        return requests.stream()
                .map(it -> JobPosition.create(
                        it.positionName(),
                        defaultApplicationType(it.applicationType()),
                        it.jobTitle(),
                        toWorkLocations(it.workLocationCodes(), workLocationNames),
                        defaultEmploymentType(it.employmentType()),
                        it.sortOrder()
                ))
                .toList();
    }

    /** 활성 근무지 코드 → 표시명. 공고 저장 1회당 한 번만 조회한다. */
    private Map<String, String> loadWorkLocationNames() {
        Map<String, String> names = new LinkedHashMap<>();
        commonCodeRepository.findByGroupCodeAndActiveTrueOrderBySortOrderAscIdAsc(WORK_LOCATION_GROUP_CODE)
                .forEach(it -> names.put(it.getCode(), it.getDisplayName()));
        return names;
    }

    private List<JobPositionWorkLocation> toWorkLocations(
            List<String> workLocationCodes,
            Map<String, String> workLocationNames
    ) {
        if (workLocationCodes == null || workLocationCodes.isEmpty()) {
            return List.of();
        }
        return workLocationCodes.stream()
                .map(code -> {
                    String name = workLocationNames.get(code);
                    if (name == null) {
                        throw new InvalidJobPostingException("등록되지 않은 근무지 코드입니다. code=" + code);
                    }
                    return JobPositionWorkLocation.of(code, name);
                })
                .toList();
    }

    private JobPostingType defaultPostingType(JobPostingType postingType) {
        return postingType == null ? JobPostingType.PUBLIC_RECRUITMENT : postingType;
    }

    private Boolean defaultVisible(Boolean visible) {
        return visible == null || visible;
    }

    private Boolean defaultPinned(Boolean pinned) {
        return pinned != null && pinned;
    }

    private Integer defaultDisplayOrder(Integer displayOrder) {
        return displayOrder == null ? 0 : displayOrder;
    }

    private JobPositionApplicationType defaultApplicationType(JobPositionApplicationType applicationType) {
        return applicationType == null ? JobPositionApplicationType.NEW_GRADUATE_OR_EXPERIENCED : applicationType;
    }

    private EmploymentType defaultEmploymentType(EmploymentType employmentType) {
        return employmentType == null ? EmploymentType.FULL_TIME : employmentType;
    }
}
