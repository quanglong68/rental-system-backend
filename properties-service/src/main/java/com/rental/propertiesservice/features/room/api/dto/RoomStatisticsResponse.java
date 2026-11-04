package com.rental.propertiesservice.features.room.api.dto;

import java.time.LocalDate;

public record RoomStatisticsResponse(long totalRooms, long operatingRooms, long roomsNeedingUpdate,
                                     LocalDate businessDate) {}
