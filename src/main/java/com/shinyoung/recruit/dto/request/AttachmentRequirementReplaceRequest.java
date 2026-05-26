package com.shinyoung.recruit.dto.request;

import java.util.List;

public record AttachmentRequirementReplaceRequest(
        List<AttachmentRequirementRequest> requirements
) {
}
