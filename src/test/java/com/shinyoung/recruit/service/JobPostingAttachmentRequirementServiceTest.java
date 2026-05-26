package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.repository.JobPostingAttachmentRequirementRepository;
import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.AttachmentRequirementReplaceRequest;
import com.shinyoung.recruit.dto.request.AttachmentRequirementRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.response.JobPostingAttachmentRequirementResponse;
import com.shinyoung.recruit.enumeration.ApplicationSectionType;
import com.shinyoung.recruit.enumeration.AttachmentType;
import com.shinyoung.recruit.enumeration.EmploymentType;
import com.shinyoung.recruit.enumeration.JobPositionApplicationType;
import com.shinyoung.recruit.enumeration.JobPostingType;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class JobPostingAttachmentRequirementServiceTest {

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingAttachmentRequirementService requirementService;

    @Autowired
    private JobPostingAttachmentRequirementRepository requirementRepository;

    @Test
    void replace_all_saves_sorted_requirements_and_normalizes_values() {
        Long postingId = jobPostingService.create(request("draft"));

        List<JobPostingAttachmentRequirementResponse> response = requirementService.replaceRequirements(
                postingId,
                new AttachmentRequirementReplaceRequest(List.of(
                        new AttachmentRequirementRequest(
                                AttachmentType.PORTFOLIO,
                                ApplicationSectionType.APPLICATION,
                                false,
                                null,
                                null,
                                " Portfolio ",
                                "  optional file  "
                        ),
                        new AttachmentRequirementRequest(
                                AttachmentType.RESUME,
                                ApplicationSectionType.APPLICATION,
                                true,
                                null,
                                null,
                                "Resume",
                                " "
                        )
                ))
        );

        assertThat(response)
                .extracting(JobPostingAttachmentRequirementResponse::displayName)
                .containsExactly("Portfolio", "Resume");
        assertThat(response.get(0).required()).isFalse();
        assertThat(response.get(0).minCount()).isZero();
        assertThat(response.get(0).description()).isEqualTo("optional file");
        assertThat(response.get(1).required()).isTrue();
        assertThat(response.get(1).minCount()).isEqualTo(1);
        assertThat(response.get(1).description()).isNull();
        assertThat(requirementRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(postingId)).hasSize(2);
    }

    @Test
    void replace_all_removes_previous_rows() {
        Long postingId = jobPostingService.create(request("draft"));
        requirementService.replaceRequirements(
                postingId,
                new AttachmentRequirementReplaceRequest(List.of(requirement(AttachmentType.RESUME, true, 1)))
        );

        requirementService.replaceRequirements(
                postingId,
                new AttachmentRequirementReplaceRequest(List.of(requirement(AttachmentType.PORTFOLIO, false, 0)))
        );

        assertThat(requirementRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(postingId))
                .extracting(requirement -> requirement.getAttachmentType())
                .containsExactly(AttachmentType.PORTFOLIO);
    }

    @Test
    void duplicate_type_and_section_fails() {
        Long postingId = jobPostingService.create(request("draft"));

        assertThatThrownBy(() -> requirementService.replaceRequirements(
                postingId,
                new AttachmentRequirementReplaceRequest(List.of(
                        requirement(AttachmentType.RESUME, true, 1),
                        requirement(AttachmentType.RESUME, false, 0)
                ))
        )).isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void invalid_rows_fail() {
        Long postingId = jobPostingService.create(request("draft"));

        assertThatThrownBy(() -> requirementService.replaceRequirements(
                postingId,
                new AttachmentRequirementReplaceRequest(List.of(
                        new AttachmentRequirementRequest(null, ApplicationSectionType.APPLICATION, true, 1, 0, "Resume", null)
                ))
        )).isInstanceOf(InvalidJobPostingException.class);

        assertThatThrownBy(() -> requirementService.replaceRequirements(
                postingId,
                new AttachmentRequirementReplaceRequest(List.of(
                        new AttachmentRequirementRequest(AttachmentType.RESUME, null, true, 1, 0, "Resume", null)
                ))
        )).isInstanceOf(InvalidJobPostingException.class);

        assertThatThrownBy(() -> requirementService.replaceRequirements(
                postingId,
                new AttachmentRequirementReplaceRequest(List.of(
                        new AttachmentRequirementRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, true, 0, 0, "Resume", null)
                ))
        )).isInstanceOf(InvalidJobPostingException.class);

        assertThatThrownBy(() -> requirementService.replaceRequirements(
                postingId,
                new AttachmentRequirementReplaceRequest(List.of(
                        new AttachmentRequirementRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, false, -1, 0, "Resume", null)
                ))
        )).isInstanceOf(InvalidJobPostingException.class);

        assertThatThrownBy(() -> requirementService.replaceRequirements(
                postingId,
                new AttachmentRequirementReplaceRequest(List.of(
                        new AttachmentRequirementRequest(AttachmentType.RESUME, ApplicationSectionType.APPLICATION, true, 1, 0, " ", null)
                ))
        )).isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void replace_fails_when_posting_is_published() {
        Long postingId = jobPostingService.create(request("published"));
        jobPostingService.publish(postingId);

        assertThatThrownBy(() -> requirementService.replaceRequirements(
                postingId,
                new AttachmentRequirementReplaceRequest(List.of(requirement(AttachmentType.RESUME, true, 1)))
        )).isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void null_request_is_treated_as_empty_replace() {
        Long postingId = jobPostingService.create(request("draft"));
        requirementService.replaceRequirements(
                postingId,
                new AttachmentRequirementReplaceRequest(List.of(requirement(AttachmentType.RESUME, true, 1)))
        );

        List<JobPostingAttachmentRequirementResponse> response = requirementService.replaceRequirements(postingId, null);

        assertThat(response).isEmpty();
        assertThat(requirementRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(postingId)).isEmpty();
    }

    private AttachmentRequirementRequest requirement(AttachmentType attachmentType, boolean required, int minCount) {
        return new AttachmentRequirementRequest(
                attachmentType,
                ApplicationSectionType.APPLICATION,
                required,
                minCount,
                0,
                attachmentType.name(),
                null
        );
    }

    private JobPostingCreateRequest request(String title) {
        return new JobPostingCreateRequest(
                title,
                JobPostingType.PUBLIC_RECRUITMENT,
                title + " summary",
                "<p>content</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                LocalDateTime.of(2026, 5, 25, 9, 0),
                LocalDateTime.of(2026, 7, 1, 18, 0),
                true,
                false,
                0,
                List.of(new JobPositionRequest(
                        "Backend",
                        JobPositionApplicationType.EXPERIENCED,
                        "IT",
                        "Backend Engineer",
                        "Seoul",
                        EmploymentType.FULL_TIME,
                        1,
                        0
                )),
                new ApplicationFormConfigRequest(true, false, true, false, true, false, false)
        );
    }
}
