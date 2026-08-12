# 공고 이미지 입력(공고 입력화면) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 공고 본문을 WYSIWYG 대신 이미지 목록(`JobPostingImage`)으로 입력·저장·서빙하고, 관리자 공고 목록/등록/상세(미리보기·발행) 화면과 지원자 이미지 렌더를 구현한다.

**Architecture:** 백엔드는 신규 `JobPostingImage` 엔티티 + 전용 storage root(첨부 헬스스캔과 분리) + multipart 생성 확장 + 이미지 단위 API + 공개/관리자 바이너리 서빙. 프론트는 관리자 3개 화면(목록/폼/상세) 신규 + 공용 이미지 스택 컴포넌트 + 지원자 상세 렌더 교체. 계약은 `recruit/api-contract.md` 🟡→🟢.

**Tech Stack:** Spring Boot 4.0.2 (Java 17, JPA/H2, MockMvc는 `spring-boot-starter-webmvc-test`), Vue 3 + TS + ant-design-vue 4 + axios.

**스펙:** `docs/superpowers/specs/2026-08-12-job-posting-image-input-design.md`

---

## 필독 문서 (작업 전)

- `recruit/CLAUDE.md` — 화면 슬라이스 워크플로우, 검증 정책(§5), git 규칙(§6)
- `recruit_back/recruit_backend/CLAUDE.md` — 백엔드 스타일(§5), 테스트 기준(§10)
- `recruit_front/AGENTS.md` — 프론트 규칙(스택 고정, `.ts` + `<script setup lang="ts">`, 한국어 UI 텍스트 유지)
- `recruit/api-contract.md` — "화면: 공고" 관련 기존 섹션

## 핵심 사전 지식 (조사 완료된 사실)

1. **경로 prefix**: 백엔드 `WebMvcConfig`가 모든 컨트롤러에 `/api` prefix를 붙인다. 컨트롤러 `@RequestMapping("/admin/job-postings")` → 실제/테스트 경로는 `/api/admin/job-postings`.
2. **보안**: `GET /api/job-postings/**` permitAll, `/api/admin/**`는 `ROLE_ADMIN`/`ROLE_RECRUIT_ADMIN` (SecurityConfig 수정 불필요). 공개 이미지 서빙은 **엔드포인트 자체에서 발행 여부 검사**가 필수.
3. **첨부 storage 헬스스캔**(`AttachmentStorageHealthScanService`)이 `recruit.attachment.storage-root` 아래 전체를 걷고 `applications/` 외 파일을 unmanaged 이슈로 보고한다 → **공고 이미지는 반드시 별도 root**(`recruit.posting-image.storage-root`, 기본 `posting-images`)를 쓴다.
4. **multipart 한도**: `application.yaml`에 이미 `max-file-size: 25MB`, `max-request-size: 105MB` — 10장×10MB 생성 요청이 통과한다. 변경 불필요.
5. **테스트**: `@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")` + `@Transactional` 관례. MockMvc는 `@AutoConfigureMockMvc`(import는 `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` — Boot 4 경로) + `spring-security-test`의 `user(...).authorities(...)`. CSRF는 disable 상태.
6. **기존 예외 매핑 재사용**: 검증 실패 = `InvalidJobPostingException`(→400), 미존재 = `JobPostingNotFoundException`(→404). 새 예외 클래스/핸들러 등록 금지.
7. **프론트 관례**: `apiClient`(axios, `withCredentials: true`, timeout 10초 — 업로드는 호출별 override), FormData에 Content-Type 수동 지정 금지, 인증 파일은 blob 응답 + `URL.createObjectURL` 패턴(`BasicInfoSection.vue` 참조). 관리자 공고 화면·라우트는 **현재 존재하지 않음**(모두 신규).
8. **백엔드 테스트 실행**(PowerShell, `recruit_back/recruit_backend/`에서 — CLAUDE.md §5 "변경 범위만": 패키지 와일드카드 금지, 변경 도메인 클래스만 지정):
   ```powershell
   $env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.JobPosting*" --tests "com.shinyoung.recruit.service.ImageSignatureValidatorTest" --tests "com.shinyoung.recruit.controller.JobPosting*" --no-daemon
   ```

## 파일 구조 맵

**recruit (하네스 저장소)**
- Modify: `api-contract.md` — 공고 이미지 계약 섹션 추가 (Task 0 🟡, Task 18 🟢)

**recruit_back/recruit_backend (백엔드)**
- Create: `src/main/java/com/shinyoung/recruit/domain/entity/JobPostingImage.java`
- Create: `src/main/java/com/shinyoung/recruit/domain/repository/JobPostingImageRepository.java`
- Create: `src/main/java/com/shinyoung/recruit/config/JobPostingImageProperties.java`
- Create: `src/main/java/com/shinyoung/recruit/service/ImageSignatureValidator.java`
- Create: `src/main/java/com/shinyoung/recruit/service/JobPostingImageStorageService.java` (+ record `StoredPostingImageFile`, `PostingImageResource` 동일 패키지 별도 파일)
- Create: `src/main/java/com/shinyoung/recruit/service/JobPostingImageService.java`
- Create: `src/main/java/com/shinyoung/recruit/dto/request/JobPostingImageMetaRequest.java`, `JobPostingImageAltTextUpdateRequest.java`, `JobPostingImageOrderRequest.java`
- Create: `src/main/java/com/shinyoung/recruit/dto/response/JobPostingImageResponse.java`
- Create: `src/main/java/com/shinyoung/recruit/controller/JobPostingImageController.java`
- Modify: `domain/entity/JobPosting.java` (contentHtml nullable), `dto/request/JobPostingCreateRequest.java`·`JobPostingUpdateRequest.java` (contentHtml @NotBlank 제거), `service/JobPostingService.java` (contentHtml 검증 제거, 이미지 연동 create, publish 규칙), `dto/response/JobPostingDetailResponse.java`·`JobPostingPublicDetailResponse.java` (images 추가), `service/JobPostingPublicService.java`, `controller/JobPostingController.java` (multipart create), `controller/JobPostingPublicController.java` (공개 서빙), `src/main/resources/application.yaml` (posting-image 설정)
- Test: `src/test/java/com/shinyoung/recruit/service/ImageSignatureValidatorTest.java`, `JobPostingImageStorageServiceTest.java`, `JobPostingImageServiceTest.java`, `src/test/java/com/shinyoung/recruit/controller/JobPostingImageControllerTest.java` (신규), 기존 `JobPostingServiceTest.java` 보완

**recruit_front (프론트)**
- Modify: `src/types/jobPosting.ts`, `src/api/adminJobPostingApi.ts`, `src/api/boardApi.ts`, `src/routes/adminRoutes.ts`, `src/views/applicant/ApplicationDetailView.vue`
- Create: `src/components/jobPosting/JobPostingImageStack.vue`
- Create: `src/views/admin/jobPosting/AdminJobPostingListView.vue`, `AdminJobPostingFormView.vue`, `AdminJobPostingDetailView.vue`

---

### Task 0: API 계약 🟡 초안 기재

**Files:**
- Modify: `C:\Users\roehf\Desktop\recruit\api-contract.md`

- [ ] **Step 1: 계약 섹션 추가**

`api-contract.md` 끝(또는 공고 관련 섹션 뒤)에 아래를 추가한다:

```markdown
### 화면: 관리자 공고 등록/수정 + 공고 이미지 (JobPostingImage)  🟡 초안 (2026-08-12)

설계: `docs/superpowers/specs/2026-08-12-job-posting-image-input-design.md`. 공고 본문은 WYSIWYG 대신 이미지 목록. `contentHtml`은 공고에서 deprecated(필드 유지, 신규 화면 미사용). 발행 조건: 이미지 ≥1장 또는 (레거시) contentHtml 존재.

#### POST `/admin/job-postings` (multipart 변형 추가) 🟡
- 기존 JSON 생성은 유지(하위호환). `consumes=multipart/form-data` 변형 추가:
  - part `request`(application/json): 기존 `JobPostingCreateRequest` 모양 (contentHtml은 이제 optional)
  - part `imageMetas`(application/json, optional): `[{ altText, sortOrder }]`
  - part `imageFiles`(file[], optional): imageMetas와 개수·순서 일치
- 응답: `ApiResponse<Long>` (생성 id). 공고+이미지 단일 트랜잭션 생성(draft).

#### 이미지 단위 API (관리자, 수정 화면 diff용) 🟡
- POST `/admin/job-postings/{id}/images` (multipart: `file` + query `altText`, `sortOrder?`) → `ApiResponse<Long>` (imageId). sortOrder 생략 시 맨 뒤.
- POST `/admin/job-postings/{id}/images/{imageId}` body `{ altText }` → altText 수정
- POST `/admin/job-postings/{id}/images/{imageId}/delete` → 삭제(행+파일)
- POST `/admin/job-postings/{id}/images/order` body `{ imageIds: [..] }` → 전체 순서 재지정(배열 index = sortOrder). imageIds는 해당 공고 이미지 전체와 정확히 일치해야 함.
- GET `/admin/job-postings/{id}/images/{imageId}/file` → 바이너리(inline). draft 포함.

#### 상세 응답 확장 🟡
- `GET /admin/job-postings/{id}`, `GET /job-postings/{id}` 응답에 `images: [{ id, altText, sortOrder, contentType, fileSize }]` 추가(sortOrder 오름차순).

#### GET `/job-postings/{id}/images/{imageId}/file` (공개) 🟡
- **발행(PUBLISHED)+공개조건 충족 공고의 이미지만** 응답(공개 상세와 동일 조건). 아니면 404. permitAll 경로이므로 이 검사가 draft 유출 차단의 2차 방어선.

#### 제한/검증 🟡
- 형식 jpg/jpeg/png/webp (Content-Type + 매직바이트), 장당 10MB, 공고당 10장, altText 필수(≤200자). 설정 prefix `recruit.posting-image.*`. storage root는 첨부와 분리(`posting-images`).
```

- [ ] **Step 2: 커밋 (recruit 저장소)**

```bash
cd C:/Users/roehf/Desktop/recruit
git add api-contract.md docs/superpowers/specs/2026-08-12-job-posting-image-input-design.md docs/superpowers/plans/2026-08-12-job-posting-image-input.md
git commit -m "docs(contract): 공고 이미지 입력 계약 초안 🟡 + 설계/계획 문서"
```

---

### Task 1: JobPostingImage 엔티티 + 리포지토리

**Files:**
- Create: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/domain/entity/JobPostingImage.java`
- Create: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/domain/repository/JobPostingImageRepository.java`

- [ ] **Step 1: 엔티티 작성**

```java
package com.shinyoung.recruit.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(nullable = false)
    private String originalFileName;

    /** storage root 기준 상대 경로. 응답에 노출하지 않는다(첨부파일 규약과 동일). */
    @Column(nullable = false, length = 500)
    private String storagePath;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private Integer sortOrder;

    /** 대체 텍스트(웹접근성). 필수. */
    @Column(nullable = false, length = 200)
    private String altText;

    private JobPostingImage(
            JobPosting jobPosting,
            String originalFileName,
            String storagePath,
            String contentType,
            Long fileSize,
            Integer sortOrder,
            String altText
    ) {
        this.jobPosting = jobPosting;
        this.originalFileName = originalFileName;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sortOrder = sortOrder;
        this.altText = altText;
    }

    public static JobPostingImage create(
            JobPosting jobPosting,
            String originalFileName,
            String storagePath,
            String contentType,
            Long fileSize,
            Integer sortOrder,
            String altText
    ) {
        return new JobPostingImage(jobPosting, originalFileName, storagePath, contentType, fileSize, sortOrder, altText);
    }

    public void updateAltText(String altText) {
        this.altText = altText;
    }

    public void changeSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
```

- [ ] **Step 2: 리포지토리 작성**

```java
package com.shinyoung.recruit.domain.repository;

import com.shinyoung.recruit.domain.entity.JobPostingImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobPostingImageRepository extends JpaRepository<JobPostingImage, Long> {

    List<JobPostingImage> findByJobPostingIdOrderBySortOrderAscIdAsc(Long jobPostingId);

    Optional<JobPostingImage> findByIdAndJobPostingId(Long id, Long jobPostingId);

    long countByJobPostingId(Long jobPostingId);
}
```

- [ ] **Step 3: 컴파일 확인**

Run (`recruit_back/recruit_backend/`):
```powershell
.\gradlew.bat compileJava --no-daemon
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋 (백엔드 저장소)**

```bash
cd C:/Users/roehf/Desktop/recruit/recruit_back/recruit_backend
git add src/main/java/com/shinyoung/recruit/domain/entity/JobPostingImage.java src/main/java/com/shinyoung/recruit/domain/repository/JobPostingImageRepository.java
git commit -m "feat(job-posting): 공고 이미지 엔티티/리포지토리 추가"
```

---

### Task 2: JobPostingImageProperties + application.yaml

**Files:**
- Create: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/config/JobPostingImageProperties.java`
- Modify: `recruit_back/recruit_backend/src/main/resources/application.yaml`

- [ ] **Step 1: 프로퍼티 클래스 작성** (`AttachmentProperties` 스타일)

