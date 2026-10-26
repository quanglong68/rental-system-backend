package com.rental.contracttenant.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoommateApplicationAcceptedEvent {
    private Long applicationId;
    private Long postId;
    private Long roomId;
    private Long buildingId;
    private Long ownerCustomerId;
    private Long applicantCustomerId;
}
