package com.shinyoung.recruit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExportAuditLoggerTest {

    @Mock
    private ActivityLogService activityLogService;

    @Test
    void applications_export_audit_records_selected_column_keys() {
        ExportAuditLogger logger = new ExportAuditLogger(Clock.systemDefaultZone(), activityLogService);
        ExportAuditContext context = new ExportAuditContext("admin01", "ROLE_ADMIN", "127.0.0.1", "test-agent", "req-1");
        ExcelExportFile file = new ExcelExportFile(Path.of("dummy.xlsx"), "applications-export.xlsx", 3L);

        logger.logApplicationsExport(context, 7L, null, "SUBMITTED", List.of("APPLICATION_ID", "NAME"), file);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(activityLogService).recordRequiresNew(captor.capture());
        ExportMetadata metadata = (ExportMetadata) captor.getValue().metadata();
        // 어떤 항목까지 반출했는지만 남긴다(값은 남기지 않는다).
        assertThat(metadata.filtersSafeJson()).contains("\"columns\":[\"APPLICATION_ID\",\"NAME\"]");
        assertThat(captor.getValue().jobPostingId()).isEqualTo(7L);
    }
}
