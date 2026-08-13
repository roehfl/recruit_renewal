package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.DeptRoleMapping;
import com.shinyoung.recruit.domain.entity.User;
import com.shinyoung.recruit.domain.entity.UserRoleMapping;
import com.shinyoung.recruit.domain.repository.DeptRoleMappingRepository;
import com.shinyoung.recruit.domain.repository.UserRepository;
import com.shinyoung.recruit.domain.repository.UserRoleMappingRepository;
import com.shinyoung.recruit.dto.request.DeptRoleMappingSaveRequest;
import com.shinyoung.recruit.dto.request.UserRoleMappingSaveRequest;
import com.shinyoung.recruit.dto.response.AssignableRoleResponse;
import com.shinyoung.recruit.dto.response.DeptRoleMappingResponse;
import com.shinyoung.recruit.dto.response.UserRoleMappingResponse;
import com.shinyoung.recruit.exception.InvalidRoleMappingException;
import com.shinyoung.recruit.exception.RoleMappingNotFoundException;
import com.shinyoung.recruit.security.auth.RoleNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 권한 관리 화면(부서별/사용자별 role 매핑) 서비스.
 *
 * <p>매핑이 로그인 권한 계산에 쓰이는 방식이 서로 다르다는 점이 검증 규칙의 근거다.
 * 부서 매핑은 AD 그룹 cn <b>부분일치</b>로 조회되므로 짧은 부서명이 다른 부서 그룹에
 * 오매칭될 수 있어 최소 길이를 강제하고, 사용자 매핑은 loginId <b>완전일치</b>라 그런 제약이 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleMappingService {

    /** 부분일치 오매칭 방어 — 예: "채널"을 등록하면 "내부채널_부서_6315"에도 걸린다. */
    private static final int MIN_DEPT_NAME_LENGTH = 2;

    private final DeptRoleMappingRepository deptRoleMappingRepository;
    private final UserRoleMappingRepository userRoleMappingRepository;
    private final UserRepository userRepository;

    public List<AssignableRoleResponse> getAssignableRoles() {
        return RoleNames.ASSIGNABLE_ROLES.stream()
                .map(AssignableRoleResponse::from)
                .toList();
    }

    public List<DeptRoleMappingResponse> getDeptMappings() {
        return deptRoleMappingRepository.findAllByOrderByIdAsc().stream()
                .map(DeptRoleMappingResponse::from)
                .toList();
    }

    @Transactional
    public Long createDeptMapping(DeptRoleMappingSaveRequest request) {
        String deptName = normalizeDeptName(request.deptName());
        String roleName = validateAssignableRole(request.roleName());

        if (deptRoleMappingRepository.existsByDeptNameAndRoleName(deptName, roleName)) {
            throw new InvalidRoleMappingException("이미 등록된 부서 매핑입니다. deptName=" + deptName + ", roleName=" + roleName);
        }

        return deptRoleMappingRepository.save(DeptRoleMapping.create(deptName, roleName)).getId();
    }

    @Transactional
    public Long updateDeptMapping(Long id, DeptRoleMappingSaveRequest request) {
        DeptRoleMapping mapping = findDeptMapping(id);

        String deptName = normalizeDeptName(request.deptName());
        String roleName = validateAssignableRole(request.roleName());

        if (deptRoleMappingRepository.existsByDeptNameAndRoleNameAndIdNot(deptName, roleName, id)) {
            throw new InvalidRoleMappingException("이미 등록된 부서 매핑입니다. deptName=" + deptName + ", roleName=" + roleName);
        }

        mapping.update(deptName, roleName);
        return mapping.getId();
    }

    @Transactional
    public void deleteDeptMapping(Long id) {
        deptRoleMappingRepository.delete(findDeptMapping(id));
    }

    public List<UserRoleMappingResponse> getUserMappings() {
        List<UserRoleMapping> mappings = userRoleMappingRepository.findAllByOrderByIdAsc();

        List<String> loginIds = mappings.stream()
                .map(UserRoleMapping::getLoginId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        Map<String, User> usersByLoginId = loginIds.isEmpty()
                ? Map.of()
                : userRepository.findByLoginIdIn(loginIds).stream()
                        .collect(Collectors.toMap(User::getLoginId, Function.identity()));

        return mappings.stream()
                .map(mapping -> UserRoleMappingResponse.from(mapping, usersByLoginId.get(mapping.getLoginId())))
                .toList();
    }

    @Transactional
    public Long createUserMapping(UserRoleMappingSaveRequest request) {
        String loginId = normalizeLoginId(request.loginId());
        String roleName = validateAssignableRole(request.roleName());

        if (userRoleMappingRepository.existsByLoginIdAndRoleName(loginId, roleName)) {
            throw new InvalidRoleMappingException("이미 등록된 사용자 매핑입니다. loginId=" + loginId + ", roleName=" + roleName);
        }

        return userRoleMappingRepository.save(UserRoleMapping.create(loginId, roleName)).getId();
    }

    @Transactional
    public Long updateUserMapping(Long id, UserRoleMappingSaveRequest request) {
        UserRoleMapping mapping = findUserMapping(id);

        String loginId = normalizeLoginId(request.loginId());
        String roleName = validateAssignableRole(request.roleName());

        if (userRoleMappingRepository.existsByLoginIdAndRoleNameAndIdNot(loginId, roleName, id)) {
            throw new InvalidRoleMappingException("이미 등록된 사용자 매핑입니다. loginId=" + loginId + ", roleName=" + roleName);
        }

        mapping.update(loginId, roleName);
        return mapping.getId();
    }

    @Transactional
    public void deleteUserMapping(Long id) {
        userRoleMappingRepository.delete(findUserMapping(id));
    }

    private DeptRoleMapping findDeptMapping(Long id) {
        return deptRoleMappingRepository.findById(id)
                .orElseThrow(() -> new RoleMappingNotFoundException("존재하지 않는 부서 매핑입니다. id=" + id));
    }

    private UserRoleMapping findUserMapping(Long id) {
        return userRoleMappingRepository.findById(id)
                .orElseThrow(() -> new RoleMappingNotFoundException("존재하지 않는 사용자 매핑입니다. id=" + id));
    }

    private String normalizeDeptName(String deptName) {
        String normalized = deptName == null ? "" : deptName.trim();

        if (normalized.length() < MIN_DEPT_NAME_LENGTH) {
            throw new InvalidRoleMappingException(
                    "부서명은 " + MIN_DEPT_NAME_LENGTH + "자 이상이어야 합니다. 짧은 부서명은 다른 부서 그룹명에 부분일치로 오매칭될 수 있습니다.");
        }

        return normalized;
    }

    private String normalizeLoginId(String loginId) {
        String normalized = loginId == null ? "" : loginId.trim();

        if (!StringUtils.hasText(normalized)) {
            throw new InvalidRoleMappingException("loginId는 필수입니다.");
        }

        return normalized;
    }

    private String validateAssignableRole(String roleName) {
        String normalized = roleName == null ? "" : roleName.trim();

        if (!RoleNames.isAssignable(normalized)) {
            throw new InvalidRoleMappingException("부여할 수 없는 role입니다. roleName=" + normalized);
        }

        return normalized;
    }
}
