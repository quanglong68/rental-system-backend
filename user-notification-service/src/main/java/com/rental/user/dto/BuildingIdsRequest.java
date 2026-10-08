package com.rental.user.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BuildingIdsRequest(@NotNull List<@NotNull Long> buildingIds) { }
