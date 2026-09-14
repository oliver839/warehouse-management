ALTER TABLE shipping_outbox_event ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);

UPDATE shipping_outbox_event
SET idempotency_key = 'delivery-note-' || delivery_note_id
WHERE idempotency_key IS NULL;

ALTER TABLE shipping_outbox_event ALTER COLUMN idempotency_key SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_shipping_outbox_idempotency_key
    ON shipping_outbox_event (idempotency_key);

ALTER TABLE shipping_outbox_event ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP;
ALTER TABLE shipping_outbox_event ADD COLUMN IF NOT EXISTS worker_id VARCHAR(64);