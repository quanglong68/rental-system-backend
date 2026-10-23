package com.rental.contracttenant.client;

import com.rental.common.feign.FeignDefaultConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign call #1 of the closed list (spec 5.2): Contract → Property, {@code GET /internal/rooms/{roomId}},
 * used solely to read {@code maxCapacity} before adding an occupant.
 *
 * <p>Response shape is taken verbatim from spec 9.2's internal API row, which documents
 * {@code 200 {id, buildingId, roomNumber, maxCapacity, status}}. Only the two fields this service
 * needs are declared; unknown JSON properties are ignored by Jackson, so Property may grow the
 * payload without breaking this client.
 *
 * <p>Failure policy (spec 5.2: "Từ chối thao tác, trả 503"): a 5xx, timeout, open circuit or a
 * response whose {@code maxCapacity} cannot be used must fail the add closed. Never fall back to a
 * default capacity — {@code ContractService.addTenant} enforces that, including for a null field.
 */
@FeignClient(name = "property-service", configuration = FeignDefaultConfig.class)
public interface PropertyClient {

    @GetMapping("/internal/rooms/{roomId}")
    RoomInfo getRoom(@PathVariable("roomId") Long roomId);

    /** The subset of the documented room payload this service consumes. */
    record RoomInfo(Long id, Long buildingId, Integer maxCapacity) {
    }
}
