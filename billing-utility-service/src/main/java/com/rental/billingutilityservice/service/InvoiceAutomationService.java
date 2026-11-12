package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.client.BillingDataResponse;
import com.rental.billingutilityservice.client.ContractClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * Hóa đơn tự sinh ngay khi phòng đã đủ chỉ số điện/nước. Chạy trong transaction mới sau khi
 * chỉ số đã commit để đọc được dữ liệu vừa lưu; lỗi tự sinh không làm hỏng thao tác nhập chỉ số.
 */
@Service
@RequiredArgsConstructor
public class InvoiceAutomationService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceAutomationService.class);
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ContractClient contractClient;
    private final InvoiceGenerator generator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int autoGenerateForRoom(Long roomId, int month, int year) {
        YearMonth target = YearMonth.of(year, month);
        if (target.isAfter(YearMonth.now(VN))) {
            return 0;
        }
        List<BillingDataResponse> contracts = contractClient.getBillingData(month, year);
        int count = 0;
        for (BillingDataResponse contract : contracts == null ? List.<BillingDataResponse>of() : contracts) {
            if (!roomId.equals(contract.getRoomId())) {
                continue;
            }
            count += generator.generateForContract(contract, month, year, new java.util.ArrayList<>(), true, false);
        }
        if (count > 0) {
            log.info("Tự động sinh/cập nhật {} hóa đơn cho phòng {} kỳ {}/{}", count, roomId, month, year);
        }
        return count;
    }
}
