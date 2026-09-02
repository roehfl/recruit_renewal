package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.AdminApplicationFormSummarySearchRequest;
import com.shinyoung.recruit.dto.response.AdminApplicationFormSummaryResponse;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.PageResponse;
import com.shinyoung.recruit.service.AdminApplicationFormSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/application-forms")
public class AdminApplicationFormController {

    private final AdminApplicationFormSummaryService summaryService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminApplicationFormSummaryResponse>>> getSummaries(
            @ModelAttribute AdminApplicationFormSummarySearchRequest searchRequest,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(summaryService.getSummaries(searchRequest, page, size)));
    }
}
