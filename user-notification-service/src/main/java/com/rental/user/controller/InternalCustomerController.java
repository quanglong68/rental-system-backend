package com.rental.user.controller;

import com.rental.common.error.NotFoundException;
import com.rental.user.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API for customer existence checks. Gateway blocks /internal/** from public access.
 * Feign call #8 (closed list): Contract → User, {@code GET /internal/customers/{id}/exists}.
 */
@RestController
@RequestMapping("/internal/customers")
@RequiredArgsConstructor
public class InternalCustomerController {

    private final CustomerRepository customers;

    @GetMapping("/{id}/exists")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void checkExists(@PathVariable Long id) {
        if (!customers.existsById(id)) {
            throw new NotFoundException("CUSTOMER_NOT_FOUND", "Khong tim thay khach hang " + id);
        }
    }
}
