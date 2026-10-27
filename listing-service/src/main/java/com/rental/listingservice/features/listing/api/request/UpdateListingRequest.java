package com.rental.listingservice.features.listing.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateListingRequest(
        @NotBlank @Size(max = 255) String title,
        String description) {
}
