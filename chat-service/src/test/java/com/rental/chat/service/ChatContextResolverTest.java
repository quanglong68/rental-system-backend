package com.rental.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.rental.chat.client.ContractClient;
import com.rental.chat.client.ListingBookingClient;
import com.rental.chat.client.UserClient;
import com.rental.chat.dto.CreateConversationRequest;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatContextResolverTest {

    @Mock private ListingBookingClient listing;
    @Mock private ContractClient contracts;
    @Mock private UserClient users;
    @InjectMocks private ChatContextResolver resolver;

    private final HeaderUser owner = new HeaderUser("10", "CUSTOMER", List.of());
    private final HeaderUser sale = new HeaderUser("20", "STAFF", List.of("SALE"));
    private final HeaderUser manager = new HeaderUser("20", "STAFF", List.of("QUAN_LY"));

    private final UserClient.StaffSummary saleA = new UserClient.StaffSummary(20L, "Sale A", "SALE", false);
    private final UserClient.StaffSummary managerA =
            new UserClient.StaffSummary(20L, "Quản lý A", "QUAN_LY", false);

    @Test
    void roommateApplication_resolvesOwnerAndApplicant() {
        when(listing.roommateApplication(9L)).thenReturn(new ListingBookingClient.RoommateContext(
                9L, 1L, 10L, 20L, "Tìm bạn ở ghép", 7L, 100L, "201", "Tòa A"));
        when(users.customers(List.of(10L))).thenReturn(List.of(new UserClient.CustomerSummary(10L, "An")));
        when(users.customers(List.of(20L))).thenReturn(List.of(new UserClient.CustomerSummary(20L, "Bình")));

        ConversationContext context = resolver.resolve(owner,
                new CreateConversationRequest("ROOMMATE_APPLICATION", 9L, null, null));

        assertThat(context.dedupKey()).isEqualTo("ROOMMATE_APPLICATION:9");
        assertThat(context.self().role()).isEqualTo("OWNER");
        assertThat(context.peer().userId()).isEqualTo(20L);
        assertThat(context.peer().role()).isEqualTo("APPLICANT");
        assertThat(context.contextLabel()).contains("Tìm bạn ở ghép").contains("Phòng 201");
    }

    @Test
    void roommateApplication_rejectsUnrelatedCustomer() {
        when(listing.roommateApplication(9L)).thenReturn(new ListingBookingClient.RoommateContext(
                9L, 1L, 10L, 20L, "Tìm bạn ở ghép", 7L, 100L, "201", "Tòa A"));
        HeaderUser stranger = new HeaderUser("99", "CUSTOMER", List.of());

        assertThatThrownBy(() -> resolver.resolve(stranger,
                new CreateConversationRequest("ROOMMATE_APPLICATION", 9L, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN.name());
    }

    @Test
    void roommatePost_opensConversationWithPostOwner() {
        when(listing.roommatePost(9L)).thenReturn(new ListingBookingClient.RoommatePostContext(
                9L, 20L, "Tìm bạn ở ghép", 7L, 100L, "201", "Tòa A"));
        when(users.customers(List.of(10L))).thenReturn(List.of(new UserClient.CustomerSummary(10L, "An")));
        when(users.customers(List.of(20L))).thenReturn(List.of(new UserClient.CustomerSummary(20L, "Bình")));

        ConversationContext context = resolver.resolve(owner,
                new CreateConversationRequest("ROOMMATE_POST", 9L, null, null));

        assertThat(context.dedupKey()).isEqualTo("ROOMMATE_POST:9:10");
        assertThat(context.self().role()).isEqualTo("INTERESTED");
        assertThat(context.peer().userId()).isEqualTo(20L);
        assertThat(context.peer().role()).isEqualTo("OWNER");
        assertThat(context.contextLabel()).contains("Tìm bạn ở ghép").contains("Phòng 201");
    }

    @Test
    void roommatePost_rejectsOwnerMessagingThemselves() {
        when(listing.roommatePost(9L)).thenReturn(new ListingBookingClient.RoommatePostContext(
                9L, 10L, "Tìm bạn ở ghép", 7L, 100L, "201", "Tòa A"));

        assertThatThrownBy(() -> resolver.resolve(owner,
                new CreateConversationRequest("ROOMMATE_POST", 9L, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN.name());
    }

    @Test
    void listing_routesToBuildingSale() {
        when(listing.listing(55L)).thenReturn(new ListingBookingClient.ListingContext(
                55L, 100L, 7L, "201", "Tòa A", "Phòng 2 phòng ngủ"));
        when(users.staffByBuilding(100L)).thenReturn(List.of(saleA));
        when(users.customers(List.of(10L))).thenReturn(List.of(new UserClient.CustomerSummary(10L, "An")));
        when(users.staff(List.of(20L))).thenReturn(List.of(saleA));

        ConversationContext context = resolver.resolve(owner,
                new CreateConversationRequest("LISTING", 55L, null, null));

        assertThat(context.dedupKey()).isEqualTo("BUILDING_SALE:100:20:10");
        assertThat(context.self().role()).isEqualTo("CUSTOMER");
        assertThat(context.peer().userId()).isEqualTo(20L);
        assertThat(context.peer().role()).isEqualTo("SALE");
        assertThat(context.contextLabel()).contains("Tư vấn thuê").contains("Phòng 201");
    }

    @Test
    void listing_withoutSaleReturnsUnprocessable() {
        when(listing.listing(55L)).thenReturn(new ListingBookingClient.ListingContext(
                55L, 100L, 7L, "201", "Tòa A", "Phòng 2 phòng ngủ"));
        when(users.staffByBuilding(100L)).thenReturn(List.of(managerA));

        assertThatThrownBy(() -> resolver.resolve(owner,
                new CreateConversationRequest("LISTING", 55L, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("SALE_NOT_ASSIGNED");
    }

    @Test
    void viewing_routesToBuildingSaleWhenAppointmentUnassigned() {
        when(listing.appointment(99L)).thenReturn(new ListingBookingClient.AppointmentContext(
                99L, 10L, null, 100L, 7L, Instant.parse("2026-11-05T09:00:00Z"), "SCHEDULED", "201", "Tòa A"));
        when(users.staffByBuilding(100L)).thenReturn(List.of(saleA));
        when(users.customers(List.of(10L))).thenReturn(List.of(new UserClient.CustomerSummary(10L, "An")));
        when(users.staff(List.of(20L))).thenReturn(List.of(saleA));

        ConversationContext context = resolver.resolve(owner,
                new CreateConversationRequest("VIEWING", 99L, null, null));

        assertThat(context.dedupKey()).isEqualTo("BUILDING_SALE:100:20:10");
        assertThat(context.peer().userId()).isEqualTo(20L);
        assertThat(context.peer().role()).isEqualTo("SALE");
        assertThat(context.contextLabel()).contains("Xem phòng").contains("05/11/2026");
    }

    @Test
    void viewing_prefersAssignedSaleOverOtherSales() {
        UserClient.StaffSummary saleB = new UserClient.StaffSummary(21L, "Sale B", "SALE", false);
        when(listing.appointment(99L)).thenReturn(new ListingBookingClient.AppointmentContext(
                99L, 10L, 21L, 100L, 7L, Instant.parse("2026-11-05T09:00:00Z"), "SCHEDULED", "201", "Tòa A"));
        when(users.staffByBuilding(100L)).thenReturn(List.of(saleA, saleB));
        when(users.customers(List.of(10L))).thenReturn(List.of(new UserClient.CustomerSummary(10L, "An")));
        when(users.staff(List.of(21L))).thenReturn(List.of(saleB));

        ConversationContext context = resolver.resolve(owner,
                new CreateConversationRequest("VIEWING", 99L, null, null));

        assertThat(context.dedupKey()).isEqualTo("BUILDING_SALE:100:21:10");
        assertThat(context.peer().userId()).isEqualTo(21L);
    }

    @Test
    void viewing_staffOutsideBuildingSaleIsForbidden() {
        HeaderUser otherStaff = new HeaderUser("21", "STAFF", List.of("SALE"));
        when(listing.appointment(99L)).thenReturn(new ListingBookingClient.AppointmentContext(
                99L, 10L, 21L, 100L, 7L, Instant.parse("2026-11-05T09:00:00Z"), "SCHEDULED", "201", "Tòa A"));
        when(users.staffByBuilding(100L)).thenReturn(List.of(saleA));

        assertThatThrownBy(() -> resolver.resolve(otherStaff,
                new CreateConversationRequest("VIEWING", 99L, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN.name());
    }

    @Test
    void booking_routesToVerifiedSale() {
        when(listing.rentalRequest(88L)).thenReturn(new ListingBookingClient.RentalRequestContext(
                88L, 10L, 55L, 100L, 7L, "APPROVED", 20L, "201", "Tòa A"));
        when(users.staffByBuilding(100L)).thenReturn(List.of(saleA));
        when(users.customers(List.of(10L))).thenReturn(List.of(new UserClient.CustomerSummary(10L, "An")));
        when(users.staff(List.of(20L))).thenReturn(List.of(saleA));

        ConversationContext context = resolver.resolve(owner,
                new CreateConversationRequest("BOOKING", 88L, null, null));

        assertThat(context.dedupKey()).isEqualTo("BUILDING_SALE:100:20:10");
        assertThat(context.peer().userId()).isEqualTo(20L);
        assertThat(context.contextLabel()).contains("Đơn thuê").contains("Phòng 201");
    }

    @Test
    void buildingManager_staffOpensConversationForTenant() {
        when(contracts.activeContract(10L)).thenReturn(new ContractClient.ActiveContract(
                1L, 10L, 100L, 7L, "201", "Tòa A", "ACTIVE"));
        when(users.staffByBuilding(100L)).thenReturn(List.of(managerA));
        when(users.staff(List.of(20L))).thenReturn(List.of(managerA));
        when(users.customers(List.of(10L))).thenReturn(List.of(new UserClient.CustomerSummary(10L, "An")));

        ConversationContext context = resolver.resolve(manager,
                new CreateConversationRequest("BUILDING_MANAGER", 100L, null, 10L));

        assertThat(context.dedupKey()).isEqualTo("BUILDING_MANAGER:100:20:10");
        assertThat(context.self().role()).isEqualTo("MANAGER");
        assertThat(context.peer().userId()).isEqualTo(10L);
    }

    @Test
    void buildingManager_saleCannotOpenManagerConversation() {
        when(contracts.activeContract(10L)).thenReturn(new ContractClient.ActiveContract(
                1L, 10L, 100L, 7L, "201", "Tòa A", "ACTIVE"));
        when(users.staffByBuilding(100L)).thenReturn(List.of(saleA));

        assertThatThrownBy(() -> resolver.resolve(sale,
                new CreateConversationRequest("BUILDING_MANAGER", 100L, null, 10L)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN.name());
    }

    @Test
    void buildingManager_customerWithoutActiveContractIsForbidden() {
        when(contracts.activeContract(10L)).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolve(owner,
                new CreateConversationRequest("BUILDING_MANAGER", 100L, 20L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN.name());
    }
}
