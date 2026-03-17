CREATE SCHEMA IF NOT EXISTS order_core;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE IF NOT EXISTS inventory.inventory_stock (
    sku_id VARCHAR(100) PRIMARY KEY,
    available_qty INTEGER NOT NULL,
    reserved_qty INTEGER NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO inventory.inventory_stock (sku_id, available_qty, reserved_qty, updated_at)
VALUES
    ('sku-burger-001', 100, 0, NOW()),
    ('sku-drink-001', 150, 0, NOW()),
    ('sku-fries-001', 120, 0, NOW())
ON CONFLICT (sku_id) DO NOTHING;