```java
package com.shinyoung.recruit.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@Validated
@ConfigurationProperties(prefix = "recruit.posting-image")
public class JobPostingImageProperties {

    /** 첨부파일 storage 헬스스캔과 충돌하지 않도록 attachment root와 반드시 분리한다. */
    @NotNull
    private Path storageRoot = Path.of("posting-images");

    @NotNull
    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    @Min(1)
    private int maxImagesPerPosting = 10;

    @NotEmpty
    private List<String> allowedExtensions = new ArrayList<>(List.of("jpg", "jpeg", "png", "webp"));

    @NotEmpty
    private List<String> allowedContentTypes = new ArrayList<>(List.of("image/jpeg", "image/png", "image/webp"));

    public Path getStorageRoot() { return storageRoot; }
    public void setStorageRoot(Path storageRoot) { this.storageRoot = storageRoot; }
    public DataSize getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(DataSize maxFileSize) { this.maxFileSize = maxFileSize; }
    public int getMaxImagesPerPosting() { return maxImagesPerPosting; }
    public void setMaxImagesPerPosting(int maxImagesPerPosting) { this.maxImagesPerPosting = maxImagesPerPosting; }
    public List<String> getAllowedExtensions() { return allowedExtensions; }
    public void setAllowedExtensions(List<String> allowedExtensions) { this.allowedExtensions = allowedExtensions; }
    public List<String> getAllowedContentTypes() { return allowedContentTypes; }
    public void setAllowedContentTypes(List<String> allowedContentTypes) { this.allowedContentTypes = allowedContentTypes; }

    @AssertTrue(message = "maxFileSize must be greater than 0.")
    public boolean isMaxFileSizePositive() {
        return maxFileSize != null && maxFileSize.toBytes() > 0;
    }
}
```

- [ ] **Step 2: application.yaml에 설정 블록 추가**

`recruit:` 하위, `attachment:` 블록 아래에 추가:

```yaml
  posting-image:
    storage-root: ${RECRUIT_POSTING_IMAGE_STORAGE_ROOT:posting-images}
    max-file-size: ${RECRUIT_POSTING_IMAGE_MAX_FILE_SIZE:10MB}
    max-images-per-posting: ${RECRUIT_POSTING_IMAGE_MAX_IMAGES_PER_POSTING:10}
    allowed-extensions: ${RECRUIT_POSTING_IMAGE_ALLOWED_EXTENSIONS:jpg,jpeg,png,webp}
    allowed-content-types: ${RECRUIT_POSTING_IMAGE_ALLOWED_CONTENT_TYPES:image/jpeg,image/png,image/webp}
```

- [ ] **Step 3: 컴파일 확인 후 커밋**

```powershell
.\gradlew.bat compileJava --no-daemon
```
```bash
git add src/main/java/com/shinyoung/recruit/config/JobPostingImageProperties.java src/main/resources/application.yaml
git commit -m "feat(job-posting): 공고 이미지 설정 프로퍼티 추가"
```

---

### Task 3: ImageSignatureValidator (TDD)

**Files:**
- Create: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/service/ImageSignatureValidator.java`
- Test: `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit/service/ImageSignatureValidatorTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSignatureValidatorTest {

    private static final byte[] PNG_HEAD = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG_HEAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] WEBP_HEAD = {0x52, 0x49, 0x46, 0x46, 0x10, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};

    @Test
    void PNG_시그니처를_인식한다() {
        assertThat(ImageSignatureValidator.matches("image/png", PNG_HEAD)).isTrue();
    }

    @Test
    void JPEG_시그니처를_인식한다() {
        assertThat(ImageSignatureValidator.matches("image/jpeg", JPEG_HEAD)).isTrue();
    }

    @Test
    void WEBP_시그니처를_인식한다() {
        assertThat(ImageSignatureValidator.matches("image/webp", WEBP_HEAD)).isTrue();
    }

    @Test
    void contentType과_시그니처가_다르면_거부한다() {
        assertThat(ImageSignatureValidator.matches("image/png", JPEG_HEAD)).isFalse();
    }

    @Test
    void 미지원_contentType은_거부한다() {
        assertThat(ImageSignatureValidator.matches("image/gif", PNG_HEAD)).isFalse();
    }

    @Test
    void null_또는_짧은_head는_거부한다() {
        assertThat(ImageSignatureValidator.matches("image/png", null)).isFalse();
        assertThat(ImageSignatureValidator.matches("image/png", new byte[]{(byte) 0x89})).isFalse();
    }
}
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.ImageSignatureValidatorTest" --no-daemon
```
Expected: FAIL (컴파일 오류 — ImageSignatureValidator 미존재)

- [ ] **Step 3: 구현**

```java
package com.shinyoung.recruit.service;

/** 공고 이미지 매직바이트 검증. Content-Type 위조 업로드를 서빙 전에 차단한다. */
public final class ImageSignatureValidator {

    private ImageSignatureValidator() {
    }

