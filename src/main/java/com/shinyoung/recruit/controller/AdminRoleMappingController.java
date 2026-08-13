package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.DeptRoleMappingSaveRequest;
import com.shinyoung.recruit.dto.request.UserRoleMappingSaveRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.dto.response.AssignableRoleResponse;
import com.shinyoung.recruit.dto.response.DeptRoleMappingResponse;
import com.shinyoung.recruit.dto.response.RoleMappingIdResponse;
import com.shinyoung.recruit.dto.response.UserRoleMappingResponse;
import com.shinyoung.recruit.service.RoleMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 권한 관리 API. 경로가 전부 {@code /api/admin/role-mappings/**}라
 * SecurityConfig의 broad {@code /api/admin/**} 매처(ROLE_ADMIN, ROLE_RECRUIT_ADMIN)에 걸린다 —
 * 전용 매처 불필요. 인가는 SecurityConfigTest로 고정한다.
 *
 * <p>레포 관례에 따라 수정·삭제도 POST를 쓴다(DELETE 동사 미사용).
 */
@RestController
@RequiredArgsConstructor
public class AdminRoleMappingController {

    private final RoleMappingService roleMappingService;

    @GetMapping("/admin/role-mappings/roles")
    public ResponseEntity<ApiResponse<List<AssignableRoleResponse>>> getAssignableRoles() {
        return ResponseEntity.ok(ApiResponse.success(roleMappingService.getAssignableRoles()));
    }

    @GetMapping("/admin/role-mappings/dept")
    public ResponseEntity<ApiResponse<List<DeptRoleMappingResponse>>> getDeptMappings() {
        return ResponseEntity.ok(ApiResponse.success(roleMappingService.getDeptMappings()));
    }

    @PostMapping("/admin/role-mappings/dept")
    public ResponseEntity<ApiResponse<RoleMappingIdResponse>> createDeptMapping(
            @Valid @RequestBody DeptRoleMappingSaveRequest request
    ) {
        Long id = roleMappingService.createDeptMapping(request);
        return ResponseEntity.ok(ApiResponse.success(new RoleMappingIdResponse(id)));
    }

    @PostMapping("/admin/role-mappings/dept/{id}")
    public ResponseEntity<ApiResponse<RoleMappingIdResponse>> updateDeptMapping(
            @PathVariable Long id,
            @Valid @RequestBody DeptRoleMappingSaveRequest request
    ) {
        Long updatedId = roleMappingService.updateDeptMapping(id, request);
        return ResponseEntity.ok(ApiResponse.success(new RoleMappingIdResponse(updatedId)));
    }

    @PostMapping("/admin/role-mappings/dept/{id}/delete")
    public ResponseEntity<ApiResponse<Void>> deleteDeptMapping(@PathVariable Long id) {
        roleMappingService.deleteDeptMapping(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/admin/role-mappings/user")
    public ResponseEntity<ApiResponse<List<UserRoleMappingResponse>>> getUserMappings() {
        return ResponseEntity.ok(ApiResponse.success(roleMappingService.getUserMappings()));
    }

    @PostMapping("/admin/role-mappings/user")
    public ResponseEntity<ApiResponse<RoleMappingIdResponse>> createUserMapping(
            @Valid @RequestBody UserRoleMappingSaveRequest request
    ) {
        Long id = roleMappingService.createUserMapping(request);
        return ResponseEntity.ok(ApiResponse.success(new RoleMappingIdResponse(id)));
    }

    @PostMapping("/admin/role-mappings/user/{id}")
    public ResponseEntity<ApiResponse<RoleMappingIdResponse>> updateUserMapping(
            @PathVariable Long id,
            @Valid @RequestBody UserRoleMappingSaveRequest request
    ) {
        Long updatedId = roleMappingService.updateUserMapping(id, request);
        return ResponseEntity.ok(ApiResponse.success(new RoleMappingIdResponse(updatedId)));
    }

    @PostMapping("/admin/role-mappings/user/{id}/delete")
    public ResponseEntity<ApiResponse<Void>> deleteUserMapping(@PathVariable Long id) {
        roleMappingService.deleteUserMapping(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
