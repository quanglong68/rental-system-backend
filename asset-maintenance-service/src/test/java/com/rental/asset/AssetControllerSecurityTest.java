package com.rental.asset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.common.error.GlobalExceptionHandler;
import io.minio.MinioClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AssetController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {"asset.minio.bucket=rental-assets"})
class AssetControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApplianceRepository appliances;

    @MockBean
    private ReportRepository reports;

    @MockBean
    private RequestRepository requests;

    @MockBean
    private RecordRepository records;

    @MockBean
    private ScheduleRepository schedules;

    @MockBean
    private OutboxRepository outbox;

    @MockBean
    private PropertyClient property;

    @MockBean
    private ContractClient contracts;

    @MockBean
    private UserClient users;

    @MockBean
    private MinioClient minio;

    @MockBean
    private KafkaTemplate<String, String> kafka;

    @Test
    @DisplayName("ADMIN: Target staff not assigned to building throws 422 and does NOT save data or emit outbox event")
    void admin_assignsStaffNotInBuilding_throwsUnprocessableEntityAndNoOutboxEvent() throws Exception {
        // Staff 201 is only in building 20, but schedule is for building 10
        when(users.staffBuildings(201L)).thenReturn(new UserClient.BuildingResponse(201L, List.of(20L)));

        String payload = """
                {
                    "buildingId": 10,
                    "staffId": 201,
                    "title": "Kiem tra thang may",
                    "description": "Dinh ky thang",
                    "intervalMonths": 1,
                    "firstDate": "2026-11-01"
                }
                """;

        mvc.perform(post("/api/v1/maintenance-schedules")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-User-Id", "1").header("X-User-Type", "ADMIN").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STAFF_NOT_IN_BUILDING"));

        verify(schedules, never()).save(any());
        verify(outbox, never()).save(any());
    }

    @Test
    @DisplayName("QUAN_LY: Creating schedule outside manager's building scope throws 403 and writes no data")
    void manager_createSchedule_outOfScope_throwsForbiddenAndNoDataWritten() throws Exception {
        // Manager 101 only manages building 10
        when(users.staffBuildings(101L)).thenReturn(new UserClient.BuildingResponse(101L, List.of(10L)));

        // Request targets building 99 (out of scope)
        String payload = """
                {
                    "buildingId": 99,
                    "staffId": 201,
                    "title": "Bao tri may bom",
                    "intervalMonths": 3,
                    "firstDate": "2026-11-01"
                }
                """;

        mvc.perform(post("/api/v1/maintenance-schedules")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-User-Id", "101").header("X-User-Type", "STAFF").header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isForbidden());

        verify(schedules, never()).save(any());
        verify(outbox, never()).save(any());
    }

    @Test
    @DisplayName("QUAN_LY: Creating schedule within manager's building scope with assigned staff succeeds (201)")
    void manager_createSchedule_inScope_success() throws Exception {
        when(users.staffBuildings(101L)).thenReturn(new UserClient.BuildingResponse(101L, List.of(10L)));
        when(users.staffBuildings(201L)).thenReturn(new UserClient.BuildingResponse(201L, List.of(10L)));

        MaintenanceSchedule saved = new MaintenanceSchedule();
        saved.setId(1L);
        saved.setBuildingId(10L);
        saved.setStaffId(201L);
        saved.setTitle("Bao tri thang may");
        saved.setIntervalMonths(1);
        saved.setNextDate(LocalDate.of(2026, 11, 1));
        when(schedules.save(any())).thenReturn(saved);

        String payload = """
                {
                    "buildingId": 10,
                    "staffId": 201,
                    "title": "Bao tri thang may",
                    "intervalMonths": 1,
                    "firstDate": "2026-11-01"
                }
                """;

        mvc.perform(post("/api/v1/maintenance-schedules")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-User-Id", "101").header("X-User-Type", "STAFF").header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.buildingId").value(10));

        verify(schedules).save(any());
    }

    @Test
    @DisplayName("QUAN_LY: Approving report on out-of-scope building throws 403 and writes no request/outbox")
    void manager_approveReport_outOfScope_throwsForbiddenAndNoDataWritten() throws Exception {
        when(users.staffBuildings(101L)).thenReturn(new UserClient.BuildingResponse(101L, List.of(10L)));

        MaintenanceReport report = new MaintenanceReport();
        report.setId(5L);
        report.setBuildingId(99L); // Building 99 outside manager's scope
        report.setStatus("PENDING");
        report.setRoomId(12L);
        report.setCustomerId(501L);
        when(reports.findById(5L)).thenReturn(Optional.of(report));

        String payload = """
                {
                    "staffId": 201,
                    "priority": "HIGH",
                    "scheduledAt": "2026-11-01T09:00:00+07:00"
                }
                """;

        mvc.perform(put("/api/v1/reports/5/approve")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-User-Id", "101").header("X-User-Type", "STAFF").header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isForbidden());

        verify(requests, never()).save(any());
        verify(outbox, never()).save(any());
    }

    @Test
    @DisplayName("SALE: Đọc appliance và report đơn lẻ trong scope; ngoài scope bị cấm")
    void sale_readsApplianceAndSingleReportInScope_outOfScopeForbidden() throws Exception {
        // SALE 301 is assigned to building 10
        when(users.staffBuildings(301L)).thenReturn(new UserClient.BuildingResponse(301L, List.of(10L)));

        when(appliances.findByBuildingId(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // In scope GET appliances: 200 OK
        mvc.perform(get("/api/v1/appliances?buildingId=10")
                        .header("X-User-Id", "301").header("X-User-Type", "STAFF").header("X-User-Roles", "SALE"))
                .andExpect(status().isOk());

        // Out of scope GET appliances: 403 Forbidden
        mvc.perform(get("/api/v1/appliances?buildingId=99")
                        .header("X-User-Id", "301").header("X-User-Type", "STAFF").header("X-User-Roles", "SALE"))
                .andExpect(status().isForbidden());

        MaintenanceReport report10 = new MaintenanceReport();
        report10.setId(10L);
        report10.setBuildingId(10L);
        when(reports.findById(10L)).thenReturn(Optional.of(report10));

        MaintenanceReport report99 = new MaintenanceReport();
        report99.setId(99L);
        report99.setBuildingId(99L);
        when(reports.findById(99L)).thenReturn(Optional.of(report99));

        // In scope GET report: 200 OK
        mvc.perform(get("/api/v1/reports/10")
                        .header("X-User-Id", "301").header("X-User-Type", "STAFF").header("X-User-Roles", "SALE"))
                .andExpect(status().isOk());

        // Out of scope GET report: 403 Forbidden
        mvc.perform(get("/api/v1/reports/99")
                        .header("X-User-Id", "301").header("X-User-Type", "STAFF").header("X-User-Roles", "SALE"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SALE: Đọc danh sách report trong scope, 403 ngoài scope; mutation vẫn 403")
    void sale_listsReports_inScope_onlyAndCannotMutate() throws Exception {
        when(users.staffBuildings(301L)).thenReturn(new UserClient.BuildingResponse(301L, List.of(10L)));
        when(reports.findByBuildingIdOrderByCreatedAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/reports?buildingId=10")
                        .header("X-User-Id", "301").header("X-User-Type", "STAFF").header("X-User-Roles", "SALE"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/reports?buildingId=99")
                        .header("X-User-Id", "301").header("X-User-Type", "STAFF").header("X-User-Roles", "SALE"))
                .andExpect(status().isForbidden());

        String approvePayload = """
                {
                    "staffId": 201,
                    "priority": "HIGH",
                    "scheduledAt": "2026-11-01T09:00:00Z"
                }
                """;
        mvc.perform(put("/api/v1/reports/5/approve")
                        .contentType(MediaType.APPLICATION_JSON).content(approvePayload)
                        .header("X-User-Id", "301").header("X-User-Type", "STAFF").header("X-User-Roles", "SALE"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/reports/5/reject")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Không hợp lệ\"}")
                        .header("X-User-Id", "301").header("X-User-Type", "STAFF").header("X-User-Roles", "SALE"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("KY_THUAT: Starting or completing maintenance request forbidden when technician is not assigned")
    void technician_unassigned_throwsForbiddenAndNoDataWritten() throws Exception {
        MaintenanceRequest request = new MaintenanceRequest();
        request.setId(7L);
        request.setStaffId(201L); // Assigned to technician 201
        request.setStatus("ASSIGNED");
        request.setBuildingId(10L);
        request.setReportId(1L);
        when(requests.findById(7L)).thenReturn(Optional.of(request));

        // Technician 202 tries to start technician 201's request
        mvc.perform(put("/api/v1/maintenance-requests/7/start")
                        .header("X-User-Id", "202").header("X-User-Type", "STAFF").header("X-User-Roles", "KY_THUAT"))
                .andExpect(status().isForbidden());

        verify(requests, never()).save(any());

        // Technician 202 tries to complete technician 201's request
        String completePayload = """
                {
                    "actionTaken": "Sua xong",
                    "cost": 150000,
                    "applianceStatus": "GOOD"
                }
                """;

        mvc.perform(post("/api/v1/maintenance-requests/7/complete")
                        .contentType(MediaType.APPLICATION_JSON).content(completePayload)
                        .header("X-User-Id", "202").header("X-User-Type", "STAFF").header("X-User-Roles", "KY_THUAT"))
                .andExpect(status().isForbidden());

        verify(records, never()).save(any());
        verify(outbox, never()).save(any());
    }

    @Test
    @DisplayName("KY_THUAT: Starting and completing request assigned to self succeeds and emits outbox event")
    void technician_assigned_startsAndCompletesRequest_successWithOutbox() throws Exception {
        MaintenanceReport report = new MaintenanceReport();
        report.setId(1L);
        report.setCustomerId(501L);
        when(reports.findById(1L)).thenReturn(Optional.of(report));

        MaintenanceRequest request = new MaintenanceRequest();
        request.setId(7L);
        request.setStaffId(201L);
        request.setStatus("ASSIGNED");
        request.setBuildingId(10L);
        request.setRoomId(101L);
        request.setReportId(1L);
        when(requests.findById(7L)).thenReturn(Optional.of(request));
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Tech 201 starts request
        mvc.perform(put("/api/v1/maintenance-requests/7/start")
                        .header("X-User-Id", "201").header("X-User-Type", "STAFF").header("X-User-Roles", "KY_THUAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        request.setStatus("IN_PROGRESS");

        String completePayload = """
                {
                    "actionTaken": "Da thay tụ quat",
                    "cost": 250000,
                    "applianceStatus": "GOOD"
                }
                """;

        // Tech 201 completes request
        mvc.perform(post("/api/v1/maintenance-requests/7/complete")
                        .contentType(MediaType.APPLICATION_JSON).content(completePayload)
                        .header("X-User-Id", "201").header("X-User-Type", "STAFF").header("X-User-Roles", "KY_THUAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(records).save(any());
        verify(outbox).save(any());
    }

    @Test
    @DisplayName("CUSTOMER: Viewing report or maintenance request requires ownership (customerId == currentUserId)")
    void customer_ownerVsNonOwner_accessControl() throws Exception {
        MaintenanceReport report = new MaintenanceReport();
        report.setId(1L);
        report.setCustomerId(501L); // Owner is customer 501
        report.setBuildingId(10L);
        when(reports.findById(1L)).thenReturn(Optional.of(report));

        MaintenanceRequest request = new MaintenanceRequest();
        request.setId(1L);
        request.setReportId(1L);
        request.setStaffId(201L);
        request.setBuildingId(10L);
        when(requests.findById(1L)).thenReturn(Optional.of(request));

        // Customer 502 (non-owner) tries to view report 1 -> 403 Forbidden
        mvc.perform(get("/api/v1/reports/1")
                        .header("X-User-Id", "502").header("X-User-Type", "CUSTOMER").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden());

        // Customer 501 (owner) views report 1 -> 200 OK
        mvc.perform(get("/api/v1/reports/1")
                        .header("X-User-Id", "501").header("X-User-Type", "CUSTOMER").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        // Customer 502 (non-owner) tries to view maintenance request 1 -> 403 Forbidden
        mvc.perform(get("/api/v1/maintenance-requests/1")
                        .header("X-User-Id", "502").header("X-User-Type", "CUSTOMER").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden());

        // Customer 501 (owner) views maintenance request 1 -> 200 OK
        mvc.perform(get("/api/v1/maintenance-requests/1")
                        .header("X-User-Id", "501").header("X-User-Type", "CUSTOMER").header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("QUAN_LY: Calling GET /maintenance-schedules without buildingId returns 400 Bad Request")
    void manager_listSchedules_withoutBuildingId_returns400() throws Exception {
        mvc.perform(get("/api/v1/maintenance-schedules")
                        .header("X-User-Id", "101").header("X-User-Type", "STAFF").header("X-User-Roles", "QUAN_LY"))
                .andExpect(status().isBadRequest());
    }
}
