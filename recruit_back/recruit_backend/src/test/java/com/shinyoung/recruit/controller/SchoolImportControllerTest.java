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
            "schoolName", "schoolType", "schoolCategory", "educationMode", "region", "address", "countryCode");
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
    void import_inserts_new_and_updates_existing_by_natural_key() throws Exception {
        // 기존: (Existing University, UNIVERSITY, Seoul) — natural key
        schoolRepository.save(School.create("Existing University", "UNIVERSITY", null, null, "Seoul", null, "KR", true));

        List<List<String>> rows = List.of(
                HEADER,
                // 동일 natural key(name/type/region) → 서술 필드 update
                List.of("Existing University", "UNIVERSITY", "GENERAL", "ONCAMPUS", "Seoul", "addr", "KR"),
                // 새 학교 → insert
                List.of("New University", "UNIVERSITY", "GENERAL", "ONCAMPUS", "Busan", "addr2", "KR"));

        mockMvc.perform(multipart("/api/admin/schools/import")
                        .file(new MockMultipartFile("file", "schools.xlsx", XLSX, xlsx(rows)))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.inserted").value(1))
                .andExpect(jsonPath("$.data.updated").value(1))
                .andExpect(jsonPath("$.data.skipped").value(0));

        School updated = schoolRepository.findByNaturalKey("Existing University", "UNIVERSITY", "Seoul")
                .stream().findFirst().orElseThrow();
        assertThat(updated.getAddress()).isEqualTo("addr"); // 업데이트됨(기존 null)
        assertThat(updated.getSchoolCategory()).isEqualTo("GENERAL");
        assertThat(schoolRepository.findByNaturalKey("New University", "UNIVERSITY", "Busan")).hasSize(1);
    }

    @Test
    void import_updates_existing_by_natural_key_only() throws Exception {
        schoolRepository.save(School.create("Natural University", "UNIVERSITY", null, null, "Seoul", null, "KR", true));

        // (name,type,region) fallback 으로 동일 학교 매칭 → update(중복 insert 아님)
        List<List<String>> rows = List.of(
                HEADER,
                List.of("Natural University", "UNIVERSITY", "GENERAL", "ONLINE", "Seoul", "newaddr", "KR"));

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
                List.of("Valid University", "UNIVERSITY", "GENERAL", "", "Seoul", "", "KR"),
                List.of("   ", "UNIVERSITY", "GENERAL", "", "Seoul", "", "KR")); // blank name → skip

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
    void import_skips_field_length_overflow_rows() throws Exception {
        String tooLongCountry = "X".repeat(20); // countryCode length 10 초과 → DB 예외 대신 행 skip
        List<List<String>> rows = List.of(
                HEADER,
                List.of("Valid University", "UNIVERSITY", "GENERAL", "", "Seoul", "", "KR"),
                List.of("Bad University", "UNIVERSITY", "GENERAL", "", "Seoul", "", tooLongCountry));

        mockMvc.perform(multipart("/api/admin/schools/import")
                        .file(new MockMultipartFile("file", "schools.xlsx", XLSX, xlsx(rows)))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inserted").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.errors[0].rowNumber").value(3));
    }

    @Test
    void import_skips_formula_cell_rows() throws Exception {
        mockMvc.perform(multipart("/api/admin/schools/import")
                        .file(new MockMultipartFile("file", "schools.xlsx", XLSX, xlsxWithFormulaRow()))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inserted").value(0))
                .andExpect(jsonPath("$.data.skipped").value(1));
    }

    @Test
    void import_skips_ambiguous_natural_key_rows() throws Exception {
        // 동일 (name,type,region) 학교가 2건 → fallback upsert 모호 → skip
        schoolRepository.saveAll(List.of(
                School.create("Dup University", "UNIVERSITY", null, null, "Seoul", null, "KR", true),
                School.create("Dup University", "UNIVERSITY", null, null, "Seoul", null, "KR", true)));

        List<List<String>> rows = List.of(
                HEADER,
                List.of("Dup University", "UNIVERSITY", "GENERAL", "ONLINE", "Seoul", "addr", "KR"));

        mockMvc.perform(multipart("/api/admin/schools/import")
                        .file(new MockMultipartFile("file", "schools.xlsx", XLSX, xlsx(rows)))
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inserted").value(0))
                .andExpect(jsonPath("$.data.updated").value(0))
                .andExpect(jsonPath("$.data.skipped").value(1));
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
        List<String> wrong = List.of("wrong", "schoolType", "schoolCategory", "educationMode", "region", "address", "countryCode");
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

    private byte[] xlsxWithFormulaRow() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("schools");
            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADER.size(); c++) {
                header.createCell(c, CellType.STRING).setCellValue(HEADER.get(c));
            }
            Row row = sheet.createRow(1);
            row.createCell(0, CellType.STRING).setCellValue("Formula University"); // schoolName
            row.createCell(1, CellType.STRING).setCellValue("UNIVERSITY");         // schoolType
            row.createCell(2, CellType.STRING).setCellValue("GENERAL");            // schoolCategory
            row.createCell(3, CellType.STRING).setCellValue("");                   // educationMode
            row.createCell(4, CellType.STRING).setCellValue("Seoul");              // region
            row.createCell(5, CellType.FORMULA).setCellFormula("1+1");             // address 셀이 formula → skip
            row.createCell(6, CellType.STRING).setCellValue("KR");                 // countryCode
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