    public static boolean matches(String contentType, byte[] head) {
        if (contentType == null || head == null) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> head.length >= 3
                    && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            case "image/png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> head.length >= 12
                    && startsWith(head, 0x52, 0x49, 0x46, 0x46)
                    && (head[8] & 0xFF) == 0x57 && (head[9] & 0xFF) == 0x45
                    && (head[10] & 0xFF) == 0x42 && (head[11] & 0xFF) == 0x50;
            default -> false;
        };
    }

    private static boolean startsWith(byte[] head, int... expected) {
        if (head.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Step 2와 동일 명령. Expected: PASS (6 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/shinyoung/recruit/service/ImageSignatureValidator.java src/test/java/com/shinyoung/recruit/service/ImageSignatureValidatorTest.java
git commit -m "feat(job-posting): 이미지 매직바이트 검증기 추가"
```

---

### Task 4: JobPostingImageStorageService (TDD)

**Files:**
- Create: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/service/StoredPostingImageFile.java`
- Create: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/service/PostingImageResource.java`
- Create: `recruit_back/recruit_backend/src/main/java/com/shinyoung/recruit/service/JobPostingImageStorageService.java`
- Test: `recruit_back/recruit_backend/src/test/java/com/shinyoung/recruit/service/JobPostingImageStorageServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.JobPostingImageProperties;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobPostingImageStorageServiceTest {

    @TempDir
    Path tempDir;

    private JobPostingImageStorageService newService() {
        JobPostingImageProperties properties = new JobPostingImageProperties();
        properties.setStorageRoot(tempDir);
        return new JobPostingImageStorageService(properties);
    }

    @Test
    void 저장하면_공고별_경로에_UUID_파일명으로_기록된다() {
        JobPostingImageStorageService service = newService();
        MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", new byte[]{1, 2, 3});

        StoredPostingImageFile stored = service.store(7L, file, "png");

        assertThat(stored.storagePath()).startsWith("job-postings/7/").endsWith(".png");
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.fileSize()).isEqualTo(3L);
        assertThat(service.exists(stored.storagePath())).isTrue();
    }

    @Test
    void 저장한_파일을_load하면_리소스를_반환한다() {
        JobPostingImageStorageService service = newService();
        MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", new byte[]{1, 2, 3});
        StoredPostingImageFile stored = service.store(7L, file, "png");

        PostingImageResource resource = service.load(stored.storagePath(), stored.contentType());

        assertThat(resource.contentLength()).isEqualTo(3L);
        assertThat(resource.contentType()).isEqualTo("image/png");
        assertThat(resource.resource().exists()).isTrue();
    }

    @Test
    void 없는_경로_load는_NotFound_예외() {
        JobPostingImageStorageService service = newService();

        assertThatThrownBy(() -> service.load("job-postings/1/none.png", "image/png"))
                .isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void 루트_밖_경로는_거부한다() {
        JobPostingImageStorageService service = newService();

        assertThatThrownBy(() -> service.load("../secret.txt", "image/png"))
                .isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void deleteIfExists는_파일을_지운다() {
        JobPostingImageStorageService service = newService();
        MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", new byte[]{1});
        StoredPostingImageFile stored = service.store(7L, file, "png");

        service.deleteIfExists(stored.storagePath());

        assertThat(service.exists(stored.storagePath())).isFalse();
    }
}
```

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.JobPostingImageStorageServiceTest" --no-daemon
```
Expected: FAIL (클래스 미존재)

- [ ] **Step 3: 구현**

`StoredPostingImageFile.java`:
```java
package com.shinyoung.recruit.service;

public record StoredPostingImageFile(String storagePath, String contentType, long fileSize) {
}
```

`PostingImageResource.java`:
```java
package com.shinyoung.recruit.service;

import org.springframework.core.io.Resource;

public record PostingImageResource(Resource resource, String contentType, long contentLength) {
}
```

`JobPostingImageStorageService.java` (`LocalAttachmentStorageService` 패턴, 단 root는 posting-image 전용):
```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.JobPostingImageProperties;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
public class JobPostingImageStorageService {

    private final Path storageRoot;

    public JobPostingImageStorageService(JobPostingImageProperties properties) {
        this.storageRoot = properties.getStorageRoot().toAbsolutePath().normalize();
    }

    public StoredPostingImageFile store(Long jobPostingId, MultipartFile file, String extension) {
        String storedFileName = UUID.randomUUID() + "." + extension;
        String storagePath = "job-postings/%d/%s".formatted(jobPostingId, storedFileName);

        Path target = resolveUnderRoot(storagePath);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new InvalidJobPostingException("공고 이미지를 저장하지 못했습니다.");
        }

        return new StoredPostingImageFile(storagePath, file.getContentType(), file.getSize());
    }

    public PostingImageResource load(String storagePath, String contentType) {
        Path path = resolveUnderRoot(storagePath);
        if (!Files.isRegularFile(path)) {
            throw new JobPostingNotFoundException("공고 이미지를 찾을 수 없습니다.");
        }
        try {
            return new PostingImageResource(new FileSystemResource(path), contentType, Files.size(path));
        } catch (IOException e) {
            throw new JobPostingNotFoundException("공고 이미지를 찾을 수 없습니다.");
        }
    }

    public boolean exists(String storagePath) {
        return Files.exists(resolveUnderRoot(storagePath));
    }

    public void deleteIfExists(String storagePath) {
        try {
            Files.deleteIfExists(resolveUnderRoot(storagePath));
        } catch (IOException | RuntimeException e) {
            // 행 삭제가 우선이며 파일 잔존은 재삭제 가능하므로 실패는 로깅만 한다.
            log.warn("Failed to delete posting image file.", e);
        }
    }

    private Path resolveUnderRoot(String storagePath) {
        if (storagePath == null || storagePath.isBlank() || Path.of(storagePath).isAbsolute()) {
            throw new JobPostingNotFoundException("공고 이미지를 찾을 수 없습니다.");
        }
        Path resolved = storageRoot.resolve(storagePath).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new JobPostingNotFoundException("공고 이미지를 찾을 수 없습니다.");
        }
        return resolved;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인** (Step 2 명령, PASS 5 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/shinyoung/recruit/service/StoredPostingImageFile.java src/main/java/com/shinyoung/recruit/service/PostingImageResource.java src/main/java/com/shinyoung/recruit/service/JobPostingImageStorageService.java src/test/java/com/shinyoung/recruit/service/JobPostingImageStorageServiceTest.java
git commit -m "feat(job-posting): 공고 이미지 로컬 스토리지 서비스 추가"
```

---

### Task 5: 요청/응답 DTO + JobPostingImageService (TDD)

**Files:**
- Create: `dto/request/JobPostingImageMetaRequest.java`, `dto/request/JobPostingImageAltTextUpdateRequest.java`, `dto/request/JobPostingImageOrderRequest.java`
- Create: `dto/response/JobPostingImageResponse.java`
- Create: `service/JobPostingImageService.java`
- Test: `src/test/java/com/shinyoung/recruit/service/JobPostingImageServiceTest.java`

- [ ] **Step 1: DTO 작성**

`JobPostingImageMetaRequest.java`:
```java
package com.shinyoung.recruit.dto.request;

/** multipart 생성 시 imageFiles와 index로 짝을 이루는 이미지 메타. */
public record JobPostingImageMetaRequest(String altText, Integer sortOrder) {
}
```

`JobPostingImageAltTextUpdateRequest.java`:
```java
package com.shinyoung.recruit.dto.request;

public record JobPostingImageAltTextUpdateRequest(String altText) {
}
```

`JobPostingImageOrderRequest.java`:
```java
package com.shinyoung.recruit.dto.request;

import java.util.List;

public record JobPostingImageOrderRequest(List<Long> imageIds) {
}
```

`JobPostingImageResponse.java`:
```java
package com.shinyoung.recruit.dto.response;

import com.shinyoung.recruit.domain.entity.JobPostingImage;

public record JobPostingImageResponse(
        Long id,
        String altText,
        Integer sortOrder,
        String contentType,
        Long fileSize
) {
    public static JobPostingImageResponse from(JobPostingImage image) {
        return new JobPostingImageResponse(
                image.getId(),
                image.getAltText(),
                image.getSortOrder(),
                image.getContentType(),
                image.getFileSize()
        );
    }
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.dto.request.JobPostingImageMetaRequest;
import com.shinyoung.recruit.dto.response.JobPostingImageResponse;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.posting-image.storage-root=build/test-posting-images"
})
@Transactional
class JobPostingImageServiceTest {

    private static final byte[] PNG_HEAD = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG_HEAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingImageService jobPostingImageService;

    private Long createPosting() {
        return jobPostingService.create(new JobPostingCreateRequest(
                "2026 채용",
                "<p>내용</p>",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest("백엔드", 0)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        ));
    }

    private MultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG_HEAD);
    }

    @Test
    void 이미지를_추가하면_목록에서_정렬순으로_조회된다() {
        Long postingId = createPosting();

        jobPostingImageService.addImage(postingId, png("b.png"), "포스터 2", 1);
        jobPostingImageService.addImage(postingId, png("a.png"), "포스터 1", 0);

        List<JobPostingImageResponse> images = jobPostingImageService.getImages(postingId);
        assertThat(images).hasSize(2);
        assertThat(images.get(0).altText()).isEqualTo("포스터 1");
        assertThat(images.get(1).altText()).isEqualTo("포스터 2");
    }

    @Test
    void sortOrder_생략시_맨뒤에_붙는다() {
        Long postingId = createPosting();
        jobPostingImageService.addImage(postingId, png("a.png"), "포스터 1", 0);

        jobPostingImageService.addImage(postingId, png("b.png"), "포스터 2", null);

        List<JobPostingImageResponse> images = jobPostingImageService.getImages(postingId);
        assertThat(images.get(1).altText()).isEqualTo("포스터 2");
        assertThat(images.get(1).sortOrder()).isEqualTo(1);
    }

    @Test
    void altText_없으면_실패한다() {
        Long postingId = createPosting();

        assertThatThrownBy(() -> jobPostingImageService.addImage(postingId, png("a.png"), " ", 0))
                .isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("대체 텍스트");
    }

    @Test
    void 허용되지_않은_contentType은_실패한다() {
        Long postingId = createPosting();
        MultipartFile gif = new MockMultipartFile("file", "a.gif", "image/gif", PNG_HEAD);

        assertThatThrownBy(() -> jobPostingImageService.addImage(postingId, gif, "포스터", 0))
                .isInstanceOf(InvalidJobPostingException.class);
    }

    @Test
    void contentType과_시그니처가_다르면_실패한다() {
        Long postingId = createPosting();
        MultipartFile fake = new MockMultipartFile("file", "a.png", "image/png", JPEG_HEAD);

        assertThatThrownBy(() -> jobPostingImageService.addImage(postingId, fake, "포스터", 0))
                .isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("시그니처");
    }

    @Test
    void 최대_장수를_넘으면_실패한다() {
        Long postingId = createPosting();
        for (int i = 0; i < 10; i++) {
            jobPostingImageService.addImage(postingId, png("p" + i + ".png"), "포스터 " + i, i);
        }

        assertThatThrownBy(() -> jobPostingImageService.addImage(postingId, png("over.png"), "초과", 10))
                .isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("최대");
    }

    @Test
    void 생성시_메타와_파일수가_다르면_실패한다() {
        Long postingId = createPosting();
        // createImages는 JobPostingService.create 경로에서 쓰이지만 단독 검증도 가능해야 한다.
        assertThatThrownBy(() -> jobPostingImageService.createImages(
                postingId,
                List.of(new JobPostingImageMetaRequest("포스터", 0)),
                List.of(png("a.png"), png("b.png"))
        )).isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("일치");
    }

    @Test
    void altText를_수정한다() {
        Long postingId = createPosting();
        Long imageId = jobPostingImageService.addImage(postingId, png("a.png"), "이전", 0);

        jobPostingImageService.updateAltText(postingId, imageId, "이후");

        assertThat(jobPostingImageService.getImages(postingId).get(0).altText()).isEqualTo("이후");
    }

    @Test
    void 이미지를_삭제한다() {
        Long postingId = createPosting();
        Long imageId = jobPostingImageService.addImage(postingId, png("a.png"), "포스터", 0);

        jobPostingImageService.deleteImage(postingId, imageId);

        assertThat(jobPostingImageService.getImages(postingId)).isEmpty();
    }

    @Test
    void 다른_공고의_이미지는_삭제할_수_없다() {
        Long postingA = createPosting();
        Long postingB = createPosting();
        Long imageId = jobPostingImageService.addImage(postingA, png("a.png"), "포스터", 0);

        assertThatThrownBy(() -> jobPostingImageService.deleteImage(postingB, imageId))
                .isInstanceOf(JobPostingNotFoundException.class);
    }

    @Test
    void 순서를_재지정한다() {
        Long postingId = createPosting();
        Long first = jobPostingImageService.addImage(postingId, png("a.png"), "포스터 1", 0);
        Long second = jobPostingImageService.addImage(postingId, png("b.png"), "포스터 2", 1);

        jobPostingImageService.reorder(postingId, List.of(second, first));

        List<JobPostingImageResponse> images = jobPostingImageService.getImages(postingId);
        assertThat(images.get(0).id()).isEqualTo(second);
        assertThat(images.get(1).id()).isEqualTo(first);
    }

    @Test
    void 순서_재지정시_전체_id가_일치하지_않으면_실패한다() {
        Long postingId = createPosting();
        Long first = jobPostingImageService.addImage(postingId, png("a.png"), "포스터 1", 0);
        jobPostingImageService.addImage(postingId, png("b.png"), "포스터 2", 1);

        assertThatThrownBy(() -> jobPostingImageService.reorder(postingId, List.of(first)))
                .isInstanceOf(InvalidJobPostingException.class);
    }
}
```

- [ ] **Step 3: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.JobPostingImageServiceTest" --no-daemon
```
Expected: FAIL (JobPostingImageService 미존재)

- [ ] **Step 4: 서비스 구현**

```java
package com.shinyoung.recruit.service;

import com.shinyoung.recruit.config.JobPostingImageProperties;
import com.shinyoung.recruit.domain.entity.JobPosting;
import com.shinyoung.recruit.domain.entity.JobPostingImage;
import com.shinyoung.recruit.domain.repository.JobPostingImageRepository;
import com.shinyoung.recruit.domain.repository.JobPostingRepository;
import com.shinyoung.recruit.dto.request.JobPostingImageMetaRequest;
import com.shinyoung.recruit.dto.response.JobPostingImageResponse;
import com.shinyoung.recruit.enumeration.JobPostingStatus;
import com.shinyoung.recruit.exception.InvalidJobPostingException;
import com.shinyoung.recruit.exception.JobPostingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingImageService {

    private static final int ALT_TEXT_MAX_LENGTH = 200;
    private static final int SIGNATURE_HEAD_LENGTH = 12;

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingImageRepository jobPostingImageRepository;
    private final JobPostingImageStorageService storageService;
    private final JobPostingImageProperties properties;
    private final Clock clock;

    public List<JobPostingImageResponse> getImages(Long jobPostingId) {
        return jobPostingImageRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(jobPostingId).stream()
                .map(JobPostingImageResponse::from)
                .toList();
    }

    public long countImages(Long jobPostingId) {
        return jobPostingImageRepository.countByJobPostingId(jobPostingId);
    }

    /** 공고 생성(multipart) 경로. 파일 전체를 먼저 검증한 뒤 저장해 부분 저장을 최소화한다. */
    @Transactional
    public void createImages(Long jobPostingId, List<JobPostingImageMetaRequest> metas, List<MultipartFile> files) {
        boolean hasFiles = files != null && !files.isEmpty();
        boolean hasMetas = metas != null && !metas.isEmpty();
        if (!hasFiles) {
            if (hasMetas) {
                throw new InvalidJobPostingException("이미지 파일 없이 이미지 정보만 전달할 수 없습니다.");
            }
            return;
        }
        if (!hasMetas || metas.size() != files.size()) {
            throw new InvalidJobPostingException("이미지 파일 수와 이미지 정보 수가 일치해야 합니다.");
        }

        JobPosting jobPosting = findJobPosting(jobPostingId);
        validateTotalCount(jobPostingId, files.size());
        validateDistinctSortOrders(metas);
        for (int i = 0; i < files.size(); i++) {
            validateFile(files.get(i));
            validateAltText(metas.get(i).altText());
            validateSortOrder(metas.get(i).sortOrder());
        }
        for (int i = 0; i < files.size(); i++) {
            saveImage(jobPosting, files.get(i), metas.get(i).altText(), metas.get(i).sortOrder());
        }
    }

    /** 수정 화면 diff 경로. sortOrder 생략 시 맨 뒤에 붙인다. */
    @Transactional
    public Long addImage(Long jobPostingId, MultipartFile file, String altText, Integer sortOrder) {
        JobPosting jobPosting = findJobPosting(jobPostingId);
        rejectClosed(jobPosting);
        validateTotalCount(jobPostingId, 1);
        validateFile(file);
        validateAltText(altText);
        Integer resolvedSortOrder = sortOrder != null ? sortOrder : nextSortOrder(jobPostingId);
        validateSortOrder(resolvedSortOrder);
        return saveImage(jobPosting, file, altText, resolvedSortOrder);
    }

    @Transactional
    public Long updateAltText(Long jobPostingId, Long imageId, String altText) {
        rejectClosed(findJobPosting(jobPostingId));
        validateAltText(altText);
        JobPostingImage image = findImage(jobPostingId, imageId);
        image.updateAltText(altText.trim());
        return image.getId();
    }

    @Transactional
    public void deleteImage(Long jobPostingId, Long imageId) {
        rejectClosed(findJobPosting(jobPostingId));
        JobPostingImage image = findImage(jobPostingId, imageId);
        String storagePath = image.getStoragePath();
        jobPostingImageRepository.delete(image);
        storageService.deleteIfExists(storagePath);
    }

    /** imageIds 배열 index가 새 sortOrder가 된다. 해당 공고 이미지 전체와 정확히 일치해야 한다. */
    @Transactional
    public void reorder(Long jobPostingId, List<Long> imageIds) {
        rejectClosed(findJobPosting(jobPostingId));
        if (imageIds == null || imageIds.isEmpty()) {
            throw new InvalidJobPostingException("이미지 순서 목록이 비어 있습니다.");
        }
        List<JobPostingImage> images = jobPostingImageRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(jobPostingId);
        Set<Long> existingIds = new HashSet<>(images.stream().map(JobPostingImage::getId).toList());
        Set<Long> requestedIds = new HashSet<>(imageIds);
        if (imageIds.size() != images.size() || !existingIds.equals(requestedIds)) {
            throw new InvalidJobPostingException("이미지 순서 목록은 공고의 전체 이미지와 일치해야 합니다.");
        }
        for (JobPostingImage image : images) {
            image.changeSortOrder(imageIds.indexOf(image.getId()));
        }
    }

    public PostingImageResource loadAdminImage(Long jobPostingId, Long imageId) {
        JobPostingImage image = findImage(jobPostingId, imageId);
        return storageService.load(image.getStoragePath(), image.getContentType());
    }

    /** 공개 서빙: 발행+공개조건 충족 공고만. draft 유출 차단의 2차 방어선. */
    public PostingImageResource loadPublicImage(Long jobPostingId, Long imageId) {
        LocalDateTime now = LocalDateTime.now(clock);
        jobPostingRepository.findPublicDetailById(jobPostingId, JobPostingStatus.PUBLISHED, now)
                .orElseThrow(() -> new JobPostingNotFoundException("공고 이미지를 찾을 수 없습니다."));
        return loadAdminImage(jobPostingId, imageId);
    }

    private Long saveImage(JobPosting jobPosting, MultipartFile file, String altText, Integer sortOrder) {
        String extension = extractExtension(file.getOriginalFilename());
        StoredPostingImageFile stored = storageService.store(jobPosting.getId(), file, extension);
        JobPostingImage image = JobPostingImage.create(
                jobPosting,
                sanitizeFileName(file.getOriginalFilename()),
                stored.storagePath(),
                stored.contentType(),
                stored.fileSize(),
                sortOrder,
                altText.trim()
        );
        return jobPostingImageRepository.save(image).getId();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidJobPostingException("이미지 파일이 없습니다.");
        }
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw new InvalidJobPostingException(
                    "이미지 크기는 장당 " + properties.getMaxFileSize().toMegabytes() + "MB 이하이어야 합니다.");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (contentType == null || !properties.getAllowedContentTypes().contains(contentType)) {
            throw new InvalidJobPostingException("허용되지 않은 이미지 형식입니다.");
        }
        String extension = extractExtension(file.getOriginalFilename());
        if (!properties.getAllowedExtensions().contains(extension)) {
            throw new InvalidJobPostingException("허용되지 않은 이미지 확장자입니다.");
        }
        byte[] head = readHead(file);
        if (!ImageSignatureValidator.matches(contentType, head)) {
            throw new InvalidJobPostingException("이미지 형식이 올바르지 않습니다(시그니처 불일치).");
        }
    }

    private byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SIGNATURE_HEAD_LENGTH);
        } catch (IOException e) {
            throw new InvalidJobPostingException("이미지 파일을 읽지 못했습니다.");
        }
    }

    private String normalizeContentType(String contentType) {
        return "image/jpg".equals(contentType) ? "image/jpeg" : contentType;
    }

    private String extractExtension(String originalFileName) {
        String name = sanitizeFileName(originalFileName);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new InvalidJobPostingException("이미지 확장자를 확인할 수 없습니다.");
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new InvalidJobPostingException("이미지 파일명이 없습니다.");
        }
        String name = originalFileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private void validateAltText(String altText) {
        if (altText == null || altText.isBlank()) {
            throw new InvalidJobPostingException("이미지 대체 텍스트는 필수입니다.");
        }
        if (altText.trim().length() > ALT_TEXT_MAX_LENGTH) {
            throw new InvalidJobPostingException("이미지 대체 텍스트는 " + ALT_TEXT_MAX_LENGTH + "자 이하이어야 합니다.");
        }
    }

    private void validateSortOrder(Integer sortOrder) {
        if (sortOrder == null || sortOrder < 0) {
            throw new InvalidJobPostingException("이미지 정렬 순서는 0 이상이어야 합니다.");
        }
    }

    private void validateDistinctSortOrders(List<JobPostingImageMetaRequest> metas) {
        Set<Integer> seen = new HashSet<>();
        for (JobPostingImageMetaRequest meta : metas) {
            if (meta.sortOrder() != null && !seen.add(meta.sortOrder())) {
                throw new InvalidJobPostingException("이미지 정렬 순서는 중복될 수 없습니다.");
            }
        }
    }

    private void validateTotalCount(Long jobPostingId, int adding) {
        long current = jobPostingImageRepository.countByJobPostingId(jobPostingId);
        if (current + adding > properties.getMaxImagesPerPosting()) {
            throw new InvalidJobPostingException(
                    "공고 이미지는 최대 " + properties.getMaxImagesPerPosting() + "장까지 등록할 수 있습니다.");
        }
    }

    private int nextSortOrder(Long jobPostingId) {
        return jobPostingImageRepository.findByJobPostingIdOrderBySortOrderAscIdAsc(jobPostingId).stream()
                .mapToInt(JobPostingImage::getSortOrder)
                .max()
                .orElse(-1) + 1;
    }

    private void rejectClosed(JobPosting jobPosting) {
        if (jobPosting.getStatus() == JobPostingStatus.CLOSED) {
            throw new InvalidJobPostingException("마감된 공고의 이미지는 수정할 수 없습니다.");
        }
    }

    private JobPosting findJobPosting(Long id) {
        return jobPostingRepository.findById(id)
                .orElseThrow(() -> new JobPostingNotFoundException("채용공고를 찾을 수 없습니다. id=" + id));
    }

    private JobPostingImage findImage(Long jobPostingId, Long imageId) {
        return jobPostingImageRepository.findByIdAndJobPostingId(imageId, jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("공고 이미지를 찾을 수 없습니다. id=" + imageId));
    }
}
```

주의: `jobPostingRepository.findPublicDetailById(...)`의 정확한 시그니처는 `JobPostingPublicService.getJobPosting`이 쓰는 것과 동일하게 맞춘다(파라미터: id, `JobPostingStatus.PUBLISHED`, now).

- [ ] **Step 5: 테스트 통과 확인** (Step 3 명령, PASS 12 tests)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/shinyoung/recruit/dto src/main/java/com/shinyoung/recruit/service/JobPostingImageService.java src/test/java/com/shinyoung/recruit/service/JobPostingImageServiceTest.java
git commit -m "feat(job-posting): 공고 이미지 서비스(추가/수정/삭제/순서/서빙 로직) 추가"
```

