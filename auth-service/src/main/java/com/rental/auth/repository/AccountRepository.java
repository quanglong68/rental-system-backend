package com.rental.auth.repository;

import com.rental.auth.entity.Account;
import com.rental.auth.entity.AccountType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUsername(String username);

    boolean existsByUsername(String username);

    Page<Account> findByAccountType(AccountType accountType, Pageable pageable);

    Page<Account> findByActive(boolean active, Pageable pageable);

    Page<Account> findByAccountTypeAndActive(AccountType accountType, boolean active, Pageable pageable);
}
