package com.rental.contracttenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rental.common.security.HeaderUser;
import com.rental.contracttenant.client.AuthClient;
import com.rental.contracttenant.client.CustomerClient;
import com.rental.contracttenant.client.PropertyClient;
import com.rental.contracttenant.config.ContractRenewalSettings;
import com.rental.contracttenant.config.ContractSignatureSettings;
import com.rental.contracttenant.dto.ContractResponse;
import com.rental.contracttenant.dto.MarkDepositPaidRequest;
import com.rental.contracttenant.dto.SignContractRequest;
import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractSignature;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.ContractTenant;
import com.rental.contracttenant.entity.SignatureStatus;
import com.rental.contracttenant.outbox.OutboxEventPublisher;
import com.rental.contracttenant.repository.ContractDocumentRepository;
import com.rental.contracttenant.repository.ContractRenewalRequestRepository;
import com.rental.contracttenant.repository.ContractRepository;
import com.rental.contracttenant.repository.ContractSignatureRepository;
import com.rental.contracttenant.repository.ContractTenantRepository;
import com.rental.contracttenant.repository.MoveOutRequestRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class ContractDepositAndSignFlowTest {

    @Mock private ContractRepository contracts;
    @Mock private ContractTenantRepository tenants;
    @Mock private MoveOutRequestRepository moveOutRequests;
    @Mock private ContractRenewalRequestRepository renewalRequests;
    @Mock private ContractSignatureRepository signatureRows;
    @Mock private ContractDocumentRepository documents;
    @Mock private ContractDocumentService documentService;
    @Mock private ContractDocumentStorage documentStorage;
    @Mock private AuthClient authClient;
    @Mock private ContractSignatureSettings signatureSettings;
    @Mock private ContractRenewalSettings renewalSettings;
    @Mock private OutboxEventPublisher outbox;
    @Mock private PropertyClient properties;
    @Mock private CustomerClient customers;
    @Mock private BuildingScope buildingScope;
    @Mock private PlatformTransactionManager transactionManager;

    /** Fixed at 2026-11-12 10:00 Asia/Ho_Chi_Minh (03:00Z). */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    private ContractService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        lenient().when(signatureSettings.deadline()).thenReturn(Duration.ofDays(15));
        lenient().when(renewalSettings.leadDays()).thenReturn(30);
        lenient().when(renewalSettings.moveOutDays()).thenReturn(15);
        lenient().when(renewalSettings.defaultTermMonths()).thenReturn(12);
        service = new ContractService(contracts, tenants, moveOutRequests, renewalRequests, signatureRows, documents,
                documentService, documentStorage, authClient, signatureSettings, renewalSettings, outbox, properties,
                customers, buildingScope, transactionManager, CLOCK);
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(String userId, String userType, List<String> roles) {
        HeaderUser user = new HeaderUser(userId, userType, roles);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    private static Contract futureContract(SignatureStatus sigStatus) {
        Contract c = new Contract();
        c.setId(9L);
        c.setRoomId(105L);
        c.setBuildingId(2L);
        c.setRoomNumber("B205");
        c.setBuildingName("Seed House B");
        c.setCustomerId(77L);
        c.setTenantFullName("Nguyen Van A");
        c.setLessorName("Quan Ly B");
        c.setStatus(ContractStatus.DRAFT);
        c.setSignatureStatus(sigStatus);
        c.setStartDate(LocalDate.of(2026, 11, 20)); // future move-in date (> 2026-11-12)
        c.setEndDate(LocalDate.of(2027, 11, 19));
        c.setRentAmount(new BigDecimal("9000000"));
        c.setDepositAmount(new BigDecimal("8000000"));
        return c;
    }

    @Test
    void markDepositPaid_publishesContractDepositPaidNotificationEvent() {
        Contract contract = futureContract(SignatureStatus.AWAITING_TENANT);
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(contracts.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        MarkDepositPaidRequest req = new MarkDepositPaidRequest("DEP9_TXN", new BigDecimal("8000000"), "VNPAY");
        var res = service.markDepositPaid(9L, req);

        assertThat(res.isDepositPaid()).isTrue();
        assertThat(contract.getDepositPaymentMethod()).isEqualTo("VNPAY");
        verify(outbox).publishContractDepositPaid(eq(contract));
    }

    @Test
    void signContract_tenantWithManualDepositConfirmed_persistsManualTransferAndNotifiesManagers() {
        signInAs("77", "CUSTOMER", List.of("CUSTOMER"));
        Contract contract = futureContract(SignatureStatus.AWAITING_TENANT);
        contract.setDepositPaid(false);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(authClient.verifyPassword(any())).thenReturn(new AuthClient.VerifyPasswordResponse(true, "tenant77"));
        when(signatureRows.save(any(ContractSignature.class))).thenAnswer(inv -> inv.getArgument(0));
        when(contracts.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        SignContractRequest req = new SignContractRequest("secret123", "sig-key-1", "v1", true, true);
        ContractResponse response = service.signContract(9L, req, new ContractService.SignatureContext("127.0.0.1", "UA"));

        assertThat(response.getSignatureStatus()).isEqualTo(SignatureStatus.AWAITING_LESSOR);
        assertThat(contract.isDepositPaid()).isTrue();
        assertThat(contract.getDepositPaymentMethod()).isEqualTo("MANUAL_TRANSFER");
        verify(outbox).publishContractTenantSigned(eq(contract), eq(Instant.parse("2026-11-12T03:00:00Z")));
    }

    @Test
    void signContract_lessorActivatesFutureMoveInContract_emitsHandoverDueImmediately() {
        signInAs("20", "STAFF", List.of("QUAN_LY"));
        Contract contract = futureContract(SignatureStatus.AWAITING_LESSOR);
        contract.setDepositPaid(true);
        contract.setDepositPaymentMethod("VNPAY");
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(authClient.verifyPassword(any())).thenReturn(new AuthClient.VerifyPasswordResponse(true, "manager20"));
        when(signatureRows.save(any(ContractSignature.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenants.save(any(ContractTenant.class))).thenAnswer(inv -> {
            ContractTenant t = inv.getArgument(0);
            t.setId(501L);
            return t;
        });
        when(contracts.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        SignContractRequest req = new SignContractRequest("secret123", "sig-lessor", "v1", true, null);
        ContractResponse response = service.signContract(9L, req, new ContractService.SignatureContext("127.0.0.1", "UA"));

        assertThat(response.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(response.getSignatureStatus()).isEqualTo(SignatureStatus.COMPLETED);
        verify(outbox).publishContractSigned(eq(contract), eq(List.of(77L)));
        verify(outbox).publishMeterReadingHandoverDue(eq(contract));
    }
}
