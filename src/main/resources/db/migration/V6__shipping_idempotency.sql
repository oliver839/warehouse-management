-- V6: Persistent carrier idempotency records (restart-proof).
-- The idempotency key is the primary key: same key always returns the same
-- shipment, even after a JVM restart (unlike the in-memory demo map).
CREATE TABLE IF NOT EXISTS shipping_idempotency_record (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    shipment_id VARCHAR(255) NOT NULL,
    tracking_number VARCHAR(255),
    created_at TIMESTAMP
);
