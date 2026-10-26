package com.rental.asset.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PageRequestFactory {

    public PageRequest create(int pageNumber, int pageSize) {
        if (pageNumber < 0 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page >= 0 và size trong khoảng 1..100");
        }
        return PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "id"));
    }
}
