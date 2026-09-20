package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Notice;
import com.shinyoung.recruit.domain.repository.NoticeRepository;
import com.shinyoung.recruit.domain.repository.NoticeSpecification;
import com.shinyoung.recruit.dto.request.NoticeSaveRequest;
import com.shinyoung.recruit.dto.response.AdminNoticeListResponse;
import com.shinyoung.recruit.dto.response.NoticeListResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.enumeration.NoticeSearchType;
import com.shinyoung.recruit.exception.NoticeNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NoticeService {
    private final NoticeRepository noticeRepository;


    public NoticeService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    public PageResponse<NoticeListResponse> getNotices(int page, int size, NoticeSearchType searchType, String keyword) {
        Page<Notice> result = noticeRepository.findAll(NoticeSpecification.search(searchType, keyword), pageable(page, size));
        Page<NoticeListResponse> response = result.map(NoticeListResponse::from);
        return PageResponse.from(response);
    }

    public PageResponse<AdminNoticeListResponse> getAdminNotices(
            int page,
            int size,
            NoticeSearchType searchType,
            String keyword,
            Boolean deleted,
            boolean pinnedOnly
    ) {
        Page<Notice> result = noticeRepository.findAll(
                NoticeSpecification.adminSearch(searchType, keyword, deleted, pinnedOnly), pageable(page, size));
        Page<AdminNoticeListResponse> response = result.map(AdminNoticeListResponse::from);
        return PageResponse.from(response);
    }

    @Transactional
    public Long create(NoticeSaveRequest request) {
        Notice notice = noticeRepository.save(Notice.create(
                request.title(),
                request.content(),
                request.isPinned()));
        return notice.getId();
    }

    @Transactional
    public Long update(Long targetId, NoticeSaveRequest request) {
        Notice notice = getAdminNotice(targetId);
        notice.update(request.title(), request.content(), request.isPinned());
        return notice.getId();
    }

    /** soft delete. 이미 삭제된 공지면 그대로 둔다(멱등). */
    @Transactional
    public void delete(Long targetId) {
        getAdminNotice(targetId).delete();
    }

    @Transactional
    public void restore(Long targetId) {
        getAdminNotice(targetId).restore();
    }

    /** 상단 고정만 바꾼다. 본문을 다시 보내지 않아도 되도록 수정과 분리했다. */
    @Transactional
    public void changePinned(Long targetId, boolean pinned) {
        getAdminNotice(targetId).changePinned(pinned);
    }

    /** 지원자 공개 상세. 삭제된 공지는 없는 것으로 본다. */
    public Notice getNotice(Long targetId) {
        Notice notice = getAdminNotice(targetId);
        if (notice.isDeleted()) {
            throw new NoticeNotFoundException("Id에 해당하는 공지를 찾을 수 없습니다.");
        }
        return notice;
    }

    /** 관리자 상세. 삭제된 공지도 조회된다. */
    public Notice getAdminNotice(Long targetId) {
        return noticeRepository.findById(targetId).orElseThrow(() -> new NoticeNotFoundException("Id에 해당하는 공지를 찾을 수 없습니다."));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt")));
    }
}
