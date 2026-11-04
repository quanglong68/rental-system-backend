package com.rental.contracttenant.entity;

/** Electronic signature progress of a contract; orthogonal to {@link ContractStatus}. */
public enum SignatureStatus {
    NOT_READY,
    AWAITING_TENANT,
    AWAITING_LESSOR,
    COMPLETED,
    DECLINED,
    EXPIRED
}
