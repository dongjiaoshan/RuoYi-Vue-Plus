-- ============================================================
-- STR-STOCK-001  门店盘点记录表 + 业务码 seed
-- ============================================================
-- baseline SYS-INIT-001 V202605200904 已建占位（11 字段 AUTO_INCREMENT，字段名 check_no 且缺
--   product_name / product_unit / check_result_type / diff_reason / check_by / is_header / del_unique）
--   + 0 行数据 → DROP + CREATE 重建（无数据迁移负担，参 WMS-STOCK-001 V202606130920 范式）
-- 结构来源：doc/11 §3.5（= §2.4 仓库盘点结构，去掉 location_id，加 store_id 区分门店）
-- header + line 单表模式：同一 check_id 下 is_header=1 一行承载 status / store_id / 锁；
--   is_header=0 的 N 行是逐产品实盘明细
-- ADR-0008 Flyway 时间戳分治 / ADR-0004 字典复用（djs_check_status + djs_check_result 不新建）
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_store_check_record 重建
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_store_check_record;
CREATE TABLE t_store_check_record (
    id                 BIGINT        NOT NULL COMMENT '主键（雪花，MP 分配，禁 AUTO_INCREMENT）',
    check_id           VARCHAR(32)   NOT NULL COMMENT '盘点单业务码 SC{yyyyMMdd}{seq5}（header + 各 line 共用）',
    store_id           BIGINT        NOT NULL COMMENT 'FK t_md_store.id（门店维度，替代 WMS 的 location_id）',
    product_id         BIGINT        NOT NULL DEFAULT 0 COMMENT 'FK t_warehouse_product_info.id（header 行写 0 占位）',
    product_name       VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '产品名称（冗余）',
    product_unit       VARCHAR(16)   NOT NULL DEFAULT '' COMMENT '产品单位（冗余）',
    sys_stock          DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '盘点时系统库存量',
    check_stock        DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '实盘量',
    diff_stock         DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '差异 = check_stock - sys_stock（>0 盘盈 / <0 盘亏）',
    check_result_type  TINYINT       NOT NULL DEFAULT 1 COMMENT '字典 djs_check_result：1=正常 / 2=异常 / 3=计损',
    diff_reason        VARCHAR(255)  NULL COMMENT '差异原因',
    check_by           BIGINT        NULL COMMENT 'FK sys_user.user_id（实盘录入人；header 行可空）',
    check_date         DATETIME      NOT NULL COMMENT '盘点日期',
    check_status       VARCHAR(16)   NOT NULL DEFAULT 'draft' COMMENT '字典 djs_check_status：draft / in_progress / completed',
    is_header          TINYINT       NOT NULL DEFAULT 0 COMMENT '1=盘点单头（承载 status / 锁）/ 0=盘点明细 line',
    tenant_id          VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    create_dept        BIGINT                              COMMENT '创建部门',
    create_by          BIGINT                              COMMENT '创建者',
    create_time        DATETIME                            COMMENT '创建时间',
    update_by          BIGINT                              COMMENT '更新者',
    update_time        DATETIME                            COMMENT '更新时间',
    remark             VARCHAR(500)                        COMMENT '备注',
    del_flag           CHAR(1)       NOT NULL DEFAULT '0'  COMMENT '删除标志（0 存在 / 1 删除）',
    del_unique         BIGINT        NOT NULL DEFAULT 0    COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_check_line (tenant_id, check_id, store_id, product_id, del_unique),
    KEY idx_store (store_id, check_status, del_flag),
    KEY idx_check_date (check_date),
    KEY idx_check_id (check_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店盘点记录表（STR-STOCK-001，结构同仓库盘点 §2.4 去 location_id 加 store_id）';

-- ------------------------------------------------------------
-- 2. 业务码 seed STORE_CHECK_NO（SC{yyyyMMdd}{seq5}，每日重置，与仓库盘点 CHECK_NO C... 分号避免冲突）
-- ------------------------------------------------------------
INSERT IGNORE INTO t_md_biz_code_rule
    (code_type,        pattern,              daily_reset, prefix, seq_length, status, create_by, create_time)
VALUES
    ('STORE_CHECK_NO', 'SC{yyyyMMdd}{seq5}', 1,           'SC',   5,          '0',    1,         NOW());
