package com.rental.contracttenant.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ContractSignatureSettings {

    private final Duration deadline;

    public ContractSignatureSettings(@Value("${contract.signature-deadline-days:2}") long deadlineDays) {
        this.deadline = Duration.ofDays(deadlineDays);
    }

    public Duration deadline() {
        return deadline;
    }
}
