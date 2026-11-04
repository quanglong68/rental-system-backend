package com.rental.contracttenant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Renewal deadlines from spec 13.5: offer a renewal 30 days before end_date and fall back to an
 * automatic move-out 15 days before end_date when the renewal is not signed by then.
 */
@Component
public class ContractRenewalSettings {

    private final int leadDays;
    private final int moveOutDays;
    private final int defaultTermMonths;

    public ContractRenewalSettings(
            @Value("${contract.renewal-lead-days:30}") int leadDays,
            @Value("${contract.renewal-move-out-days:15}") int moveOutDays,
            @Value("${contract.renewal-default-months:12}") int defaultTermMonths) {
        this.leadDays = leadDays;
        this.moveOutDays = moveOutDays;
        this.defaultTermMonths = defaultTermMonths;
    }

    public int leadDays() {
        return leadDays;
    }

    public int moveOutDays() {
        return moveOutDays;
    }

    public int defaultTermMonths() {
        return defaultTermMonths;
    }
}