---

### Task 6: contentHtml 완화 + 생성 이미지 연동 + publish 규칙 (TDD)

**Files:**
- Modify: `domain/entity/JobPosting.java:33-35`
- Modify: `dto/request/JobPostingCreateRequest.java:18`, `dto/request/JobPostingUpdateRequest.java` (contentHtml `@NotBlank` 제거)
- Modify: `service/JobPostingService.java` (validateRequest의 contentHtml 검증 제거, create 오버로드, publish 규칙)
- Test: 기존 `JobPostingServiceTest.java`에 추가/수정

- [ ] **Step 1: 실패하는 테스트 추가** (`JobPostingServiceTest`에 추가; `JobPostingImageService` `@Autowired` 필드와 PNG 헬퍼도 추가)

```java
    @Autowired
    private JobPostingImageService jobPostingImageService;

    private static final byte[] PNG_HEAD = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Test
    void contentHtml_없이_공고를_생성할_수_있다() {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "이미지 공고",
                null,
                null,
                null,   // contentHtml 없음
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                null, null, null, null, null,
                List.of(new JobPositionRequest("백엔드", 0)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );

        Long id = jobPostingService.create(request);

        assertThat(jobPostingService.getJobPosting(id).contentHtml()).isNull();
    }

    @Test
    void 이미지와_contentHtml_모두_없으면_발행할_수_없다() {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "이미지 공고",
                null,
                null,
                null,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                null, null, null, null, null,
                List.of(new JobPositionRequest("백엔드", 0)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );
        Long id = jobPostingService.create(request);

        assertThatThrownBy(() -> jobPostingService.publish(id))
                .isInstanceOf(InvalidJobPostingException.class)
                .hasMessageContaining("이미지");
    }

    @Test
    void 이미지가_있으면_contentHtml_없이_발행된다() {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "이미지 공고",
                null,
                null,
                null,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                null, null, null, null, null,
                List.of(new JobPositionRequest("백엔드", 0)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );
        Long id = jobPostingService.create(request);
        jobPostingImageService.addImage(
                id,
                new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", PNG_HEAD),
                "채용 포스터",
                0
        );

        Long published = jobPostingService.publish(id);

        assertThat(published).isEqualTo(id);
    }
```

참고: 이 테스트 클래스에도 `recruit.posting-image.storage-root=build/test-posting-images` 프로퍼티를 추가한다:
`@SpringBootTest(properties = {"crypto.aes.key=22791194512954214612461221261067", "recruit.posting-image.storage-root=build/test-posting-images"})`

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.JobPostingServiceTest" --no-daemon
```
Expected: FAIL — `contentHtml_없이_공고를_생성할_수_있다`가 "공고 내용은 필수입니다"로 실패, publish 규칙 테스트 실패.

- [ ] **Step 3: 구현**

1. `JobPosting.java` — contentHtml 컬럼 완화:
```java
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String contentHtml;
```

2. `JobPostingCreateRequest.java` — `@NotBlank String contentHtml` → `String contentHtml`. `JobPostingUpdateRequest.java`도 동일하게(파일을 열어 contentHtml의 `@NotBlank`를 제거).

3. `JobPostingService.java`:
   - `validateRequest(...)`에서 contentHtml blank 검사 블록(`"공고 내용은 필수입니다."`) 삭제.
   - 필드 추가: `private final JobPostingImageService jobPostingImageService;` (생성자 주입은 `@RequiredArgsConstructor`가 처리. **순환 주입 아님** — JobPostingImageService는 JobPostingService를 참조하지 않는다.)
   - create 오버로드 추가(기존 `create(request)`는 유지):
```java
    @Transactional
    public Long create(
            JobPostingCreateRequest request,
            List<JobPostingImageMetaRequest> imageMetas,
            List<MultipartFile> imageFiles
    ) {
        Long id = create(request);
        jobPostingImageService.createImages(id, imageMetas, imageFiles);
        return id;
    }
```
   (import 추가: `com.shinyoung.recruit.dto.request.JobPostingImageMetaRequest`, `org.springframework.web.multipart.MultipartFile`)
   - `publish(...)`에 검증 추가 (`validateLayoutForPublish(jobPosting);` 다음 줄):
```java
        validateContentForPublish(jobPosting);
```
   메서드 추가:
```java
    /** 발행 조건: 이미지 ≥1장 또는 (레거시 데이터 호환) contentHtml 존재. */
    private void validateContentForPublish(JobPosting jobPosting) {
        boolean hasImages = jobPostingImageService.countImages(jobPosting.getId()) > 0;
        boolean hasLegacyContent = jobPosting.getContentHtml() != null && !jobPosting.getContentHtml().isBlank();
        if (!hasImages && !hasLegacyContent) {
            throw new InvalidJobPostingException("공고 본문 이미지가 최소 1장 필요합니다.");
        }
    }
```

4. 기존 테스트 보수: `JobPostingServiceTest`(및 컨트롤러/타 서비스 테스트)에서 "공고 내용은 필수" 실패를 기대하는 테스트가 있으면 삭제한다. 확인 명령:
```powershell
# recruit_back/recruit_backend 에서
Select-String -Path src\test\java\**\*.java -Pattern "공고 내용은 필수"
```

- [ ] **Step 4: 테스트 통과 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.JobPostingServiceTest" --tests "com.shinyoung.recruit.service.JobPostingImageServiceTest" --no-daemon
```
Expected: PASS. 기존 publish 테스트들은 contentHtml을 넣고 생성하므로 레거시 조건으로 계속 통과한다.

- [ ] **Step 5: 커밋**

```bash
git add -A src/main/java/com/shinyoung/recruit src/test/java/com/shinyoung/recruit
git commit -m "feat(job-posting): contentHtml 선택화, 생성 시 이미지 일괄 등록, 발행 본문 검증"
```

---

### Task 7: 상세 응답에 images 추가 (관리자 + 공개)

**Files:**
- Modify: `dto/response/JobPostingDetailResponse.java`, `dto/response/JobPostingPublicDetailResponse.java`
- Modify: `service/JobPostingService.java` (getJobPosting), `service/JobPostingPublicService.java` (getJobPosting)
- Test: `JobPostingServiceTest`, `JobPostingPublicServiceTest`에 추가

- [ ] **Step 1: 실패하는 테스트 추가**

`JobPostingServiceTest`:
```java
    @Test
    void 상세조회에_이미지_목록이_정렬순으로_포함된다() {
        Long id = jobPostingService.create(createRequest());
        jobPostingImageService.addImage(id,
                new org.springframework.mock.web.MockMultipartFile("file", "b.png", "image/png", PNG_HEAD), "포스터 2", 1);
        jobPostingImageService.addImage(id,
                new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", PNG_HEAD), "포스터 1", 0);

        JobPostingDetailResponse detail = jobPostingService.getJobPosting(id);

        assertThat(detail.images()).hasSize(2);
        assertThat(detail.images().get(0).altText()).isEqualTo("포스터 1");
    }
```

`JobPostingPublicServiceTest` (기존 클래스 관례에 맞춰; 발행된 공고를 만드는 기존 헬퍼를 재사용하고, `JobPostingImageService`와 storage-root 프로퍼티를 추가):
```java
    @Test
    void 공개_상세조회에_이미지_목록이_포함된다() {
        // 기존 테스트의 '발행 공고 생성' 패턴 재사용 후:
        jobPostingImageService.addImage(publishedPostingId,
                new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", PNG_HEAD), "채용 포스터", 0);

        JobPostingPublicDetailResponse detail = jobPostingPublicService.getJobPosting(publishedPostingId);

        assertThat(detail.images()).hasSize(1);
        assertThat(detail.images().get(0).altText()).isEqualTo("채용 포스터");
    }
```

- [ ] **Step 2: 실패 확인** (`images()` 미존재로 컴파일 실패)

- [ ] **Step 3: 구현**

1. `JobPostingDetailResponse` — 레코드 컴포넌트 맨 끝에 `List<JobPostingImageResponse> images` 추가. `from(jobPosting, now)`는 유지하되 `images`에 `List.of()`를 넣도록 하고, 새 오버로드 추가:
```java
    public static JobPostingDetailResponse from(JobPosting jobPosting, LocalDateTime now) {
        return from(jobPosting, now, List.of());
    }

    public static JobPostingDetailResponse from(
            JobPosting jobPosting,
            LocalDateTime now,
            List<JobPostingImageResponse> images
    ) {
        // 기존 생성 로직 그대로, 마지막 인자에 images
    }
```
2. `JobPostingService.getJobPosting`:
```java
    public JobPostingDetailResponse getJobPosting(Long id) {
        JobPosting jobPosting = findJobPostingDetail(id);
        return JobPostingDetailResponse.from(
                jobPosting,
                LocalDateTime.now(clock),
                jobPostingImageService.getImages(id)
        );
    }
```
3. `JobPostingPublicDetailResponse` — 동일하게 `List<JobPostingImageResponse> images` 컴포넌트 추가, `from(...)` 파라미터에 `List<JobPostingImageResponse> images` 추가(호출부가 1곳뿐이므로 오버로드 불필요).
4. `JobPostingPublicService`에 `private final JobPostingImageService jobPostingImageService;` 추가, `getJobPosting`에서 `jobPostingImageService.getImages(id)`를 넘긴다.
5. `JobPostingDetailResponse.from(jobPosting, now)`를 쓰는 다른 호출부가 있는지 확인(기본 오버로드가 남아 있으므로 컴파일은 유지된다):
```powershell
Select-String -Path src\main\java\**\*.java -Pattern "JobPostingDetailResponse.from"
```

- [ ] **Step 4: 테스트 통과 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.JobPostingServiceTest" --tests "com.shinyoung.recruit.service.JobPostingPublicServiceTest" --no-daemon
```
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add -A src/main/java/com/shinyoung/recruit src/test/java/com/shinyoung/recruit
git commit -m "feat(job-posting): 관리자/공개 상세 응답에 이미지 목록 추가"
```

