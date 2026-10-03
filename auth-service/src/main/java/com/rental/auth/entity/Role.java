package com.rental.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Role co dinh, seed boi V2 (ADMIN, QUAN_LY, KY_THUAT, SALE, CUSTOMER).
 * Quyen han do Auth quan ly, User Service khong luu (docs muc 9.1).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "role")
public class Role {

    @Id
    private Integer id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;
}
