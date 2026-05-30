-- ============================================================
-- D10 HOTFIX — 双 P0 修复（pack-ship Mapper 合并 + 7363 撞段重 seed）
--
-- 触发：testing-ai 跑发现 2 个 P0 阻塞
-- 1) ProductProductionMapper 同短名冲突 → admin 拒启动
--    根因：D9 SHIP 包 + D10 PACK 包均建同名 Mapper，MyBatis bean name 冲突
--    解：Java 侧 PACK Mapper 合并 SHIP markDeliveryChecked；SHIP 包 Mapper/Entity 删
--        DDL 侧同步合并 PACK 表结构 — PACK DDL DROP+CREATE 时遗漏了 SHIP 用的
--        demand_id / produce_quantity，且 produce_location TINYINT 与 SHIP 用作
--        location_info FK BIGINT 类型冲突 → 本 hotfix ALTER 补回
-- 2) 7363 menu_id 撞段（D8 仔猪耳标徽标 vs D10 mp 饲料退回 同时占 7363）
--    INSERT IGNORE 跳过 D10 的 seed → djs:applet:breed:feed:return perm DB 不存在
--    解：D10 饲料退回重 seed 到 7365（饲料子段预留空位 7362-7365）
--
-- 同步代码改动（不在本 SQL）：
--   - pack/mapper/ProductProductionMapper.java：合并 markDeliveryChecked
--   - pack/domain/ProductProduction.java：加 demandId / produceQuantity；produceLocation 改 Long
--   - pack/domain/vo/ProductProductionVo.java：produceLocation 改 Long
--   - pack/service/impl/ProductProductionServiceImpl.java：PRODUCE_LOCATION_WAREHOUSE 改 Long null
--   - product/mapper/ProductProductionMapper.java：删
--   - product/domain/ProductProduction.java：删
--   - shipment/service/impl/ShipmentServiceImpl.java：import 改 pack 包
--
-- Follow-up（_open-issues #D10-FUP-1）：
--   produce_location BIGINT FK（SHIP 语义）vs TINYINT 字典 djs_produce_location（PACK doc/11 §2.6 R27）
--   语义冲突；本 hotfix 选 BIGINT 兼容 SHIP；PACK service 不写本字段（值 NULL）；
--   字典 djs_produce_location 仍保留（dict_data 在）但暂未使用；D11 决策保留方案/拆字段。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- §1 t_warehouse_product_production 表结构补字段（PACK / SHIP 合并）
-- ------------------------------------------------------------
-- 加 demand_id（SHIP service listAvailableProductions 按 demandId 筛选）
ALTER TABLE t_warehouse_product_production
    ADD COLUMN demand_id BIGINT NULL COMMENT '关联需求 FK → t_warehouse_demand_manage.id（WMS-SHIP-001 用；PACK 不写）'
        AFTER store_id,
    ADD KEY idx_demand_id (demand_id);

-- 加 produce_quantity（SHIP service 写 stock_flow.changeNum / changeQuantity 用）
ALTER TABLE t_warehouse_product_production
    ADD COLUMN produce_quantity DECIMAL(12,3) NULL COMMENT '生产数量（kg / 件数 / 盒数；PACK service 双写 productWeight 同步；SHIP 流水读）'
        AFTER product_weight;

-- produce_location 类型改 BIGINT（兼容 SHIP location_info FK 用法）
-- 原 TINYINT NOT NULL DEFAULT 1（PACK DDL doc/11 §2.6 R27 字典语义）→ 本 hotfix 改 BIGINT NULL
-- 现存 PACK 行（如已 seed）的 location=1 不构成 FK 合法值，需要 service 端逻辑处理（PACK 已改写 NULL）
ALTER TABLE t_warehouse_product_production
    MODIFY COLUMN produce_location BIGINT NULL COMMENT '生产位置 FK → t_warehouse_location_info.id（D10 hotfix 改类型）';

-- 清洗：PACK service 老逻辑写了 produce_location=1（字典码）→ 不再有效，统一置 NULL（避免误读为 location_info.id=1）
UPDATE t_warehouse_product_production SET produce_location = NULL WHERE produce_location = 1;

-- ------------------------------------------------------------
-- §2 7363 撞段重 seed：mp 饲料退回 perm → menu_id 7365
-- ------------------------------------------------------------
-- 原 V202606091400 用 INSERT IGNORE seed 7363 (djs:applet:breed:feed:return)
-- 与 D8 V202606081300 占 7363 (仔猪耳标徽标) 撞 → IGNORE 跳过 → 实际 perm 缺失
-- 修：在饲料子段 7362-7365 中找空位 7365 重新 seed（7362 pick / 7364 loss 已稳定，7363 让给 D8）
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark
) VALUES (
    7365, 'mp 饲料退回', 0, 23, '', '', '', 1, 0, 'F', '1', '0',
    'djs:applet:breed:feed:return', '#', 1, NOW(),
    'D10-HOTFIX: 原 7363 撞 D8 仔猪耳标徽标 → 重 seed 到 7365'
);

-- 白名单 role 绑定（同 D10 范式 role_id NOT IN (1, 101) AND del_flag='0'）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 7365
FROM sys_role r
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0';
