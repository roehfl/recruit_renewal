package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.Notice;
import com.shinyoung.recruit.domain.repository.NoticeRepository;
import com.shinyoung.recruit.domain.repository.NoticeSpecification;
import com.shinyoung.recruit.dto.request.NoticeSaveRequest;
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
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt")));
        Page<Notice> result = noticeRepository.findAll(NoticeSpecification.search(searchType, keyword), pageable);
        Page<NoticeListResponse> response = result.map(NoticeListResponse::from);
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

    public Notice getNotice(Long targetId) {
        return noticeRepository.findById(targetId).orElseThrow(() -> new NoticeNotFoundException("Id에 해당하는 공지를 찾을 수 없습니다."));
    }
}