---

### Task 8: 컨트롤러 — multipart 생성 + 이미지 API + 바이너리 서빙 (MockMvc TDD)

**Files:**
- Create: `controller/JobPostingImageController.java`
- Modify: `controller/JobPostingController.java` (multipart create), `controller/JobPostingPublicController.java` (공개 서빙)
- Test: `src/test/java/com/shinyoung/recruit/controller/JobPostingImageControllerTest.java` (신규)

- [ ] **Step 1: 실패하는 MockMvc 테스트 작성**

```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.ApplicationFormConfigRequest;
import com.shinyoung.recruit.dto.request.JobPositionRequest;
import com.shinyoung.recruit.dto.request.JobPostingCreateRequest;
import com.shinyoung.recruit.service.JobPostingImageService;
import com.shinyoung.recruit.service.JobPostingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "crypto.aes.key=22791194512954214612461221261067",
        "recruit.posting-image.storage-root=build/test-posting-images"
})
@AutoConfigureMockMvc
@Transactional
class JobPostingImageControllerTest {

    private static final byte[] PNG_HEAD = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingImageService jobPostingImageService;

    private RequestPostProcessor admin() {
        return user("admin").authorities(new SimpleGrantedAuthority("ROLE_RECRUIT_ADMIN"));
    }

    private JobPostingCreateRequest createRequest() {
        return new JobPostingCreateRequest(
                "이미지 공고",
                null,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 30, 18, 0),
                List.of(new JobPositionRequest("백엔드", 0)),
                new ApplicationFormConfigRequest(true, true, true, true, true, true, true)
        );
    }

    private Long createPosting() {
        return jobPostingService.create(createRequest());
    }

    private MockMultipartFile pngPart(String partName, String fileName) {
        return new MockMultipartFile(partName, fileName, "image/png", PNG_HEAD);
    }

    @Test
    void multipart로_공고와_이미지를_함께_생성한다() throws Exception {
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(createRequest()));
        MockMultipartFile metasPart = new MockMultipartFile(
                "imageMetas", "", MediaType.APPLICATION_JSON_VALUE,
                "[{\"altText\":\"채용 포스터\",\"sortOrder\":0}]".getBytes());

        mockMvc.perform(multipart("/api/admin/job-postings")
                        .file(requestPart)
                        .file(metasPart)
                        .file(pngPart("imageFiles", "poster.png"))
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    void 이미지를_개별_추가하고_상세에서_확인한다() throws Exception {
        Long postingId = createPosting();

        mockMvc.perform(multipart("/api/admin/job-postings/" + postingId + "/images")
                        .file(pngPart("file", "poster.png"))
                        .param("altText", "채용 포스터")
                        .with(admin()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/job-postings/" + postingId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images[0].altText").value("채용 포스터"));
    }

    @Test
    void 관리자_이미지_파일을_서빙한다() throws Exception {
        Long postingId = createPosting();
        Long imageId = jobPostingImageService.addImage(postingId, pngPart("file", "poster.png"), "포스터", 0);

        mockMvc.perform(get("/api/admin/job-postings/" + postingId + "/images/" + imageId + "/file").with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void draft_공고의_공개_이미지_서빙은_404() throws Exception {
        Long postingId = createPosting();
        Long imageId = jobPostingImageService.addImage(postingId, pngPart("file", "poster.png"), "포스터", 0);

        mockMvc.perform(get("/api/job-postings/" + postingId + "/images/" + imageId + "/file"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 발행_공고의_공개_이미지는_서빙된다() throws Exception {
        Long postingId = createPosting();
        Long imageId = jobPostingImageService.addImage(postingId, pngPart("file", "poster.png"), "포스터", 0);
        jobPostingService.publish(postingId);

        mockMvc.perform(get("/api/job-postings/" + postingId + "/images/" + imageId + "/file"))
                .andExpect(status().isOk());
    }

    @Test
    void 이미지_삭제와_순서변경() throws Exception {
        Long postingId = createPosting();
        Long first = jobPostingImageService.addImage(postingId, pngPart("file", "a.png"), "포스터 1", 0);
        Long second = jobPostingImageService.addImage(postingId, pngPart("file", "b.png"), "포스터 2", 1);

        mockMvc.perform(post("/api/admin/job-postings/" + postingId + "/images/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[" + second + "," + first + "]}")
                        .with(admin()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/job-postings/" + postingId + "/images/" + first + "/delete")
                        .with(admin()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/job-postings/" + postingId).with(admin()))
                .andExpect(jsonPath("$.data.images.length()").value(1))
                .andExpect(jsonPath("$.data.images[0].altText").value("포스터 2"));
    }

    @Test
    void 권한없는_사용자는_이미지_API에_접근할_수_없다() throws Exception {
        Long postingId = createPosting();

        mockMvc.perform(multipart("/api/admin/job-postings/" + postingId + "/images")
                        .file(pngPart("file", "poster.png"))
                        .param("altText", "포스터")
                        .with(user("applicant").authorities(new SimpleGrantedAuthority("ROLE_APPLICANT"))))
                .andExpect(status().isForbidden());
    }
}
```

참고: `JobPostingImageService.addImage`는 `MultipartFile`을 받으므로 테스트의 `pngPart(...)`(MockMultipartFile) 그대로 전달 가능.

- [ ] **Step 2: 실패 확인**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.controller.JobPostingImageControllerTest" --no-daemon
```
Expected: FAIL (엔드포인트 미존재 → 404/405 또는 컴파일 오류)

- [ ] **Step 3: 컨트롤러 구현**

1. `JobPostingController.java` — multipart 생성 변형 추가(기존 JSON `@PostMapping create`는 유지):
```java
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Long>> createWithImages(
            @Valid @RequestPart("request") JobPostingCreateRequest request,
            @RequestPart(value = "imageMetas", required = false) List<JobPostingImageMetaRequest> imageMetas,
            @RequestPart(value = "imageFiles", required = false) List<MultipartFile> imageFiles
    ) {
        return ResponseEntity.ok(ApiResponse.success(jobPostingService.create(request, imageMetas, imageFiles)));
    }
```
(import: `org.springframework.http.MediaType`, `org.springframework.web.multipart.MultipartFile`, `com.shinyoung.recruit.dto.request.JobPostingImageMetaRequest`, `java.util.List`)

2. `JobPostingImageController.java` 신규:
```java
package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.dto.request.JobPostingImageAltTextUpdateRequest;
import com.shinyoung.recruit.dto.request.JobPostingImageOrderRequest;
import com.shinyoung.recruit.dto.response.ApiResponse;
import com.shinyoung.recruit.service.JobPostingImageService;
import com.shinyoung.recruit.service.PostingImageResource;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/job-postings/{jobPostingId}/images")
public class JobPostingImageController {

    private final JobPostingImageService jobPostingImageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Long>> addImage(
            @PathVariable Long jobPostingId,
            @RequestPart("file") MultipartFile file,
            @RequestParam String altText,
            @RequestParam(required = false) Integer sortOrder
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                jobPostingImageService.addImage(jobPostingId, file, altText, sortOrder)));
    }

    @PostMapping("/{imageId}")
    public ResponseEntity<ApiResponse<Long>> updateAltText(
            @PathVariable Long jobPostingId,
            @PathVariable Long imageId,
            @RequestBody JobPostingImageAltTextUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                jobPostingImageService.updateAltText(jobPostingId, imageId, request.altText())));
    }

    @PostMapping("/{imageId}/delete")
    public ResponseEntity<ApiResponse<Long>> deleteImage(
            @PathVariable Long jobPostingId,
            @PathVariable Long imageId
    ) {
        jobPostingImageService.deleteImage(jobPostingId, imageId);
        return ResponseEntity.ok(ApiResponse.success(imageId));
    }

    @PostMapping("/order")
    public ResponseEntity<ApiResponse<Long>> reorder(
            @PathVariable Long jobPostingId,
            @RequestBody JobPostingImageOrderRequest request
    ) {
        jobPostingImageService.reorder(jobPostingId, request.imageIds());
        return ResponseEntity.ok(ApiResponse.success(jobPostingId));
    }

    @GetMapping("/{imageId}/file")
    public ResponseEntity<Resource> serveImage(
            @PathVariable Long jobPostingId,
            @PathVariable Long imageId
    ) {
        return toImageResponse(jobPostingImageService.loadAdminImage(jobPostingId, imageId));
    }

    static ResponseEntity<Resource> toImageResponse(PostingImageResource image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(image.resource());
    }
}
```

3. `JobPostingPublicController.java` — 공개 서빙 추가:
```java
    private final JobPostingImageService jobPostingImageService;   // 필드 추가

    @GetMapping("/{id}/images/{imageId}/file")
    public ResponseEntity<Resource> serveImage(@PathVariable Long id, @PathVariable Long imageId) {
        return JobPostingImageController.toImageResponse(jobPostingImageService.loadPublicImage(id, imageId));
    }
```
(import: `com.shinyoung.recruit.service.JobPostingImageService`, `org.springframework.core.io.Resource`)

- [ ] **Step 4: 테스트 통과 확인** (Step 2 명령, PASS 7 tests)

- [ ] **Step 5: 변경 범위(JobPosting 도메인) 테스트**

```powershell
$env:AES_SECRET_KEY='22791194512954214612461221261067'; .\gradlew.bat test --tests "com.shinyoung.recruit.service.JobPosting*" --tests "com.shinyoung.recruit.service.ImageSignatureValidatorTest" --tests "com.shinyoung.recruit.controller.JobPosting*" --no-daemon
```
Expected: PASS (기존 JobPostingControllerTest 등 변경 도메인 회귀 포함)

- [ ] **Step 6: 커밋**

```bash
git add -A src/main/java/com/shinyoung/recruit/controller src/test/java/com/shinyoung/recruit/controller
git commit -m "feat(job-posting): multipart 공고 생성, 이미지 CRUD/순서/서빙 API 추가"
```

---

### Task 9: 프론트 — 타입 + API 모듈 확장

**Files:**
- Modify: `recruit_front/src/types/jobPosting.ts`
- Modify: `recruit_front/src/api/adminJobPostingApi.ts`
- Modify: `recruit_front/src/api/boardApi.ts`

- [ ] **Step 1: 타입 추가** (`src/types/jobPosting.ts`에 추가; 기존 타입은 수정하지 않되 `JobPostingDetail`에 `images` 추가)

```ts
export interface JobPostingImage {
  id: number
  altText: string
  sortOrder: number
  contentType: string
  fileSize: number
}

// 기존 JobPostingDetail에 필드 추가:
//   images: JobPostingImage[]

export type AdminJobPostingStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'

export interface AdminJobPositionForm {
  positionName: string
  applicationType: 'NEW_GRADUATE' | 'EXPERIENCED' | 'NEW_GRADUATE_OR_EXPERIENCED'
  jobGroup: string | null
  jobTitle: string | null
  workLocation: string | null
  employmentType: 'FULL_TIME' | 'CONTRACT' | 'INTERN' | 'FREELANCE' | 'PART_TIME' | 'ETC'
  sortOrder: number
}

export interface AdminApplicationFormConfig {
  useEducation: boolean
  requireEducation: boolean | null
  useCareer: boolean
  requireCareer: boolean | null
  useCertificate: boolean
  requireCertificate: boolean | null
  useLanguage: boolean
  requireLanguage: boolean | null
  useMilitary: boolean
  requireMilitary: boolean | null
  useAward: boolean
  requireAward: boolean | null
  useGapPeriod: boolean
  requireGapPeriod: boolean | null
  useAttachment: boolean
}

/** POST /admin/job-postings 의 request JSON part. contentHtml은 deprecated라 보내지 않는다. */
export interface AdminJobPostingSaveRequest {
  title: string
  postingType: string | null
  summary: string | null
  receptionStartDateTime: string
  receptionEndDateTime: string
  displayStartDateTime: string | null
  displayEndDateTime: string | null
  visible: boolean
  pinned: boolean
  displayOrder: number
  jobPositions: AdminJobPositionForm[]
  applicationFormConfig: AdminApplicationFormConfig
}

export interface AdminJobPostingDetail {
  id: number
  title: string
  postingType: string
  summary: string | null
  contentHtml: string | null
  receptionStartDateTime: string
  receptionEndDateTime: string
  receptionStatus: 'UPCOMING' | 'ACCEPTING' | 'CLOSED'
  accepting: boolean
  status: AdminJobPostingStatus
  visible: boolean
  pinned: boolean
  displayOrder: number
  displayStartDateTime: string | null
  displayEndDateTime: string | null
  publishedAt: string | null
  closedAt: string | null
  createdAt: string
  updatedAt: string
  positionCount: number
  jobPositions: (AdminJobPositionForm & { id: number })[]
  applicationFormConfig: AdminApplicationFormConfig
  images: JobPostingImage[]
}

export interface NewPostingImage {
  file: File
  altText: string
}
```

- [ ] **Step 2: adminJobPostingApi 확장**

```ts
import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type {
  AdminJobPostingDetail,
  AdminJobPostingListItem,
  AdminJobPostingSaveRequest,
  NewPostingImage,
} from '@/types/jobPosting'

