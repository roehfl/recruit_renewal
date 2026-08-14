package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.MenuSaveRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.MenuIdResponse;
import com.shinyoung.recruit.dto.response.MenuResponse;
import com.shinyoung.recruit.enumeration.MenuSite;
import com.shinyoung.recruit.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/menu")
public class MenuController {
    private final MenuService menuService;

    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<MenuResponse>>> getMenuTree(
            @RequestParam(defaultValue = "APPLICANT") MenuSite site
    ) {
        return ResponseEntity.ok(ApiResponse.success(menuService.getTree(site)));
    }

    @GetMapping("/{menuId}")
    public ResponseEntity<ApiResponse<MenuResponse>> getMenu(@PathVariable Long menuId) {
        return ResponseEntity.ok(ApiResponse.success(menuService.get(menuId)));
    }

    @GetMapping("/breadcrumb")
    public ResponseEntity<ApiResponse<List<MenuResponse>>> getBreadcrumb(
            @RequestParam(defaultValue = "APPLICANT") MenuSite site,
            @RequestParam String path
    ) {
        return ResponseEntity.ok(ApiResponse.success(menuService.getBreadcrumb(site, path)));
    }

    @PostMapping("/admin/menu")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<MenuIdResponse>> createMenu(@Valid @RequestBody MenuSaveRequest request) {
        Long menuId = menuService.create(request);
        return ResponseEntity.ok(ApiResponse.success(new MenuIdResponse(menuId)));
    }

    @PostMapping("/admin/menu/{menuId}")
    public ResponseEntity<ApiResponse<MenuIdResponse>> updateMenu(
            @PathVariable Long menuId,
            @Valid @RequestBody MenuSaveRequest request
    ) {
        Long menuIdResult = menuService.update(menuId, request);
        return ResponseEntity.ok(ApiResponse.success(new MenuIdResponse(menuIdResult)));
    }
}
