-- V7: Allow bulk stock (no assigned bin) in pick order and delivery note lines.
-- StockPosition.binLocation is already nullable, and InventoryService.reserve()
-- treats null-bin positions as pickable. PickOrderLine and DeliveryNoteLine were
-- still NOT NULL, which caused a 500 DataIntegrityViolationException when a project
-- was approved against bulk stock (no bin assigned). Make them nullable so bulk
-- stock can be picked and packed without first moving it to a bin.

ALTER TABLE pick_order_line ALTER COLUMN bin_location_id DROP NOT NULL;
ALTER TABLE delivery_note_line ALTER COLUMN bin_location_id DROP NOT NULL;