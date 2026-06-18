-- WMS-VEG-FEED-LOG-001：饲料饲喂专用表（果蔬全流程 spec 步8）
-- 去向③饲料饲喂单独建表；每条按自然日 × 作物品类记录重量；该类记录不记录地块编号（无 plot_id）。
-- 提供「按自然日 × 作物品类」SUM(feed_weight) 统计（mapper GROUP BY feed_date, crop_id）。

CREATE TABLE IF NOT EXISTS t_warehouse_feed_log (
    id           BIGINT          NOT NULL COMMENT '主键（雪花）',
    feed_date    DATE            NOT NULL COMMENT '饲喂日期（自然日，统计维度）',
    crop_id      BIGINT          NULL     COMMENT '作物 FK → t_plant_crop_info.id',
    crop_name    VARCHAR(64)     NULL     COMMENT '作物名称（冗余快照）',
    feed_weight  DECIMAL(12, 3)  NOT NULL COMMENT '饲喂重量(kg)',
    remark       VARCHAR(255)    NULL     COMMENT '备注',
    tenant_id    VARCHAR(20)     NOT NULL DEFAULT '1001' COMMENT '租户编号',
    create_by    BIGINT          NULL     COMMENT '创建者',
    create_time  DATETIME        NULL     COMMENT '创建时间',
    update_by    BIGINT          NULL     COMMENT '更新者',
    update_time  DATETIME        NULL     COMMENT '更新时间',
    create_dept  BIGINT          NULL     COMMENT '创建部门',
    del_flag     CHAR(1)         NOT NULL DEFAULT '0' COMMENT '删除标记（0 未删 / 1 已删）',
    PRIMARY KEY (id),
    KEY idx_feed_date_crop (feed_date, crop_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '饲料饲喂台账（果蔬去向三）';
