package com.rental.billingutilityservice.repository;

import com.rental.billingutilityservice.entity.Invoice;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByContractTenantIdAndMonthAndYear(Long contractTenantId, Integer month, Integer year);

    boolean existsByContractTenantIdAndMonthAndYear(Long contractTenantId, Integer month, Integer year);

    boolean existsByRoomIdAndMonthAndYear(Long roomId, Integer month, Integer year);

    Page<Invoice> findByCustomerIdAndMonthAndYear(Long customerId, Integer month, Integer year, Pageable pageable);

    Page<Invoice> findByCustomerId(Long customerId, Pageable pageable);

    @Query("""
            select i from Invoice i
            where (:customerId is null or i.customerId = :customerId)
              and (:buildingId is null or i.buildingId = :buildingId)
              and (:roomId is null or i.roomId = :roomId)
              and (:month is null or i.month = :month)
              and (:year is null or i.year = :year)
              and (:status is null or i.status = :status)
            """)
    Page<Invoice> searchMine(Long customerId, Long buildingId, Long roomId, Integer month, Integer year,
                             InvoiceStatus status, Pageable pageable);

    @Query("""
            select i from Invoice i
            where (:buildingId is null or i.buildingId = :buildingId)
              and (:roomId is null or i.roomId = :roomId)
              and (:month is null or i.month = :month)
              and (:year is null or i.year = :year)
              and (:status is null or i.status = :status)
            """)
    Page<Invoice> search(Long buildingId, Long roomId, Integer month, Integer year, InvoiceStatus status,
            Pageable pageable);

    List<Invoice> findByStatusAndDueDateBefore(InvoiceStatus status, LocalDate date);

    List<Invoice> findByStatusInAndDueDateBefore(List<InvoiceStatus> statuses, LocalDate date);
}
