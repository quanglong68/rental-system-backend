package com.rental.user.controller;

import com.rental.common.error.NotFoundException;
import com.rental.user.entity.Customer;
import com.rental.user.repository.CustomerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    public record CustomerSummary(Long id, String fullName) { }

    /** Contact snapshot used by contract PDFs created outside the rental-request event flow. */
    public record CustomerProfile(Long id, String fullName, String phone, String email) { }

    private final CustomerRepository customers;

    @GetMapping("/{id}/exists")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void checkExists(@PathVariable Long id) {
        if (!customers.existsById(id)) {
            throw new NotFoundException("CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng " + id);
        }
    }

    @GetMapping("/{id}")
    public CustomerProfile get(@PathVariable Long id) {
        Customer customer = customers.findById(id).orElseThrow(() ->
                new NotFoundException("CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng " + id));
        return new CustomerProfile(customer.getId(), customer.getFullName(), customer.getPhone(),
                customer.getEmail());
    }

    /** Trả tên hiển thị của một loạt khách hàng để service khác enrich DTO mà không lộ hồ sơ đầy đủ. */
    @GetMapping
    public List<CustomerSummary> summaries(@RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return customers.findAllById(ids).stream()
                .map(customer -> new CustomerSummary(customer.getId(), customer.getFullName()))
                .toList();
    }
}

