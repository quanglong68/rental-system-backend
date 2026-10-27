package com.rental.listingservice.features.listing.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateListingRequest(
        @NotNull @Positive Long roomId,
        @NotBlank @Size(max = 255) String title,
        String description) {
}
