package com.shinyoung.recruit.service;

import com.shinyoung.recruit.domain.entity.CommonCode;
import com.shinyoung.recruit.domain.repository.CommonCodeRepository;
import com.shinyoung.recruit.dto.request.CommonCodeCreateRequest;
import com.shinyoung.recruit.dto.request.CommonCodeUpdateRequest;
import com.shinyoung.recruit.dto.response.CommonCodeResponse;
import com.shinyoung.recruit.exception.CommonCodeNotFoundException;
import com.shinyoung.recruit.exception.InvalidCommonCodeException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CommonCode 관리(Phase 08a). public 조회는 활성 코드만, admin 은 CRUD + 비활성 포함 조회.
 * code/groupCode 는 생성 후 불변이며 삭제는 soft delete({@code active=false})로만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommonCodeService {

    private final CommonCodeRepository commonCodeRepository;

    /** public: 활성 코드만 sortOrder 순. */
    public List<CommonCodeResponse> getActiveCodes(String groupCode) {
        validateGroupCode(groupCode);
        return commonCodeRepository
                .findByGroupCodeAndActiveTrueOrderBySortOrderAscIdAsc(groupCode.trim())
                .stream()
                .map(CommonCodeResponse::from)
                .toList();
    }

    /** admin: 비활성 포함. groupCode 미지정 시 전체. */
    public List<CommonCodeResponse> getAdminCodes(String groupCode) {
        List<CommonCode> codes = (groupCode == null || groupCode.isBlank())
                ? commonCodeRepository.findAllByOrderByGroupCodeAscSortOrderAscIdAsc()
                : commonCodeRepository.findByGroupCodeOrderBySortOrderAscIdAsc(groupCode.trim());
        return codes.stream().map(CommonCodeResponse::from).toList();
    }

    @Transactional
    public CommonCodeResponse create(CommonCodeCreateRequest request) {
        String groupCode = request.groupCode().trim();
        String code = request.code().trim();
        if (commonCodeRepository.existsByGroupCodeAndCode(groupCode, code)) {
            throw new InvalidCommonCodeException("이미 존재하는 코드입니다. groupCode=" + groupCode + ", code=" + code);
        }
        try {
            // 동시 생성 race(둘 다 exists=false 통과)는 DB unique 제약으로만 잡히므로 flush 시점에 변환한다.
            CommonCode saved = commonCodeRepository.saveAndFlush(CommonCode.create(
                    groupCode,
                    code,
                    request.displayName(),
                    request.sortOrder(),
                    request.active(),
                    request.description()
            ));
            return CommonCodeResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new InvalidCommonCodeException("이미 존재하는 코드입니다. groupCode=" + groupCode + ", code=" + code);
        }
    }

    @Transactional
    public CommonCodeResponse update(Long id, CommonCodeUpdateRequest request) {
        CommonCode commonCode = commonCodeRepository.findById(id)
                .orElseThrow(() -> new CommonCodeNotFoundException("CommonCode를 찾을 수 없습니다. id=" + id));
        commonCode.update(
                request.displayName(),
                request.sortOrder(),
                request.active(),
                request.description()
        );
        return CommonCodeResponse.from(commonCode);
    }

    private void validateGroupCode(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            throw new InvalidCommonCodeException("groupCode은(는) 필수입니다.");
        }
    }
}
