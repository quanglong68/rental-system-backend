package com.rental.billingutilityservice.repository;

import com.rental.billingutilityservice.entity.InvoiceItem;
import com.rental.billingutilityservice.enums.InvoiceItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {
    List<InvoiceItem> findByInvoice_Id(Long invoiceId);

    void deleteByInvoice_Id(Long invoiceId);

    @Query("""
            select i.itemType as itemType, sum(i.amount) as total
            from InvoiceItem i
            where i.invoice.roomInvoiceId = :roomInvoiceId
            group by i.itemType
            """)
    List<ItemTypeTotal> sumByRoomInvoice(@Param("roomInvoiceId") Long roomInvoiceId);

    interface ItemTypeTotal {
        InvoiceItemType getItemType();

        BigDecimal getTotal();
    }
}
