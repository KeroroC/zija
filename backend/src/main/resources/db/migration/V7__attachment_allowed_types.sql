-- 附件封闭类型与文档体积上限（图片仍由应用层限制 5 MiB）
ALTER TABLE stored_file DROP CONSTRAINT ck_stored_file_media_type;
ALTER TABLE stored_file ADD CONSTRAINT ck_stored_file_media_type CHECK (
    detected_media_type IN (
        'image/jpeg',
        'image/png',
        'image/webp',
        'image/heic',
        'image/heif',
        'application/pdf',
        'text/markdown',
        'text/plain',
        'application/msword',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'application/vnd.ms-powerpoint',
        'application/vnd.openxmlformats-officedocument.presentationml.presentation',
        'application/vnd.ms-excel',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    )
);

ALTER TABLE stored_file DROP CONSTRAINT ck_stored_file_size;
ALTER TABLE stored_file ADD CONSTRAINT ck_stored_file_size CHECK (byte_size > 0 AND byte_size <= 20971520);

ALTER TABLE stored_file ADD COLUMN mount_type VARCHAR(16);
ALTER TABLE stored_file ADD COLUMN mount_id UUID;
ALTER TABLE stored_file ADD COLUMN name_normalized VARCHAR(255);

CREATE UNIQUE INDEX uq_stored_file_mount_name
    ON stored_file (household_id, mount_type, mount_id, name_normalized)
    WHERE mount_type IS NOT NULL AND name_normalized IS NOT NULL;
