-- V5: Outbox lease/recovery columns (idempotent, safe to run after V2/V4).
-- Guarantees ddl-auto=validate passes on a fresh prod database even if
-- migration ordering differs: Hibernate expects processing_started_at/worker_id.
ALTER TABLE shipping_outbox_event ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP;
ALTER TABLE shipping_outbox_event ADD COLUMN IF NOT EXISTS worker_id VARCHAR(64);
