-- V6__add_location_status.sql
-- 为 location 表添加 status 字段，支持位置归档/禁用

ALTER TABLE location ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
COMMENT ON COLUMN location.status IS '位置状态：ACTIVE / ARCHIVED';
