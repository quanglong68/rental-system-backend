package com.rental.user.repository;

import com.rental.user.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** Cast keyword sang string de Postgres suy ra kieu tham so khi keyword null. */
    @Query("""
            select c from Customer c
            where :keyword is null
               or lower(c.fullName) like lower(concat('%', cast(:keyword as string), '%'))
               or c.phone like concat('%', cast(:keyword as string), '%')
               or cast(c.id as string) like concat('%', cast(:keyword as string), '%')
            """)
    Page<Customer> search(@Param("keyword") String keyword, Pageable pageable);
}
