-- ============================================================
-- DJS-FIX-WMS-RALN  外购猪只台账建表
-- 原型：产品生产管理/外购猪只（仅 新增 + 删除，无编辑）
-- 表 t_warehouse_outsource_pig：购买日期 / 到场时间 / 猪只标识号 / 毛猪重量 /
--   送宰日期 / 供应商 / 购买人 + 6 公共字段 + tenant_id
-- 菜单 seed 在 V202606280900__DJS-FIX-WMS-RALN-menu.sql（C=9028 / F=9290-9293），本文件只建表。
-- 无业务唯一码（外购整头活猪逐头登记，允许同日同供应商多头），不建 biz UNIQUE；
--   del_unique 保留以对齐 djs 软删双列范式（DjsMetaObjectHandler 在 del_flag='1' 时 SET del_unique=id）。
-- ============================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS t_warehouse_outsource_pig;
CREATE TABLE t_warehouse_outsource_pig (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id       VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    purchase_date   DATE          NULL COMMENT '购买日期（必填，应用层校验）',
    arrive_time     DATETIME      NULL COMMENT '到场时间（可空）',
    pig_mark_no     VARCHAR(64)   NULL COMMENT '猪只标识号（可空）',
    pig_weight      DECIMAL(10,2) NULL COMMENT '毛猪重量 kg（必填，应用层校验）',
    slaughter_date  DATE          NULL COMMENT '送宰日期（必填，应用层校验）',
    supplier_id     BIGINT        NULL COMMENT '供应商 ID → t_djs_supplier.id（必填，应用层校验）',
    buyer           VARCHAR(32)   NULL COMMENT '购买人 sys_user.user_id（可空）',
    create_dept     BIGINT        NULL COMMENT '创建部门',
    create_by       BIGINT        NULL COMMENT '创建人',
    create_time     DATETIME      NULL COMMENT '创建时间',
    update_by       BIGINT        NULL COMMENT '更新人',
    update_time     DATETIME      NULL COMMENT '更新时间',
    del_flag        CHAR(1)       DEFAULT '0' COMMENT '删除标志（0 未删 / 1 已删）',
    remark          VARCHAR(500)  NULL COMMENT '备注',
    del_unique      BIGINT        NOT NULL DEFAULT 0 COMMENT "软删 token（del_flag='1' 时 SET del_unique=id）",
    PRIMARY KEY (id),
    KEY idx_tenant_purchase (tenant_id, purchase_date),
    KEY idx_tenant_supplier (tenant_id, supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外购猪只台账（DJS-FIX-WMS-RALN）';
