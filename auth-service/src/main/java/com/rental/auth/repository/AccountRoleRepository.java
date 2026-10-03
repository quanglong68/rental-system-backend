package com.rental.auth.repository;

import com.rental.auth.entity.AccountRole;
import com.rental.auth.entity.AccountRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRoleRepository extends JpaRepository<AccountRole, AccountRoleId> {

    List<AccountRole> findByAccountId(Long accountId);

    void deleteByAccountId(Long accountId);
}
