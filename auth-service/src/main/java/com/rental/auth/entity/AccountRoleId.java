package com.rental.auth.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Khoa chinh kep cua account_role (account_id, role_id). */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AccountRoleId implements Serializable {

    private Long accountId;
    private Integer roleId;
}
