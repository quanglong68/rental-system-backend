package com.rental.listingbooking.dto;

import java.time.Instant;
import java.time.LocalDate;

public record RoommateApplicationResponse(
    Long id,
    Long roommatePostId,
    Long customerId,
    String customerName,
    String fullName,
    String phone,
    String cccdNumber,
    String message,
    LocalDate dateOfBirth,
    String gender,
    LocalDate cccdIssueDate,
    String cccdIssuePlace,
    String permanentAddress,
    String occupation,
    LocalDate moveInDate,
    boolean cccdFrontUploaded,
    boolean cccdBackUploaded,
    String status,
    Instant createdAt,
    Instant readAt
) {}
