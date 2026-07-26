-- 为已有 lot_number IS NULL 的记录补生成批次号
-- 格式：YYYYMMDD + 3位序号（按 household + 天 递增，用 created_at 作为日期）
WITH numbered AS (
    SELECT
        id,
        TO_CHAR(created_at, 'YYYYMMDD') AS date_part,
        ROW_NUMBER() OVER (
            PARTITION BY household_id, DATE(created_at)
            ORDER BY created_at, id
        ) AS seq
    FROM inventory_lot
    WHERE lot_number IS NULL
)
UPDATE inventory_lot il
SET lot_number = n.date_part || LPAD(n.seq::TEXT, 3, '0')
FROM numbered n
WHERE il.id = n.id;
