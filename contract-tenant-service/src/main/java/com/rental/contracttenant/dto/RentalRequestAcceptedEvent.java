package com.rental.contracttenant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RentalRequestAcceptedEvent {
    private Long rentalRequestId;
    private Long listingId;
    private Long roomId;
    private Long buildingId;
    private Long customerId;
    private BigDecimal price;
    private LocalDate moveInDate;
}
