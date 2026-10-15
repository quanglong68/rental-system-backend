package com.rental.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfileResponse(Long id, String fullName, String phone, String email, String position,
        Boolean isAdmin, List<Long> buildingIds) { }
