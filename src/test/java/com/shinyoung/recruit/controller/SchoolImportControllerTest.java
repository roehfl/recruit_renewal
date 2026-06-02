package com.shinyoung.recruit.controller;

import com.shinyoung.recruit.common.hash.HashUtil;
import com.shinyoung.recruit.domain.entity.Applicant;
import com.shinyoung.recruit.domain.entity.School;
import com.shinyoung.recruit.domain.repository.ApplicantRepository;
import com.shinyoung.recruit.domain.repository.SchoolRepository;
import com.shinyoung.recruit.security.auth.CustomUserDetails;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "crypto.aes.key=22791194512954214612461221261067")
@Transactional
class SchoolImportControllerTest {

    private static final List<String> HEADER = List.of(
            "schoolCode", "schoolName", "schoolType", "educationMode", "region", "address", "countryCode");
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void import_inserts_new_and_updates_existing_by_school_code() throws Exception {
        // 기존: schoolCode SC1 (region 옛값)
        schoolRepository.save(School.create("SC1", "Existing University", "UNIVERSITY", null, "Seoul", null, "KR", true));

        List<List<String>> rows = List.of(
                HEADER,
                List.of("SC1", "Existing University", "UNIVERSITY", "ONCAMPUS", "Incheon", "addr", "KR"), // update
                List.of("SC2", "New University", "UNIVERSITY", "ONCAMPUS", "Busan", "addr2", "KR"));       // insert

        mockMvc.perform(multipart("/api/admin/schools/import")
                        .file(new MockMultipartFile("file", "schools.xlsx", XLSX, xlsx(rows)))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.inserted").value(1))
                .andExpect(jsonPath("$.data.updated").value(1))
                .andExpect(jsonPath("$.data.skipped").value(0));

        School updated = schoolRepository.findBySchoolCode("SC1").orElseThrow();
        assertThat(updated.getRegion()).isEqualTo("Incheon"); // 업데이트됨
        assertThat(schoolRepository.findBySchoolCode("SC2")).isPresent(); // 신규
    }

    @Test
    void import_upserts_by_natural_key_when_school_code_absent() throws Exception {
        schoolRepository.save(School.create(null, "Natural University", "UNIVERSITY", null, "Seoul", null, "KR", true));

        // schoolCode 없음 → (name,type,region) fallback 으로 동일 학교 매칭 → update(중복 insert 아님)
        List<List<String>> rows = List.of(
                HEADER,
                List.of("", "Natural University", "UNIVERSITY", "ONLINE", "Seoul", "newaddr", "KR"));

        mockMvc.perform(multipart("/api/admin/schools/import")
                        .file(new MockMultipartFile("file", "schools.xlsx", XLSX, xlsx(rows)))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inserted").value(0))
                .andExpect(jsonPath("$.data.updated").value(1));

        assertThat(schoolRepository.findByNaturalKey("Natural University", "UNIVERSITY", "Seoul")).hasSize(1);
    }

    @Test
    void import_skips_blank_school_name_rows() throws Exception {
        List<List<String>> rows = List.of(
                HEADER,
                List.of("SCX", "Valid University", "UNIVERSITY", "", "Seoul", "", "KR"),
                List.of("SCY", "   ", "UNIVERSITY", "", "Seoul", "", "KR")); // blank name → skip

        mockMvc.perform(multipart("/api/admin/schools/import")
                        .file(new MockMultipartFile("file", "schools.xlsx", XLSX, xlsx(rows)))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.inserted").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.errors[0].rowNumber").value(3));
    }

    @Test
    void import_rejects_non_xlsx_extension() throws Exception {
        mockMvc.perform(multipart("/api/admin/schools/import")
                        .file(new MockMultipartFile("file", "schools.xls", XLSX, xlsx(List.of(HEADER))))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_rejects_wrong_header() throws Exception {
        List<String> wrong = List.of("wrong", "schoolName", "schoolType", "educationMode", "region", "address", "countryCode");
        mockMvc.perform(multipart("/api/admin/schools/import")
                        .file(new MockMultipartFile("file", "schools.xlsx", XLSX, xlsx(List.of(wrong))))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_blocks_applicant_and_anonymous() throws Exception {
        Applicant applicant = saveApplicant();
        byte[] bytes = xlsx(List.of(HEADER));

        mockMvc.perform(multipart("/api/admin/schools/import")
                        .file(new MockMultipartFile("file", "schools.xlsx", XLSX, bytes))
                        .with(authentication(applicantAuthentication(applicant))))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/admin/schools/import")
                        .file(new MockMultipartFile("file", "schools.xlsx", XLSX, bytes))
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private byte[] xlsx(List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("schools");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                List<String> cells = rows.get(r);
                for (int c = 0; c < cells.size(); c++) {
                    Cell cell = row.createCell(c, CellType.STRING);
                    cell.setCellValue(cells.get(c));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private Applicant saveApplicant() {
        String ci = "ci-" + UUID.randomUUID();
        Applicant applicant = new Applicant(ci, HashUtil.sha256(ci));
        applicant.setLoginId("applicant-" + UUID.randomUUID());
        applicant.setName("지원자");
        applicant.setUserName("지원자");
        applicant.setEmail(UUID.randomUUID() + "@example.com");
        applicant.setPhoneNumber("01000000000");
        return applicantRepository.saveAndFlush(applicant);
    }

    private Authentication adminAuthentication() {
        CustomUserDetails userDetails = CustomUserDetails.fromLdap(
                "school-import-admin-" + UUID.randomUUID(),
                "Recruit",
                "School Import Admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Authentication applicantAuthentication(Applicant applicant) {
        CustomUserDetails userDetails = CustomUserDetails.fromUser(
                applicant,
                List.of(new SimpleGrantedAuthority("ROLE_APPLICANT")));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
