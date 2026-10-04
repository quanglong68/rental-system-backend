package com.rental.auth.dto;

import java.util.List;
import lombok.Value;
import org.springframework.data.domain.Page;

/**
 * Phan trang chuan (docs muc 8): {content,page,size,totalElements,totalPages}.
 */
@Value
public class PageResponse<T> {

    List<T> content;
    int page;
    int size;
    long totalElements;
    int totalPages;

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
