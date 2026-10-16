package com.rental.contracttenant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Temporary HTTP request payload adapter for internal Contract DRAFT creation.
 *
 * <p>Intended role:
 * This DTO is a narrow internal HTTP adapter simulating the payload of the canonical
 * {@code RentalRequestAccepted} domain event until an asynchronous Kafka consumer is introduced.
 * It is NOT the canonical {@link com.rental.common.event.EventEnvelope} itself.
 *
 * <p>Field design and replay semantics:
 * <ul>
 *   <li>{@code rentalRequestId}: uniquely identifies the accepted rental request. One DRAFT contract
 *       per rental request is permitted.</li>
 *   <li>{@code listingId}: validated event source metadata representing the listing from which the request
 *       originated. Intentionally event-only and not persisted in {@code contract} table because contract lifecycle
 *       binds directly to the specific {@code roomId} and {@code buildingId}. Deferring listing association to event audit.</li>
 *   <li>{@code roomId}, {@code buildingId}, {@code customerId}, {@code moveInDate}, {@code price}: core contract
 *       terms mapped directly to {@code Contract} entity.</li>
 *   <li>{@code customerId}: preserved directly on {@code contract} for now as recorded in the event. Its transition
 *       to individual {@code ContractTenant} records must be decided during formal contract signing design.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateDraftContractRequest {

    @NotNull
    @Positive
    private Long rentalRequestId;

    @NotNull
    @Positive
    private Long listingId;

    @NotNull
    @Positive
    private Long roomId;

    @NotNull
    @Positive
    private Long buildingId;

    @NotNull
    @Positive
    private Long customerId;

    @NotNull
    private LocalDate moveInDate;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 15, fraction = 0)
    private BigDecimal price;
}
