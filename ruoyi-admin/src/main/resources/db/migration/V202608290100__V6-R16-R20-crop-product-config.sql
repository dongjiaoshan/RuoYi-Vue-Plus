-- ============================================================================
-- V6 需求和问题（测试环境）row16~row20：作物 → 产品「一对多」配置
--
-- 甲方口径：一个作物可以产出多个产品，每个产品有各自的绩效金额（元/公斤）。
--   row16 作物管理新增「产品配置」页签（列表 + 增删改），基础信息里的「关联产品」与
--        产量品质里的「采摘单价」退出编辑（数据迁到本表，一对一 → 一行）；
--   row17/18 毛菜处理采摘 / 处理录入按产品记账；
--   row19 采摘明细列出产品名称；
--   row20 班组绩效按「作物 × 产品」统计，单价取该产品的绩效金额。
--
-- 1. t_plant_crop_product 作物-产品配置表（新建）
-- 2. 存量 related_product / pick_unit_price 一对一迁入
-- 3. t_warehouse_handle_record 补 product_id（采摘 / 处理流水按产品记账）
-- 4. t_plant_work_performance 补 product_id（绩效按产品结算）
--
-- 无新菜单：产品配置是作物编辑弹窗内的页签，权限沿用 djs:plant:crop:*。
-- ============================================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_plant_crop_product 作物关联产品配置
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_plant_crop_product (
    id          BIGINT        NOT NULL                COMMENT '主键（雪花）',
    tenant_id   VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    crop_id     BIGINT        NOT NULL                COMMENT '作物 FK → t_plant_crop_info.id',
    product_id  BIGINT        NOT NULL                COMMENT '关联产品 FK → t_warehouse_product_info.id',
    perf_price  DECIMAL(10,2) NULL                    COMMENT '作物绩效（元/公斤）—— 该产品的采摘绩效单价',
    create_by   BIGINT        NULL                    COMMENT '创建人',
    create_time DATETIME      NULL                    COMMENT '创建时间',
    update_by   BIGINT        NULL                    COMMENT '更新人',
    update_time DATETIME      NULL                    COMMENT '更新时间',
    create_dept BIGINT        NULL                    COMMENT '创建部门',
    del_flag    CHAR(1)       NOT NULL DEFAULT '0'    COMMENT '软删标记 0=正常 / 1=已删',
    del_unique  BIGINT        NOT NULL DEFAULT 0      COMMENT '软删唯一辅助列（删除时写 id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crop_product (tenant_id, crop_id, product_id, del_unique),
    KEY idx_crop (tenant_id, crop_id, del_flag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作物关联产品配置（一个作物多个产品，各自绩效金额）';

-- ------------------------------------------------------------
-- 2. 存量一对一数据迁入（甲方 row16 第 3 点）
--    主键直接复用 crop.id：雪花号全局唯一，跨表复用不会与后续 app 生成的新号相撞。
-- ------------------------------------------------------------
INSERT INTO t_plant_crop_product
       (id, tenant_id, crop_id, product_id, perf_price, create_by, create_time, del_flag, del_unique)
SELECT c.id, c.tenant_id, c.id, c.related_product, c.pick_unit_price, 1, NOW(), '0', 0
  FROM t_plant_crop_info c
 WHERE c.del_flag = '0'
   AND c.related_product IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM t_plant_crop_product p
                    WHERE p.crop_id = c.id AND p.product_id = c.related_product AND p.del_flag = '0');

-- ------------------------------------------------------------
-- 3. 毛菜处理流水按产品记账（row17 / row18 / row19）
--    NULL = 该条流水录入时还没选产品（存量数据），读取侧回落到该作物的首个配置产品。
-- ------------------------------------------------------------
ALTER TABLE t_warehouse_handle_record
    ADD COLUMN product_id BIGINT NULL COMMENT '产品 FK → t_warehouse_product_info.id（本次采摘/处理算作哪个产品）' AFTER crop_id;

-- 存量流水按作物当时的唯一关联产品回填，让采摘明细 / 绩效不至于整片空白
UPDATE t_warehouse_handle_record r
  JOIN t_plant_crop_info c ON c.id = r.crop_id AND c.del_flag = '0'
   SET r.product_id = c.related_product
 WHERE r.del_flag = '0'
   AND r.product_id IS NULL
   AND c.related_product IS NOT NULL;

-- ------------------------------------------------------------
-- 4. 班组绩效按产品结算（row20）
-- ------------------------------------------------------------
ALTER TABLE t_plant_work_performance
    ADD COLUMN product_id BIGINT NULL COMMENT '产品 FK → t_warehouse_product_info.id（该行绩效对应的产品）' AFTER crop_id;