const UPLOAD_TIMEOUT_MS = 120000 // 기본 10초로는 다장 이미지 업로드가 끊길 수 있다.

export const adminJobPostingApi = {
  getJobPostings(page = 0, size = 50) {
    return apiClient.get<ApiResponse<PageResponse<AdminJobPostingListItem>>>('/admin/job-postings', {
      params: { page, size },
    })
  },
  getJobPosting(id: number) {
    return apiClient.get<ApiResponse<AdminJobPostingDetail>>(`/admin/job-postings/${id}`)
  },
  createJobPosting(request: AdminJobPostingSaveRequest, images: NewPostingImage[]) {
    const formData = new FormData()
    formData.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
    if (images.length > 0) {
      const metas = images.map((image, index) => ({ altText: image.altText, sortOrder: index }))
      formData.append('imageMetas', new Blob([JSON.stringify(metas)], { type: 'application/json' }))
      images.forEach((image) => formData.append('imageFiles', image.file))
    }
    return apiClient.post<ApiResponse<number>>('/admin/job-postings', formData, { timeout: UPLOAD_TIMEOUT_MS })
  },
  updateJobPosting(id: number, request: AdminJobPostingSaveRequest & { contentHtml: string | null }) {
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}`, request)
  },
  publishJobPosting(id: number) {
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}/publish`)
  },
  closeJobPosting(id: number) {
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}/close`)
  },
  addImage(id: number, file: File, altText: string, sortOrder?: number) {
    const formData = new FormData()
    formData.append('file', file)
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}/images`, formData, {
      params: { altText, sortOrder },
      timeout: UPLOAD_TIMEOUT_MS,
    })
  },
  updateImageAltText(id: number, imageId: number, altText: string) {
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}/images/${imageId}`, { altText })
  },
  deleteImage(id: number, imageId: number) {
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}/images/${imageId}/delete`)
  },
  reorderImages(id: number, imageIds: number[]) {
    return apiClient.post<ApiResponse<number>>(`/admin/job-postings/${id}/images/order`, { imageIds })
  },
  /** 관리자 미리보기용. 세션 쿠키가 필요해 <img src> 직접 참조 대신 blob으로 받는다. */
  fetchImageBlob(id: number, imageId: number) {
    return apiClient.get<Blob>(`/admin/job-postings/${id}/images/${imageId}/file`, { responseType: 'blob' })
  },
}
```

주의: 기존 `getJobPostings` 시그니처/동작은 그대로 보존한다(대시보드가 사용 중).

- [ ] **Step 3: boardApi에 공개 이미지 blob 함수 추가**

`src/api/boardApi.ts`에 추가:
```ts
  fetchJobPostingImageBlob(jobPostingId: number, imageId: number) {
    return apiClient.get<Blob>(`/job-postings/${jobPostingId}/images/${imageId}/file`, { responseType: 'blob' })
  },
```
그리고 `src/types/jobPosting.ts`의 `JobPostingDetail`에 `images: JobPostingImage[]` 필드를 추가한다.

- [ ] **Step 4: 타입 체크**

Run (`recruit_front/`):
```bash
npm run type-check
```
Expected: 오류 없음

- [ ] **Step 5: 커밋 (프론트 저장소)**

```bash
cd C:/Users/roehf/Desktop/recruit/recruit_front
git add src/types/jobPosting.ts src/api/adminJobPostingApi.ts src/api/boardApi.ts
git commit -m "feat(job-posting): 공고 이미지 타입/관리자·공개 API 모듈 확장"
```

---

### Task 10: 프론트 — 공용 이미지 스택 컴포넌트

**Files:**
- Create: `recruit_front/src/components/jobPosting/JobPostingImageStack.vue`

- [ ] **Step 1: 컴포넌트 작성**

지원자 상세와 관리자 미리보기가 공유한다. blob 로더를 주입받아 admin/public 경로 차이를 흡수한다.

```vue
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import type { JobPostingImage } from '@/types/jobPosting'

const props = defineProps<{
  images: JobPostingImage[]
  fetchImage: (imageId: number) => Promise<Blob>
}>()

const objectUrls = ref<Record<number, string>>({})
const failedIds = ref<Set<number>>(new Set())

const revokeAll = () => {
  Object.values(objectUrls.value).forEach((url) => URL.revokeObjectURL(url))
  objectUrls.value = {}
}

const loadImages = async (images: JobPostingImage[]) => {
  revokeAll()
  failedIds.value = new Set()
  for (const image of images) {
    try {
      const response = await props.fetchImage(image.id)
      objectUrls.value = { ...objectUrls.value, [image.id]: URL.createObjectURL(response) }
    } catch {
      failedIds.value = new Set([...failedIds.value, image.id])
    }
  }
}

watch(() => props.images, (images) => { void loadImages(images) }, { immediate: true })
onBeforeUnmount(revokeAll)
</script>

<template>
  <div class="posting-image-stack">
    <template v-for="image in images" :key="image.id">
      <img
        v-if="objectUrls[image.id]"
        :src="objectUrls[image.id]"
        :alt="image.altText"
        class="posting-image"
      />
      <p v-else-if="failedIds.has(image.id)" class="image-error">이미지를 불러오지 못했습니다.</p>
    </template>
  </div>
</template>

<style scoped>
.posting-image-stack {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
}
.posting-image {
  display: block;
  max-width: 100%;
  height: auto;
}
.image-error {
  color: #999;
  padding: 16px 0;
}
</style>
```

- [ ] **Step 2: 타입 체크 후 커밋**

```bash
npm run type-check
git add src/components/jobPosting/JobPostingImageStack.vue
git commit -m "feat(job-posting): 공고 이미지 스택 공용 컴포넌트 추가"
```

---

### Task 11: 프론트 — 지원자 상세 렌더 교체

**Files:**
- Modify: `recruit_front/src/views/applicant/ApplicationDetailView.vue`

- [ ] **Step 1: 이미지 우선 렌더로 변경**

1. script에 추가:
```ts
import JobPostingImageStack from '@/components/jobPosting/JobPostingImageStack.vue'
import type { JobPostingImage } from '@/types/jobPosting'

const jobPostImages = ref<JobPostingImage[]>([])

const fetchPostingImage = (imageId: number) =>
  boardApi.fetchJobPostingImageBlob(Number(route.params.jobPostingId), imageId).then((res) => res.data)
```
2. 상세 로딩부(현재 `jobPostContentHtml.value = result.data.data.contentHtml;` 위치)에 추가:
```ts
jobPostImages.value = result.data.data.images ?? []
```
3. 템플릿의 본문 영역(현재 `<template v-if="jobPostContentHtml">` ... `<HtmlView :content="jobPostContentHtml" />` 부분)을 아래로 교체 — **이미지가 있으면 이미지 스택, 없으면 레거시 contentHtml 폴백**:
```html
<template v-if="jobPostImages.length > 0">
  <a-card class="form-content-card" :bordered="false">
    <JobPostingImageStack :images="jobPostImages" :fetch-image="fetchPostingImage" />
  </a-card>
</template>
<template v-else-if="jobPostContentHtml">
  <a-card class="form-content-card" :bordered="false">
    <HtmlView :content="jobPostContentHtml" />
  </a-card>
</template>
```
(기존 카드 래퍼 구조·클래스는 파일의 실제 마크업을 그대로 유지하며 조건만 확장한다.)

- [ ] **Step 2: 타입 체크 후 커밋**

```bash
npm run type-check
git add src/views/applicant/ApplicationDetailView.vue
git commit -m "feat(job-posting): 지원자 공고 상세를 이미지 렌더 우선으로 교체(레거시 contentHtml 폴백)"
```

---

### Task 12: 프론트 — 관리자 공고 목록 화면 + 라우트

**Files:**
- Create: `recruit_front/src/views/admin/jobPosting/AdminJobPostingListView.vue`
- Modify: `recruit_front/src/routes/adminRoutes.ts`

- [ ] **Step 1: 라우트 4개 추가** (`adminRoutes.ts`의 children에, 기존 lazy-load·네이밍 관례 유지)

```ts
      {
        path: 'job-postings',
        name: 'AdminJobPostingList',
        component: () => import('@/views/admin/jobPosting/AdminJobPostingListView.vue'),
      },
      {
        path: 'job-postings/new',
        name: 'AdminJobPostingCreate',
        component: () => import('@/views/admin/jobPosting/AdminJobPostingFormView.vue'),
      },
      {
        path: 'job-postings/:id',
        name: 'AdminJobPostingDetail',
        component: () => import('@/views/admin/jobPosting/AdminJobPostingDetailView.vue'),
      },
      {
        path: 'job-postings/:id/edit',
        name: 'AdminJobPostingEdit',
        component: () => import('@/views/admin/jobPosting/AdminJobPostingFormView.vue'),
      },
```
(Form/Detail 파일은 Task 13·14에서 생성되므로, 이 시점 type-check는 Task 14 완료 후 실행한다. 커밋도 Task 14에서 묶는다.)

- [ ] **Step 2: 목록 화면 작성**

```vue
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { adminJobPostingApi } from '@/api/adminJobPostingApi'
import { getApiErrorMessage } from '@/api/apiError'
import type { AdminJobPostingListItem } from '@/types/jobPosting'

const router = useRouter()
const loading = ref(false)
const postings = ref<AdminJobPostingListItem[]>([])
const page = ref(0)
const pageSize = 10
const totalElements = ref(0)

const statusLabelMap: Record<string, string> = {
  DRAFT: '작성 중',
  PUBLISHED: '게시 중',
  CLOSED: '마감',
}
const statusColorMap: Record<string, string> = {
  DRAFT: 'default',
  PUBLISHED: 'green',
  CLOSED: 'red',
}
const postingTypeLabelMap: Record<string, string> = {
  PUBLIC_RECRUITMENT: '공개채용',
  EXPERIENCED_RECRUITMENT: '경력채용',
  INTERN_RECRUITMENT: '인턴채용',
  ROLLING_RECRUITMENT: '수시채용',
}

const columns = [
  { title: '제목', dataIndex: 'title', key: 'title' },
  { title: '유형', key: 'postingType', width: 110 },
  { title: '상태', key: 'status', width: 90 },
  { title: '접수 기간', key: 'reception', width: 300 },
  { title: '모집분야', dataIndex: 'positionCount', key: 'positionCount', width: 90 },
]

const pagination = computed(() => ({
  current: page.value + 1,
  pageSize,
  total: totalElements.value,
  showSizeChanger: false,
}))

const formatDateTime = (value: string) => value.replace('T', ' ').slice(0, 16)

