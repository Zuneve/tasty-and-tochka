--liquibase formatted sql

--changeset gusev:catalog-001-create-tables
CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    CONSTRAINT categories_name_not_blank CHECK (name ~ '[^[:space:]]')
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    price_in_kopecks BIGINT NOT NULL,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT products_category_fk FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT products_name_not_blank CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT products_price_nonnegative CHECK (price_in_kopecks >= 0)
);

CREATE INDEX products_category_id_idx ON products (category_id);
