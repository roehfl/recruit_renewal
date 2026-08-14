package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.ApplicationFormPage;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ApplicationFormLayoutDefaultFactory {

    public List<ApplicationFormPage> createDefaultLayout(
            JobPosting jobPosting,
            Set<ApplicationSectionType> effectiveEnabledSections
    ) {
        if (jobPosting == null) {
            throw new IllegalArgumentException("Job posting is required.");
        }
        if (effectiveEnabledSections == null || effectiveEnabledSections.isEmpty()) {
            throw new IllegalArgumentException("Effective enabled sections are required.");
        }

        List<ApplicationFormPage> pages = new ArrayList<>();
        addPageIfNeeded(pages, jobPosting, "Basic Info", effectiveEnabledSections, List.of(
                ApplicationSectionType.BASIC_INFO,
                ApplicationSectionType.MILITARY
        ));
        addPageIfNeeded(pages, jobPosting, "Education And Career", effectiveEnabledSections, List.of(
                ApplicationSectionType.EDUCATION,
                ApplicationSectionType.CAREER
        ));
        addPageIfNeeded(pages, jobPosting, "Additional Records", effectiveEnabledSections, List.of(
                ApplicationSectionType.CERTIFICATE,
                ApplicationSectionType.LANGUAGE,
                ApplicationSectionType.AWARD,
                ApplicationSectionType.GAP_PERIOD
        ));
        addPageIfNeeded(pages, jobPosting, "Question Answer", effectiveEnabledSections, List.of(
                ApplicationSectionType.QUESTION_ANSWER
        ));
        addPageIfNeeded(pages, jobPosting, "Attachment", effectiveEnabledSections, List.of(
                ApplicationSectionType.ATTACHMENT
        ));
        return pages;
    }

    private void addPageIfNeeded(
            List<ApplicationFormPage> pages,
            JobPosting jobPosting,
            String title,
            Set<ApplicationSectionType> effectiveEnabledSections,
            List<ApplicationSectionType> candidates
    ) {
        List<ApplicationSectionType> sections = candidates.stream()
                .filter(effectiveEnabledSections::contains)
                .toList();
        if (sections.isEmpty()) {
            return;
        }

        int pageIndex = pages.size();
        ApplicationFormPage page = ApplicationFormPage.create(
                jobPosting,
                pageIndex + 1,
                title,
                null,
                pageIndex
        );
        for (int index = 0; index < sections.size(); index++) {
            ApplicationSectionType sectionType = Objects.requireNonNull(sections.get(index));
            page.addItem(sectionType, index);
        }
        pages.add(page);
    }
}
