package com.rental.listingservice.integration.staff;

import java.util.List;

public record StaffBuildingsResponse(Long staffId, List<Long> buildingIds) {
}
