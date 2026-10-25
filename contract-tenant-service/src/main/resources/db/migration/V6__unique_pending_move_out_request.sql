-- Spec 9.4: "mỗi hợp đồng một yêu cầu PENDING". Enforced in the database so two concurrent
-- creates cannot both win; the service also serialises on the contract row and maps this index
-- back to 409 REQUEST_ALREADY_EXISTS.
CREATE UNIQUE INDEX uq_move_out_request_pending
    ON move_out_request (contract_id)
    WHERE status = 'PENDING';
