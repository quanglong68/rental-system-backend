package com.rental.billingutilityservice.repository;

import com.rental.billingutilityservice.entity.RoomInvoice;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomInvoiceRepository extends JpaRepository<RoomInvoice, Long> {

    Optional<RoomInvoice> findByRoomIdAndMonthAndYear(Long roomId, Integer month, Integer year);

    List<RoomInvoice> findByYear(Integer year);

    @Query("""
            select r from RoomInvoice r
            where (:buildingId is null or r.buildingId = :buildingId)
              and (:month is null or r.month = :month)
              and (:year is null or r.year = :year)
              and (:status is null or r.status = :status)
            """)
    Page<RoomInvoice> search(Long buildingId, Integer month, Integer year, InvoiceStatus status,
            Pageable pageable);

    @Query("""
            select r from RoomInvoice r
            where r.buildingId in :buildingIds
              and (:month is null or r.month = :month)
              and (:year is null or r.year = :year)
              and (:status is null or r.status = :status)
            """)
    Page<RoomInvoice> searchIn(Collection<Long> buildingIds, Integer month, Integer year, InvoiceStatus status,
            Pageable pageable);

    /** Khach chi thay hoa don tong cua phong ma minh co share (docs muc 11.4). */
    @Query("""
            select distinct r from RoomInvoice r, Invoice i
            where i.roomInvoiceId = r.id
              and i.customerId = :customerId
              and (:month is null or r.month = :month)
              and (:year is null or r.year = :year)
              and (:status is null or r.status = :status)
            """)
    Page<RoomInvoice> searchMine(Long customerId, Integer month, Integer year, InvoiceStatus status,
            Pageable pageable);
}
