--liquibase formatted sql

--changeset gusev:ordering-001-create-tables
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    total_price_in_kopecks BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT orders_status_valid CHECK (
        status IN ('CREATED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT orders_total_nonnegative CHECK (total_price_in_kopecks >= 0),
    CONSTRAINT orders_version_nonnegative CHECK (version >= 0)
);

CREATE TABLE order_items (
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_name TEXT NOT NULL,
    unit_price_in_kopecks BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    PRIMARY KEY (order_id, product_id),
    CONSTRAINT order_items_order_fk FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT order_items_product_fk FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT order_items_name_not_blank CHECK (product_name ~ '[^[:space:]]'),
    CONSTRAINT order_items_price_nonnegative CHECK (unit_price_in_kopecks >= 0),
    CONSTRAINT order_items_quantity_valid CHECK (quantity BETWEEN 1 AND 99)
);

CREATE INDEX order_items_product_id_idx ON order_items (product_id);
