package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** Trang chung theo quy uoc muc 8: {content, page, size, totalElements, totalPages}. */
@Value
@Builder
public class PageResponse<T> {
    List<T> content;
    int page;
    int size;
    long totalElements;
    int totalPages;
}
