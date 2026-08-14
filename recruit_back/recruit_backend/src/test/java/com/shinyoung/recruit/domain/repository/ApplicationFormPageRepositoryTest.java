package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.common.crypto.CryptoHolder;
import com.shinyoung.recruit.config.CryptoConfig;
import com.shinyoung.recruit.config.JpaConfig;
import com.shinyoung.recruit.domain.entity.ApplicationFormPage;
import com.shinyoung.recruit.domain.entity.ApplicationFormPageItem;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CryptoConfig.class, JpaConfig.class, CryptoHolder.class})
class ApplicationFormPageRepositoryTest {

    @Autowired
    private ApplicationFormPageRepository pageRepository;

    @Autowired
    private ApplicationFormPageItemRepository itemRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Test
    void saves_page_and_items_by_cascade() {
        JobPosting jobPosting = saveJobPosting();
        ApplicationFormPage page = ApplicationFormPage.create(jobPosting, 1, "Basic Info", null, 0);
        page.addItem(ApplicationSectionType.BASIC_INFO, 0);
        page.addItem(ApplicationSectionType.MILITARY, 1);

        ApplicationFormPage saved = pageRepository.saveAndFlush(page);

        List<ApplicationFormPageItem> items = itemRepository.findByPageIdOrderBySortOrderAscIdAsc(saved.getId());
        assertThat(items).extracting(ApplicationFormPageItem::getSectionType)
                .containsExactly(ApplicationSectionType.BASIC_INFO, ApplicationSectionType.MILITARY);
    }

    @Test
    void finds_pages_by_job_posting_ordered_by_sort_order() {
        JobPosting jobPosting = saveJobPosting();
        ApplicationFormPage later = pageRepository.save(page(jobPosting, 2, "Later", 2, ApplicationSectionType.ATTACHMENT));
        ApplicationFormPage earlier = pageRepository.save(page(jobPosting, 1, "Earlier", 1, ApplicationSectionType.BASIC_INFO));
        pageRepository.flush();

        List<ApplicationFormPage> pages = pageRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(jobPosting.getId());

        assertThat(pages).extracting(ApplicationFormPage::getId)
                .containsExactly(earlier.getId(), later.getId());
    }

    @Test
    void finds_pages_with_items() {
        JobPosting jobPosting = saveJobPosting();
        pageRepository.save(page(jobPosting, 1, "Basic Info", 0, ApplicationSectionType.BASIC_INFO));
        pageRepository.flush();

        List<ApplicationFormPage> pages = pageRepository.findByJobPostingIdWithItems(jobPosting.getId());

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).getItems()).hasSize(1);
    }

    @Test
    void exists_and_delete_by_job_posting_id() {
        JobPosting jobPosting = saveJobPosting();
        pageRepository.saveAndFlush(page(jobPosting, 1, "Basic Info", 0, ApplicationSectionType.BASIC_INFO));

        assertThat(pageRepository.existsByJobPostingId(jobPosting.getId())).isTrue();

        pageRepository.deleteByJobPostingId(jobPosting.getId());
        pageRepository.flush();

        assertThat(pageRepository.existsByJobPostingId(jobPosting.getId())).isFalse();
    }

    private ApplicationFormPage page(
            JobPosting jobPosting,
            int pageNo,
            String title,
            int sortOrder,
            ApplicationSectionType sectionType
    ) {
        ApplicationFormPage page = ApplicationFormPage.create(jobPosting, pageNo, title, null, sortOrder);
        page.addItem(sectionType, 0);
        return page;
    }

    private JobPosting saveJobPosting() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 9, 0);
        return jobPostingRepository.saveAndFlush(JobPosting.create("Posting", "Content", now, now.plusDays(10)));
    }
}
