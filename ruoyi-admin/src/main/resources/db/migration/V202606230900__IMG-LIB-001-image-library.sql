-- ============================================================
-- IMG-LIB-001 公共图片库 + 分类默认图
--   t_md_image_library  公共图库（主名 + 别名 + 单张 OSS 图）
--   t_md_default_image  分类默认图（belong_type 6 类 + 全局兜底）
--   图全走 OSS：oss_id 存 sys_oss.oss_id（VARCHAR(32) string）。
--   不 seed 任何 oss_id 真值 —— 图由 Kevin 手动传 OSS 后 UPDATE oss_id 录入。
-- ============================================================

CREATE TABLE IF NOT EXISTS t_md_image_library (
    id           BIGINT       NOT NULL                COMMENT '主键',
    image_name   VARCHAR(64)  NOT NULL                COMMENT '主名（与作物/商品 name 精确匹配）',
    aliases      VARCHAR(255)                         COMMENT '别名（逗号分隔，如 西红柿,tomato）',
    oss_id       VARCHAR(32)                          COMMENT '单张图 ossId（sys_oss.oss_id）',
    sort_order   INT          NOT NULL DEFAULT 0       COMMENT '排序',
    status       CHAR(1)      NOT NULL DEFAULT '0'     COMMENT '状态（字典 sys_normal_disable：0 正常 / 1 停用）',
    tenant_id    VARCHAR(20)  NOT NULL DEFAULT '1001'  COMMENT '租户编号',
    create_dept  BIGINT                               COMMENT '创建部门',
    create_by    BIGINT                               COMMENT '创建者',
    create_time  DATETIME                             COMMENT '创建时间',
    update_by    BIGINT                               COMMENT '更新者',
    update_time  DATETIME                             COMMENT '更新时间',
    remark       VARCHAR(500)                         COMMENT '备注',
    del_flag     CHAR(1)      NOT NULL DEFAULT '0'     COMMENT '删除标志（0 未删 / 1 已删）',
    del_unique   BIGINT       NOT NULL DEFAULT 0       COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_image_name_tenant (tenant_id, image_name, del_unique)
) ENGINE=InnoDB COMMENT='公共图片库（主数据配图自动匹配源）';

CREATE TABLE IF NOT EXISTS t_md_default_image (
    id           BIGINT       NOT NULL                COMMENT '主键',
    category_key VARCHAR(32)  NOT NULL                COMMENT '分类键（belong_type 值 / vegetable / global）',
    oss_id       VARCHAR(32)                          COMMENT '默认图 ossId（sys_oss.oss_id）',
    is_global    TINYINT      NOT NULL DEFAULT 0       COMMENT '是否全局兜底（1 是 / 0 否）',
    tenant_id    VARCHAR(20)  NOT NULL DEFAULT '1001'  COMMENT '租户编号',
    create_dept  BIGINT                               COMMENT '创建部门',
    create_by    BIGINT                               COMMENT '创建者',
    create_time  DATETIME                             COMMENT '创建时间',
    update_by    BIGINT                               COMMENT '更新者',
    update_time  DATETIME                             COMMENT '更新时间',
    remark       VARCHAR(500)                         COMMENT '备注',
    del_flag     CHAR(1)      NOT NULL DEFAULT '0'     COMMENT '删除标志（0 未删 / 1 已删）',
    del_unique   BIGINT       NOT NULL DEFAULT 0       COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_default_cat_tenant (tenant_id, category_key, del_unique)
) ENGINE=InnoDB COMMENT='分类默认图（4 层 resolver L2/L3 兜底）';

-- seed 7 行占位（oss_id 留 NULL，Kevin 传图后只需 UPDATE oss_id）。
-- category_key：6 个 belong_type 值 + global 全局兜底（is_global=1）。
INSERT INTO t_md_default_image (id, tenant_id, category_key, oss_id, is_global, create_by, create_time, remark)
VALUES
  (5511001, '1001', 'pork',       NULL, 0, 1, NOW(), 'IMG-LIB-001 默认图占位：猪肉'),
  (5511002, '1001', 'vegetable',  NULL, 0, 1, NOW(), 'IMG-LIB-001 默认图占位：蔬菜（作物 L2 兜底）'),
  (5511003, '1001', 'white_bar',  NULL, 0, 1, NOW(), 'IMG-LIB-001 默认图占位：白条'),
  (5511004, '1001', 'dry_good',   NULL, 0, 1, NOW(), 'IMG-LIB-001 默认图占位：干货'),
  (5511005, '1001', 'egg',        NULL, 0, 1, NOW(), 'IMG-LIB-001 默认图占位：蛋'),
  (5511006, '1001', 'gift_box',   NULL, 0, 1, NOW(), 'IMG-LIB-001 默认图占位：礼盒'),
  (5511007, '1001', 'global',     NULL, 1, 1, NOW(), 'IMG-LIB-001 默认图占位：全局兜底（L3）');
