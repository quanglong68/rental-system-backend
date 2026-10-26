package com.rental.billingutilityservice.repository;

import com.rental.billingutilityservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByInvoice_IdOrderByPaidAtAsc(Long invoiceId);
}
