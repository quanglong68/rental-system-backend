package com.rental.common.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.domain.Page;

/**
 * Phan trang chuan (docs muc 8): {content, page, size, totalElements, totalPages}.
 * Moi service dung chung de response phan trang thong nhat tren gateway.
 */
@Value
@Builder
@AllArgsConstructor
public class PageResponse<T> {

    List<T> content;
    int page;
    int size;
    long totalElements;
    int totalPages;

    public static <T> PageResponse<T> of(Page<T> result) {
        return PageResponse.<T>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    public static <T> PageResponse<T> from(Page<T> result) {
        return of(result);
    }

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return PageResponse.<T>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }
}
