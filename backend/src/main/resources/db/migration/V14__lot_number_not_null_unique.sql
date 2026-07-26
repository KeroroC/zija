-- 批次号设为 NOT NULL 并添加唯一约束
ALTER TABLE inventory_lot ALTER COLUMN lot_number SET NOT NULL;
ALTER TABLE inventory_lot ADD CONSTRAINT uq_inventory_lot_number UNIQUE (lot_number);
