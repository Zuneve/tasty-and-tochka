--liquibase formatted sql

--changeset gusev:ordering-002-remove-version
ALTER TABLE orders DROP COLUMN version;
