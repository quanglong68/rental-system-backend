-- Make rental_request_id nullable to support direct DRAFT creation without rental request.
-- Event-driven idempotency preserved: UNIQUE constraint still prevents duplicate rentalRequestId.
-- Direct drafts: rentalRequestId=NULL (semantically correct, no rental request exists).
ALTER TABLE contract ALTER COLUMN rental_request_id DROP NOT NULL;