const loadPostings = async () => {
  loading.value = true
  try {
    const response = await adminJobPostingApi.getJobPostings(page.value, pageSize)
    postings.value = response.data.data.content
    totalElements.value = response.data.data.totalElements
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const handleTableChange = (nextPagination: { current?: number }) => {
  page.value = (nextPagination.current ?? 1) - 1
  void loadPostings()
}

const goToDetail = (record: AdminJobPostingListItem) => {
  void router.push({ name: 'AdminJobPostingDetail', params: { id: record.id } })
}

const goToCreate = () => {
  void router.push({ name: 'AdminJobPostingCreate' })
}

onMounted(loadPostings)
</script>

<template>
  <div class="job-posting-list">
    <header class="page-header">
      <div>
        <h2 class="page-title">공고 목록</h2>
        <p class="page-description">채용 공고를 조회하고 상세에서 검수·발행합니다.</p>
      </div>
      <a-button type="primary" @click="goToCreate">공고 등록</a-button>
    </header>

    <a-table
      :columns="columns"
      :data-source="postings"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      :custom-row="(record: AdminJobPostingListItem) => ({ onClick: () => goToDetail(record) })"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'postingType'">
          {{ postingTypeLabelMap[record.postingType] ?? record.postingType }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColorMap[record.status]">{{ statusLabelMap[record.status] ?? record.status }}</a-tag>
        </template>
        <template v-else-if="column.key === 'reception'">
          {{ formatDateTime(record.receptionStartDateTime) }} ~ {{ formatDateTime(record.receptionEndDateTime) }}
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.job-posting-list {
  padding: 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-title {
  margin: 0 0 4px;
}
.page-description {
  margin: 0;
  color: #888;
}
:deep(.ant-table-row) {
  cursor: pointer;
}
</style>
```

(커밋은 Task 14에서 폼·상세와 함께.)

---

### Task 13: 프론트 — 관리자 공고 등록/수정 폼 (이미지 업로더 포함)

**Files:**
- Create: `recruit_front/src/views/admin/jobPosting/AdminJobPostingFormView.vue`

- [ ] **Step 1: 폼 화면 작성**

한 화면 일괄 저장: 신규는 multipart 생성 1회, 수정은 기본정보 저장 후 이미지 diff(삭제→추가→altText→순서) 적용.

```vue
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { adminJobPostingApi } from '@/api/adminJobPostingApi'
import { getApiErrorMessage } from '@/api/apiError'
import type {
  AdminApplicationFormConfig,
  AdminJobPositionForm,
  AdminJobPostingSaveRequest,
  JobPostingImage,
} from '@/types/jobPosting'

const MAX_IMAGES = 10
const MAX_IMAGE_SIZE = 10 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

interface EditableImage {
  key: string
  id: number | null          // 기존 이미지면 서버 id, 신규면 null
  file: File | null          // 신규 파일
  altText: string
  originalAltText: string    // 수정 여부 판단용
  previewUrl: string
}

const route = useRoute()
const router = useRouter()
const editingId = computed(() => (route.params.id ? Number(route.params.id) : null))
const isEdit = computed(() => editingId.value !== null)

const loading = ref(false)
const saving = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const title = ref('')
const postingType = ref('PUBLIC_RECRUITMENT')
const summary = ref('')
const receptionStart = ref<string | null>(null)
const receptionEnd = ref<string | null>(null)
const displayStart = ref<string | null>(null)
const displayEnd = ref<string | null>(null)
const visible = ref(true)
const pinned = ref(false)
const displayOrder = ref(0)
const contentHtmlLegacy = ref<string | null>(null) // 수정 시 기존 값 보존용(화면 미노출)

const jobPositions = ref<AdminJobPositionForm[]>([
  { positionName: '', applicationType: 'NEW_GRADUATE_OR_EXPERIENCED', jobGroup: null, jobTitle: null, workLocation: null, employmentType: 'FULL_TIME', sortOrder: 0 },
])

const formConfig = ref<AdminApplicationFormConfig>({
  useEducation: true, requireEducation: null,
  useCareer: true, requireCareer: null,
  useCertificate: true, requireCertificate: null,
  useLanguage: true, requireLanguage: null,
  useMilitary: true, requireMilitary: null,
  useAward: true, requireAward: null,
  useGapPeriod: true, requireGapPeriod: null,
  useAttachment: false,
})

const images = ref<EditableImage[]>([])
const removedImageIds = ref<number[]>([])

const postingTypeOptions = [
  { value: 'PUBLIC_RECRUITMENT', label: '공개채용' },
  { value: 'EXPERIENCED_RECRUITMENT', label: '경력채용' },
  { value: 'INTERN_RECRUITMENT', label: '인턴채용' },
  { value: 'ROLLING_RECRUITMENT', label: '수시채용' },
]
const applicationTypeOptions = [
  { value: 'NEW_GRADUATE', label: '신입' },
  { value: 'EXPERIENCED', label: '경력' },
  { value: 'NEW_GRADUATE_OR_EXPERIENCED', label: '신입/경력' },
]
const employmentTypeOptions = [
  { value: 'FULL_TIME', label: '정규직' },
  { value: 'CONTRACT', label: '계약직' },
  { value: 'INTERN', label: '인턴' },
  { value: 'FREELANCE', label: '프리랜서' },
  { value: 'PART_TIME', label: '파트타임' },
  { value: 'ETC', label: '기타' },
]
const formSections: { useKey: keyof AdminApplicationFormConfig; requireKey: keyof AdminApplicationFormConfig; label: string }[] = [
  { useKey: 'useEducation', requireKey: 'requireEducation', label: '학력' },
  { useKey: 'useCareer', requireKey: 'requireCareer', label: '경력' },
  { useKey: 'useCertificate', requireKey: 'requireCertificate', label: '자격증' },
  { useKey: 'useLanguage', requireKey: 'requireLanguage', label: '어학' },
  { useKey: 'useMilitary', requireKey: 'requireMilitary', label: '병역' },
  { useKey: 'useAward', requireKey: 'requireAward', label: '포상' },
  { useKey: 'useGapPeriod', requireKey: 'requireGapPeriod', label: '공백기간' },
]

let imageKeySeq = 0

const revokePreviews = () => {
  images.value.filter((image) => image.file).forEach((image) => URL.revokeObjectURL(image.previewUrl))
}

const openFilePicker = () => fileInput.value?.click()

const handleFilesSelected = (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  for (const file of files) {
    if (images.value.length >= MAX_IMAGES) {
      message.warning(`이미지는 최대 ${MAX_IMAGES}장까지 등록할 수 있습니다.`)
      break
    }
    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
      message.warning(`${file.name}: jpg/png/webp 형식만 등록할 수 있습니다.`)
      continue
    }
    if (file.size > MAX_IMAGE_SIZE) {
      message.warning(`${file.name}: 장당 10MB 이하만 등록할 수 있습니다.`)
      continue
    }
    images.value.push({
      key: `new-${imageKeySeq++}`,
      id: null,
      file,
      altText: '',
      originalAltText: '',
      previewUrl: URL.createObjectURL(file),
    })
  }
}

const removeImage = (index: number) => {
  const [removed] = images.value.splice(index, 1)
  if (removed.id !== null) {
    removedImageIds.value.push(removed.id)
  } else {
    URL.revokeObjectURL(removed.previewUrl)
  }
}

const moveImage = (index: number, delta: number) => {
  const target = index + delta
  if (target < 0 || target >= images.value.length) return
  const next = [...images.value]
  ;[next[index], next[target]] = [next[target], next[index]]
  images.value = next
}

const addPosition = () => {
  jobPositions.value.push({
    positionName: '',
    applicationType: 'NEW_GRADUATE_OR_EXPERIENCED',
    jobGroup: null,
    jobTitle: null,
    workLocation: null,
    employmentType: 'FULL_TIME',
    sortOrder: jobPositions.value.length,
  })
}

const removePosition = (index: number) => {
  if (jobPositions.value.length <= 1) {
    message.warning('모집분야는 최소 1개 이상이어야 합니다.')
    return
  }
  jobPositions.value.splice(index, 1)
}

const validate = (): string | null => {
  if (!title.value.trim()) return '공고 제목을 입력해 주세요.'
  if (!receptionStart.value || !receptionEnd.value) return '접수 기간을 입력해 주세요.'
  if (receptionEnd.value <= receptionStart.value) return '접수 종료일시는 시작일시 이후여야 합니다.'
  if (jobPositions.value.some((position) => !position.positionName.trim())) return '모집분야명을 입력해 주세요.'
  if (images.value.some((image) => !image.altText.trim())) return '모든 이미지에 대체 텍스트를 입력해 주세요.'
  return null
}

const buildSaveRequest = (): AdminJobPostingSaveRequest => ({
  title: title.value.trim(),
  postingType: postingType.value,
  summary: summary.value.trim() || null,
  receptionStartDateTime: receptionStart.value!,
  receptionEndDateTime: receptionEnd.value!,
  displayStartDateTime: displayStart.value,
  displayEndDateTime: displayEnd.value,
  visible: visible.value,
  pinned: pinned.value,
  displayOrder: displayOrder.value,
  jobPositions: jobPositions.value.map((position, index) => ({ ...position, sortOrder: index })),
  applicationFormConfig: formConfig.value,
})

const save = async () => {
  const errorMessage = validate()
  if (errorMessage) {
    message.warning(errorMessage)
    return
  }
  saving.value = true
  try {
    if (!isEdit.value) {
      const response = await adminJobPostingApi.createJobPosting(
        buildSaveRequest(),
        images.value.map((image) => ({ file: image.file!, altText: image.altText.trim() })),
      )
      message.success('공고가 등록되었습니다. 미리보기로 검수 후 발행해 주세요.')
      void router.push({ name: 'AdminJobPostingDetail', params: { id: response.data.data } })
      return
    }

    const id = editingId.value!
    await adminJobPostingApi.updateJobPosting(id, { ...buildSaveRequest(), contentHtml: contentHtmlLegacy.value })
    // 이미지 diff: 삭제 → 추가(id 확보) → altText 변경 → 전체 순서 재지정
    for (const removedId of removedImageIds.value) {
      await adminJobPostingApi.deleteImage(id, removedId)
    }
    for (const image of images.value) {
      if (image.id === null) {
        const response = await adminJobPostingApi.addImage(id, image.file!, image.altText.trim())
        image.id = response.data.data
      } else if (image.altText.trim() !== image.originalAltText) {
        await adminJobPostingApi.updateImageAltText(id, image.id, image.altText.trim())
      }
    }
    if (images.value.length > 0) {
      await adminJobPostingApi.reorderImages(id, images.value.map((image) => image.id!))
    }
    message.success('공고가 저장되었습니다.')
    void router.push({ name: 'AdminJobPostingDetail', params: { id } })
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 저장에 실패했습니다.'))
  } finally {
    saving.value = false
  }
}

const loadForEdit = async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const response = await adminJobPostingApi.getJobPosting(editingId.value!)
    const detail = response.data.data
    title.value = detail.title
    postingType.value = detail.postingType
    summary.value = detail.summary ?? ''
    receptionStart.value = detail.receptionStartDateTime
    receptionEnd.value = detail.receptionEndDateTime
    displayStart.value = detail.displayStartDateTime
    displayEnd.value = detail.displayEndDateTime
    visible.value = detail.visible
    pinned.value = detail.pinned
    displayOrder.value = detail.displayOrder
    contentHtmlLegacy.value = detail.contentHtml
    jobPositions.value = detail.jobPositions.map(({ id: _id, ...position }) => position)
    formConfig.value = detail.applicationFormConfig
    const loaded: EditableImage[] = []
    for (const image of detail.images) {
      const blob = await adminJobPostingApi.fetchImageBlob(editingId.value!, image.id)
      loaded.push({
        key: `existing-${image.id}`,
        id: image.id,
        file: null,
        altText: image.altText,
        originalAltText: image.altText,
        previewUrl: URL.createObjectURL(blob.data),
      })
    }
    images.value = loaded
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 정보를 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

onMounted(loadForEdit)
onBeforeUnmount(() => {
  images.value.forEach((image) => URL.revokeObjectURL(image.previewUrl))
})
</script>

<template>
  <div class="job-posting-form">
    <header class="page-header">
      <h2 class="page-title">{{ isEdit ? '공고 수정' : '공고 등록' }}</h2>
      <p class="page-description">저장하면 작성 중(draft) 상태로 보관되며, 상세 화면에서 미리보기 검수 후 발행합니다.</p>
    </header>

    <a-spin :spinning="loading">
      <a-card title="기본 정보" :bordered="false" class="form-card">
        <div class="field-grid">
          <label class="field field-wide">
            <span class="field-label">공고 제목 *</span>
            <a-input v-model:value="title" placeholder="예: 2026년 신입사원 공개채용" />
          </label>
          <label class="field">
            <span class="field-label">공고 유형</span>
            <a-select v-model:value="postingType" :options="postingTypeOptions" />
          </label>
          <label class="field">
            <span class="field-label">표시 순서</span>
            <a-input-number v-model:value="displayOrder" :min="0" style="width: 100%" />
          </label>
          <label class="field field-wide">
            <span class="field-label">요약</span>
            <a-textarea v-model:value="summary" :rows="2" :maxlength="500" placeholder="목록에 노출되는 짧은 설명 (HTML 불가)" />
          </label>
          <label class="field">
            <span class="field-label">접수 시작일시 *</span>
            <a-date-picker v-model:value="receptionStart" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
          </label>
          <label class="field">
            <span class="field-label">접수 종료일시 *</span>
            <a-date-picker v-model:value="receptionEnd" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
          </label>
          <label class="field">
            <span class="field-label">노출 시작일시</span>
            <a-date-picker v-model:value="displayStart" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
          </label>
          <label class="field">
            <span class="field-label">노출 종료일시</span>
            <a-date-picker v-model:value="displayEnd" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
          </label>
          <label class="field">
            <span class="field-label">노출 여부</span>
            <a-switch v-model:checked="visible" />
          </label>
          <label class="field">
            <span class="field-label">상단 고정</span>
            <a-switch v-model:checked="pinned" />
          </label>
        </div>
      </a-card>

      <a-card :bordered="false" class="form-card">
        <template #title>공고 이미지 ({{ images.length }}/{{ MAX_IMAGES }})</template>
        <template #extra>
          <a-button @click="openFilePicker" :disabled="images.length >= MAX_IMAGES">이미지 추가</a-button>
        </template>
        <input
          ref="fileInput"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          class="hidden-input"
          @change="handleFilesSelected"
        />
        <p v-if="images.length === 0" class="state-message">
          공고 본문으로 노출할 포스터 이미지를 추가해 주세요. (jpg/png/webp, 장당 10MB, 최대 10장)
        </p>
        <div v-for="(image, index) in images" :key="image.key" class="image-row">
          <img :src="image.previewUrl" :alt="image.altText || '공고 이미지 미리보기'" class="image-thumb" />
          <div class="image-meta">
            <a-input v-model:value="image.altText" :maxlength="200" placeholder="대체 텍스트(필수) — 예: 2026 신입 공채 모집 부문 안내" />
          </div>
          <div class="image-actions">
            <a-button size="small" :disabled="index === 0" @click="moveImage(index, -1)">위로</a-button>
            <a-button size="small" :disabled="index === images.length - 1" @click="moveImage(index, 1)">아래로</a-button>
            <a-button size="small" danger @click="removeImage(index)">삭제</a-button>
          </div>
        </div>
      </a-card>

      <a-card title="모집분야" :bordered="false" class="form-card">
        <template #extra>
          <a-button @click="addPosition">모집분야 추가</a-button>
        </template>
        <div v-for="(position, index) in jobPositions" :key="index" class="position-row">
          <a-input v-model:value="position.positionName" placeholder="모집분야명 *" class="position-name" />
          <a-select v-model:value="position.applicationType" :options="applicationTypeOptions" class="position-select" />
          <a-select v-model:value="position.employmentType" :options="employmentTypeOptions" class="position-select" />
          <a-input v-model:value="position.jobGroup" placeholder="직군" class="position-input" />
          <a-input v-model:value="position.jobTitle" placeholder="담당 직무" class="position-input" />
          <a-input v-model:value="position.workLocation" placeholder="근무지" class="position-input" />
          <a-button danger size="small" @click="removePosition(index)">삭제</a-button>
        </div>
      </a-card>

      <a-card title="지원서 양식 구성" :bordered="false" class="form-card">
        <div class="config-grid">
          <div v-for="section in formSections" :key="section.useKey" class="config-item">
            <a-checkbox
              :checked="Boolean(formConfig[section.useKey])"
              @update:checked="(checked: boolean) => { (formConfig[section.useKey] as boolean) = checked; if (!checked) (formConfig[section.requireKey] as boolean | null) = false }"
            >
              {{ section.label }}
            </a-checkbox>
            <a-checkbox
              :checked="Boolean(formConfig[section.requireKey])"
              :disabled="!formConfig[section.useKey]"
              @update:checked="(checked: boolean) => { (formConfig[section.requireKey] as boolean | null) = checked }"
            >
              필수
            </a-checkbox>
          </div>
          <div class="config-item">
            <a-checkbox v-model:checked="formConfig.useAttachment">첨부파일</a-checkbox>
          </div>
        </div>
      </a-card>

      <div class="form-actions">
        <a-button @click="router.back()">취소</a-button>
        <a-button type="primary" :loading="saving" @click="save">저장</a-button>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.job-posting-form {
  padding: 24px;
  max-width: 1080px;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  margin: 0 0 4px;
}
.page-description {
  margin: 0;
  color: #888;
}
.form-card {
  margin-bottom: 16px;
}
.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.field-wide {
  grid-column: 1 / -1;
}
.field-label {
  font-size: 13px;
  color: #666;
}
.hidden-input {
  display: none;
}
.state-message {
  color: #999;
}
.image-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}
.image-thumb {
  width: 96px;
  height: 96px;
  object-fit: contain;
  background: #fafafa;
  border: 1px solid #eee;
}
.image-meta {
  flex: 1;
}
.image-actions {
  display: flex;
  gap: 4px;
}
.position-row {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
}
.position-name {
  width: 180px;
}
.position-select {
  width: 130px;
}
.position-input {
  width: 140px;
}
.config-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 16px;
}
.config-item {
  display: flex;
  gap: 12px;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
```

주의: `a-date-picker`의 `value-format` 문자열 바인딩이 이 프로젝트의 ant-design-vue 4에서 string ref와 함께 동작하는지 type-check로 확인하고, 타입 오류가 나면 `v-model:value`를 `Dayjs` ref로 바꾸고 저장 시 `.format('YYYY-MM-DDTHH:mm:ss')`로 변환한다.

(커밋은 Task 14에서 함께.)

---

### Task 14: 프론트 — 관리자 공고 상세(미리보기·발행·마감)

**Files:**
- Create: `recruit_front/src/views/admin/jobPosting/AdminJobPostingDetailView.vue`

- [ ] **Step 1: 상세 화면 작성**

```vue
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import { adminJobPostingApi } from '@/api/adminJobPostingApi'
import { getApiErrorMessage } from '@/api/apiError'
import JobPostingImageStack from '@/components/jobPosting/JobPostingImageStack.vue'
import type { AdminJobPostingDetail } from '@/types/jobPosting'

const route = useRoute()
const router = useRouter()
const postingId = computed(() => Number(route.params.id))

const loading = ref(false)
const acting = ref(false)
const detail = ref<AdminJobPostingDetail | null>(null)

const statusLabelMap: Record<string, string> = {
  DRAFT: '작성 중',
  PUBLISHED: '게시 중',
  CLOSED: '마감',
}
const statusColorMap: Record<string, string> = {
  DRAFT: 'default',
  PUBLISHED: 'green',
  CLOSED: 'red',
}
const postingTypeLabelMap: Record<string, string> = {
  PUBLIC_RECRUITMENT: '공개채용',
  EXPERIENCED_RECRUITMENT: '경력채용',
  INTERN_RECRUITMENT: '인턴채용',
  ROLLING_RECRUITMENT: '수시채용',
}

const formatDateTime = (value: string | null) => (value ? value.replace('T', ' ').slice(0, 16) : '-')

const fetchImage = (imageId: number) =>
  adminJobPostingApi.fetchImageBlob(postingId.value, imageId).then((response) => response.data)

const loadDetail = async () => {
  loading.value = true
  try {
    const response = await adminJobPostingApi.getJobPosting(postingId.value)
    detail.value = response.data.data
  } catch (error) {
    message.error(getApiErrorMessage(error, '공고 정보를 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

const publish = () => {
  Modal.confirm({
    title: '공고를 발행할까요?',
    content: '발행하면 지원자 화면에 노출됩니다. 미리보기 검수를 완료했는지 확인해 주세요.',
    okText: '발행',
    cancelText: '취소',
    async onOk() {
      acting.value = true
      try {
        await adminJobPostingApi.publishJobPosting(postingId.value)
        message.success('공고가 발행되었습니다.')
        await loadDetail()
      } catch (error) {
        message.error(getApiErrorMessage(error, '발행에 실패했습니다.'))
      } finally {
        acting.value = false
      }
    },
  })
}

const close = () => {
  Modal.confirm({
    title: '공고를 마감할까요?',
    content: '마감한 공고는 수정할 수 없습니다.',
    okText: '마감',
    cancelText: '취소',
    async onOk() {
      acting.value = true
      try {
        await adminJobPostingApi.closeJobPosting(postingId.value)
        message.success('공고가 마감되었습니다.')
        await loadDetail()
      } catch (error) {
        message.error(getApiErrorMessage(error, '마감에 실패했습니다.'))
      } finally {
        acting.value = false
      }
    },
  })
}

const goToEdit = () => {
  void router.push({ name: 'AdminJobPostingEdit', params: { id: postingId.value } })
}

onMounted(loadDetail)
</script>

<template>
  <div class="job-posting-detail">
    <a-spin :spinning="loading">
      <template v-if="detail">
        <header class="page-header">
          <div>
            <h2 class="page-title">
              {{ detail.title }}
              <a-tag :color="statusColorMap[detail.status]">{{ statusLabelMap[detail.status] }}</a-tag>
            </h2>
            <p class="page-description">아래 미리보기는 지원자에게 보이는 본문과 동일합니다.</p>
          </div>
          <div class="header-actions">
            <a-button @click="router.push({ name: 'AdminJobPostingList' })">목록</a-button>
            <a-button v-if="detail.status !== 'CLOSED'" @click="goToEdit">수정</a-button>
            <a-button v-if="detail.status === 'DRAFT'" type="primary" :loading="acting" @click="publish">발행</a-button>
            <a-button v-if="detail.status === 'PUBLISHED'" danger :loading="acting" @click="close">마감</a-button>
          </div>
        </header>

        <a-descriptions bordered :column="2" size="small" class="info-block">
          <a-descriptions-item label="공고 유형">{{ postingTypeLabelMap[detail.postingType] ?? detail.postingType }}</a-descriptions-item>
          <a-descriptions-item label="표시 순서">{{ detail.displayOrder }}</a-descriptions-item>
          <a-descriptions-item label="접수 기간">
            {{ formatDateTime(detail.receptionStartDateTime) }} ~ {{ formatDateTime(detail.receptionEndDateTime) }}
          </a-descriptions-item>
          <a-descriptions-item label="노출 기간">
            {{ formatDateTime(detail.displayStartDateTime) }} ~ {{ formatDateTime(detail.displayEndDateTime) }}
          </a-descriptions-item>
          <a-descriptions-item label="노출 여부">{{ detail.visible ? '노출' : '비노출' }}</a-descriptions-item>
          <a-descriptions-item label="상단 고정">{{ detail.pinned ? '고정' : '-' }}</a-descriptions-item>
          <a-descriptions-item label="발행일시">{{ formatDateTime(detail.publishedAt) }}</a-descriptions-item>
          <a-descriptions-item label="마감일시">{{ formatDateTime(detail.closedAt) }}</a-descriptions-item>
        </a-descriptions>

        <a-card title="본문 미리보기 (지원자 화면과 동일)" :bordered="false" class="preview-card">
          <JobPostingImageStack v-if="detail.images.length > 0" :images="detail.images" :fetch-image="fetchImage" />
          <p v-else class="state-message">
            등록된 이미지가 없습니다. 발행하려면 이미지를 최소 1장 등록해 주세요.
          </p>
        </a-card>
      </template>
    </a-spin>
  </div>
</template>

<style scoped>
.job-posting-detail {
  padding: 24px;
  max-width: 1080px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-title {
  margin: 0 0 4px;
}
.page-description {
  margin: 0;
  color: #888;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.info-block {
  margin-bottom: 16px;
}
.state-message {
  color: #999;
}
</style>
```

- [ ] **Step 2: 타입 체크**

```bash
npm run type-check
```
Expected: 오류 없음. (Task 12의 라우트가 참조하는 3개 파일이 모두 존재하는 시점.)

- [ ] **Step 3: 커밋 (관리자 화면 일괄)**

```bash
git add src/routes/adminRoutes.ts src/views/admin/jobPosting
git commit -m "feat(job-posting): 관리자 공고 목록/등록·수정 폼/상세(미리보기·발행) 화면 추가"
```

---

### Task 15: 브라우저 검증 (dev 서버)

- [ ] **Step 1: 백엔드/프론트 dev 서버 기동 후 시나리오 확인**

`.claude/launch.json`의 프론트 dev 서버 프리뷰를 사용한다(하네스에 이미 구성됨). 백엔드는 별도 실행이 필요하면 사용자에게 요청한다. 확인 시나리오:

1. `/admin/job-postings/new` — 이미지 3장 추가(순서 변경, altText 입력) 후 저장 → 상세로 이동, draft 뱃지 확인.
2. 상세 미리보기에 이미지 3장이 순서대로 노출.
3. 발행 → 지원자 공고 상세(`/applicant/:id/detail`)에서 동일 이미지 노출 확인.
4. draft 상태의 다른 공고에서 공개 이미지 URL 직접 호출 → 404 확인.
5. 수정 화면에서 1장 삭제 + 1장 추가 + 순서 변경 저장 → 반영 확인.

콘솔 오류(`read_console_messages`)와 네트워크 4xx/5xx(`read_network_requests`)가 없는지 확인하고, 미리보기 스크린샷을 결과 보고에 첨부한다.

---

### Task 16: 계약 🟢 확정 + 보고

**Files:**
- Modify: `C:\Users\roehf\Desktop\recruit\api-contract.md`

- [ ] **Step 1: 계약 확정**

Task 0에서 추가한 섹션의 🟡를 🟢로 바꾸고, 구현과 다르게 확정된 부분(필드명, 에러 메시지 등)을 실제 코드와 일치하게 수정한다. 특히:
- multipart part 이름: `request` / `imageMetas` / `imageFiles` (확정)
- 발행 규칙: "이미지 ≥1 또는 레거시 contentHtml 존재" (확정)
- `JobPostingUpdateRequest`의 contentHtml optional 변경도 명시

- [ ] **Step 2: 커밋 (recruit 저장소)**

```bash
cd C:/Users/roehf/Desktop/recruit
git add api-contract.md
git commit -m "docs(contract): 공고 이미지 입력 계약 확정 🟢"
```

- [ ] **Step 3: 최종 보고** (recruit/CLAUDE.md §3-7 형식)

- 변경 파일(백엔드/프론트/계약), 테스트 결과(실행 명령 포함), 계약 변경분, 남은 이슈.
- **운영 안내 포함**: 메뉴는 코드가 아니라 `/admin/menus` 메뉴 관리 화면에서 DB 등록 — 대메뉴 "공고 관리"(path 없음) + 소메뉴 "공고 목록"(`/admin/job-postings`) / "공고 등록"(`/admin/job-postings/new`), 아이콘은 `ADMIN_MENU_ICONS`에서 선택.

---

## 자체 리뷰 결과 반영 사항

- `JobPostingImageService.createImages`는 posting id(`Long`)를 받는다 — `JobPostingService.create` 오버로드가 저장 직후 id로 호출(Task 6)하고, 테스트(Task 5)도 id로 호출. 시그니처 일치 확인 완료.
- `JobPostingPublicService`가 `JobPostingImageService`를 주입해도 순환 없음(ImageService는 Repository만 참조).
- 기존 `JobPostingControllerTest`/`JobPostingPublicControllerTest`가 contentHtml 필수를 전제하면 Task 6 Step 3-4에서 함께 보수한다.
- H2 `ddl-auto: update`라 신규 테이블(`job_posting_image`)과 contentHtml nullable 변경은 자동 반영된다(기존 컬럼의 NOT NULL 제약은 update 모드에서 완화되지 않을 수 있으니, 로컬 H2 파일 DB에서 문제가 생기면 해당 컬럼 제약을 수동 완화하거나 DB 파일 재생성).
