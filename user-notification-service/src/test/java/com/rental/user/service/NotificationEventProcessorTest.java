package com.rental.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.user.entity.Notification;
import com.rental.user.entity.Staff;
import com.rental.user.entity.StaffBuilding;
import com.rental.user.entity.StaffBuildingId;
import com.rental.user.repository.CustomerRepository;
import com.rental.user.repository.DeviceTokenRepository;
import com.rental.user.repository.NotificationRepository;
import com.rental.user.repository.ProcessedEventRepository;
import com.rental.user.repository.StaffBuildingRepository;
import com.rental.user.repository.StaffRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class NotificationEventProcessorTest {

    @Mock
    private ProcessedEventRepository processed;
    @Mock
    private CustomerRepository customers;
    @Mock
    private StaffRepository staff;
    @Mock
    private StaffBuildingRepository assignments;
    @Mock
    private NotificationRepository notifications;
    @Mock
    private DeviceTokenRepository devices;
    @Mock
    private ApplicationEventPublisher publisher;

    private NotificationEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new NotificationEventProcessor(
                new ObjectMapper(),
                processed,
                customers,
                staff,
                assignments,
                notifications,
                devices,
                publisher);
        lenient().when(processed.existsById(any(UUID.class))).thenReturn(false);
        lenient().when(devices.findByAccountId(any())).thenReturn(List.of());
        lenient().when(notifications.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Staff staffMember(long id, String position, boolean isAdmin) {
        Staff s = new Staff();
        s.setId(id);
        s.setPosition(position);
        s.setAdmin(isAdmin);
        s.setEmail("staff" + id + "@test.local");
        return s;
    }

    private void stubBuildingStaffForBuilding2() {
        when(assignments.findByIdBuildingId(2L)).thenReturn(List.of(
                new StaffBuilding(new StaffBuildingId(20L, 2L)),
                new StaffBuilding(new StaffBuildingId(21L, 2L)),
                new StaffBuilding(new StaffBuildingId(22L, 2L))));
        when(staff.findById(20L)).thenReturn(Optional.of(staffMember(20L, "QUAN_LY", false)));
        when(staff.findById(21L)).thenReturn(Optional.of(staffMember(21L, "KY_THUAT", false)));
        when(staff.findById(22L)).thenReturn(Optional.of(staffMember(22L, "SALE", false)));
        when(staff.findByAdminTrue()).thenReturn(List.of(staffMember(1L, "ADMIN", true)));
    }

    @Test
    void contractDepositPaid_notifiesOnlyBuildingManagerAndAdmin_notTechOrSale() throws Exception {
        stubBuildingStaffForBuilding2();
        String eventJson = """
                {
                  "eventId": "%s",
                  "eventType": "ContractDepositPaid",
                  "payload": {
                    "contractId": 9,
                    "roomId": 105,
                    "buildingId": 2,
                    "customerId": 300,
                    "roomNumber": "B205",
                    "buildingName": "Seed House B",
                    "tenantFullName": "Nguyễn Văn A",
                    "depositAmount": 8000000,
                    "depositPaymentMethod": "VNPAY",
                    "depositTxnRef": "DEP9_XYZ",
                    "depositPaidAt": "2026-11-12T03:00:00Z"
                  }
                }
                """.formatted(UUID.randomUUID());

        processor.process(eventJson);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();
        assertThat(saved).extracting(Notification::getRecipientId).containsExactlyInAnyOrder(20L, 1L);
        assertThat(saved).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo("ContractDepositPaid");
            assertThat(n.getRefType()).isEqualTo("Contract");
            assertThat(n.getRefId()).isEqualTo(9L);
            assertThat(n.getContent()).contains("B205").contains("VNPay").contains("DEP9_XYZ");
        });
    }

    @Test
    void contractTenantSigned_notifiesOnlyBuildingManagerAndAdmin_withContractRef() throws Exception {
        stubBuildingStaffForBuilding2();
        String eventJson = """
                {
                  "eventId": "%s",
                  "eventType": "ContractTenantSigned",
                  "payload": {
                    "contractId": 15,
                    "roomId": 108,
                    "buildingId": 2,
                    "customerId": 301,
                    "roomNumber": "B206",
                    "buildingName": "Seed House B",
                    "tenantFullName": "Trần Thị B",
                    "depositAmount": 7500000,
                    "depositPaid": true,
                    "depositPaymentMethod": "MANUAL_TRANSFER",
                    "signedAt": "2026-11-12T04:00:00Z"
                  }
                }
                """.formatted(UUID.randomUUID());

        processor.process(eventJson);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();
        assertThat(saved).extracting(Notification::getRecipientId).containsExactlyInAnyOrder(20L, 1L);
        assertThat(saved).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo("ContractTenantSigned");
            assertThat(n.getRefType()).isEqualTo("Contract");
            assertThat(n.getRefId()).isEqualTo(15L);
            assertThat(n.getContent()).contains("B206").contains("Khách báo đã chuyển tiền cọc");
        });
    }

    @Test
    void meterReadingReminder_usesPeriodWording_andNotifiesBuildingStaff() throws Exception {
        when(assignments.findByIdBuildingId(2L)).thenReturn(List.of(
                new StaffBuilding(new StaffBuildingId(21L, 2L))));
        when(staff.findByAdminTrue()).thenReturn(List.of(staffMember(1L, "ADMIN", true)));
        String eventJson = """
                {
                  "eventId": "%s",
                  "eventType": "MeterReadingReminder",
                  "payload": {
                    "buildingId": 2,
                    "month": 10,
                    "year": 2026,
                    "roomCount": 2,
                    "roomNumbers": ["B201", "B203"]
                  }
                }
                """.formatted(UUID.randomUUID());

        processor.process(eventJson);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getRecipientId)
                .containsExactlyInAnyOrder(21L, 1L);
        assertThat(captor.getAllValues().get(0).getContent()).contains("kỳ 10/2026").contains("B201, B203");
    }
}
