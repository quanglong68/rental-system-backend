package com.rental.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Gan role cho account (docs muc 7.3). ADMIN gan role cho STAFF (Task C4). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@IdClass(AccountRoleId.class)
@Table(name = "account_role")
public class AccountRole {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Id
    @Column(name = "role_id")
    private Integer roleId;
}
