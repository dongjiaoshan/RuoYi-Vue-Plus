-- DENGBO-R32 用药治疗：药品彻底废弃批次，用药记录不再传 batch_id。
-- t_breed_medicine_record.batch_id 原 NOT NULL 无默认值 → 放开为 NULL（否则去批次插入报
-- "Field 'batch_id' doesn't have a default value"）。保留字段兼容旧数据，新记录恒 NULL。
ALTER TABLE t_breed_medicine_record
    MODIFY COLUMN batch_id BIGINT NULL COMMENT '批次 ID（药品废弃批次后恒 NULL；保留兼容旧数据）';
