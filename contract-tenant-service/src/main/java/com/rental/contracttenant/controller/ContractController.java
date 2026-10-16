package com.rental.contracttenant.controller;

import com.rental.contracttenant.dto.ContractResponse;
import com.rental.contracttenant.dto.CreateDraftContractRequest;
import com.rental.contracttenant.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal ingress adapter for {@code RentalRequestAccepted} payloads.
 *
 * <p>Notice: This endpoint is an internal synchronous bridge used only until an asynchronous
 * Kafka consumer is introduced to consume canonical {@code EventEnvelope<RentalRequestAccepted>}.
 * It is strictly internal and inaccessible from public API Gateway routing.
 */
@RestController
@RequestMapping("/internal/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResponse createDraft(@Valid @RequestBody CreateDraftContractRequest request) {
        return contractService.createDraft(request);
    }
}
