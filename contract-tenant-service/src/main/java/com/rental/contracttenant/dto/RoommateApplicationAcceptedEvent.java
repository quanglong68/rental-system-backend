package com.rental.contracttenant.dto;

import java.time.LocalDate;
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
    private String fullName;
    private String phone;
    private String cccdNumber;
    private LocalDate moveInDate;

    public RoommateApplicationAcceptedEvent(Long applicationId, Long postId, Long roomId, Long buildingId,
            Long ownerCustomerId, Long applicantCustomerId, String fullName, String phone, String cccdNumber) {
        this(applicationId, postId, roomId, buildingId, ownerCustomerId, applicantCustomerId, fullName, phone, cccdNumber, null);
    }
}

