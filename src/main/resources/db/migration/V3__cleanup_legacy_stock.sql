DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'inventory_item'
          AND column_name = 'quantity_in_stock'
    ) THEN
        INSERT INTO stock_position (inventory_item_id, bin_location_id, quantity, reserved_quantity, status, version)
        SELECT i.id,
               CASE WHEN EXISTS (
                   SELECT 1 FROM information_schema.columns
                   WHERE table_schema = current_schema()
                     AND table_name = 'inventory_item'
                     AND column_name = 'bin_location_id'
               ) THEN i.bin_location_id ELSE NULL END,
               COALESCE(i.quantity_in_stock, 0),
               COALESCE(i.reserved_quantity, 0),
               'AVAILABLE',
               0
        FROM inventory_item i
        WHERE NOT EXISTS (
            SELECT 1 FROM stock_position sp WHERE sp.inventory_item_id = i.id
        );
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'inventory_item'
          AND column_name = 'quantity_in_stock'
    ) THEN
        ALTER TABLE inventory_item DROP COLUMN quantity_in_stock;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'inventory_item'
          AND column_name = 'reserved_quantity'
    ) THEN
        ALTER TABLE inventory_item DROP COLUMN reserved_quantity;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'inventory_item'
          AND column_name = 'bin_location_id'
    ) THEN
        ALTER TABLE inventory_item DROP COLUMN bin_location_id;
    END IF;
END $$;
