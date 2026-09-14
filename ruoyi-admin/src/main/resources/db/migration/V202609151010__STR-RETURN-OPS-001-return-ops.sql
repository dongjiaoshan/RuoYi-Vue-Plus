-- ============================================================================
-- STR-RETURN-OPS-001  admin 新菜单「门店退回操作」（含单位退回）
--
-- 甲方 row213：仓库 → 库存管理 下新增「门店退回操作」菜单，
--   列表 = 退回日期 / 退回类型 / 退回门店 / 退回状态 / 三个品类数 / 退回操作人·时间 / 退回处理人·时间；
--   待处理行「退回处理」右侧抽屉逐行确认（仓库确认量 + 入库库位 + 处理方式）；
--   已处理行「查看详情」只读；
--   另有「新增」按钮 → 建一张**单位退回**单（已处理态 + 未丢弃行直接入库）。
--
-- 本迁移只做「schema + 字典 + 菜单」三件事，不建业务表、不写业务数据：
--   1) t_store_return 加 return_type / return_unit 两列（历史 46 行回填 store）
--   2) 新字典 djs_store_return_type（门店退回/单位退回）+ djs_return_unit（退回单位配置，值取自 djs_stock_out_dest）
--   3) djs_store_return_status 的 label 改为「待处理 / 已处理」（**只改 label，不动 value**：
--      value 已落库，mp 与仓库侧都在用）
--   4) sys_menu 9152（C）+ 9153~9157（F），挂 9302「库存管理」下 order_num=13，原 >=13 整体后移
--
-- 🔴 「单位退回没有门店」：return_unit 是新列，**绝不复用 store_id**。
--   store_id 在门店盘点候选 / 退回记录按门店分组 / store-daily 汇总里到处都是门店 FK，
--   塞一个假门店 id 会污染门店维度的所有统计。验收语句：
--     SELECT COUNT(*) FROM t_store_return WHERE return_type='unit' AND store_id IS NOT NULL;  -- 必须 = 0
--
-- 幂等：MySQL 无 ADD COLUMN IF NOT EXISTS，按项目先例用 information_schema 存在性判断后再 ADD
--   （见 V202608241300__PLT-CROP-GENUS-001）。字典 / 菜单全部走 upsert，**不整表 DELETE**。
--
-- 取号：列名 / 字典 / 菜单均先实查零占用。
--   flyway_schema_history 当前 max = 202609081000（本工作区另有未部署的 202609081200），
--   本文件取 202609081300（ADR-0008 §2.5：> max + 1h buffer）。
--   menu 9152-9157：仓库域 9000-9999，库存管理 9302 下（9145-9151 已被出入库月汇总/统计占），
--     9152-9199 整段实查 COUNT(*)=0。
--   dict_id 102609140（djs_store_return_type）/ 102609150（djs_return_unit）；
--     dict_code 1090200-1090201（退回类型两态）/ 1091000+（退回单位，按出库去向 dict_sort 定号）。
--
-- 跑完刷 Redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1) t_store_return 加两列
-- ---------------------------------------------------------------------------

-- 1.1 return_type：门店退回 / 单位退回。历史行全部回填 'store'（现存 46 行都是门店退回）。
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_store_return'
      AND COLUMN_NAME = 'return_type'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_store_return ADD COLUMN return_type VARCHAR(16) NOT NULL DEFAULT ''store'' COMMENT ''退回类型 djs_store_return_type：store=门店退回 / unit=单位退回'' AFTER return_direction',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.2 return_unit：单位退回时存退回单位名称（取自字典 djs_return_unit）；门店退回恒 NULL。
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_store_return'
      AND COLUMN_NAME = 'return_unit'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_store_return ADD COLUMN return_unit VARCHAR(64) NULL COMMENT ''退回单位（return_type=unit 时的退回单位名称，取自字典 djs_return_unit；门店退回恒 NULL）'' AFTER return_type',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.3 历史行回填（列是 NOT NULL DEFAULT 'store'，理论上不会有 NULL；这道是给
--     「列先被别的通道建出来、老行留 NULL」的兜底，幂等且无副作用）
UPDATE t_store_return SET return_type = 'store' WHERE return_type IS NULL OR return_type = '';

-- ---------------------------------------------------------------------------
-- 2) 新字典
-- ---------------------------------------------------------------------------

-- 2.1 退回类型 djs_store_return_type：store=门店退回 / unit=单位退回（两态固定，非客户自配）
INSERT INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (102609140, '1001', '退回类型', 'djs_store_return_type', NULL, NOW(),
   '门店退回操作的退回类型：store=门店退回 / unit=单位退回（固定两态，勿删）')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), remark = VALUES(remark);

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (1090200, '1001', 0, '门店退回', 'store', 'djs_store_return_type', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090201, '1001', 1, '单位退回', 'unit',  'djs_store_return_type', '', 'warning', 'N', NULL, NOW(), NULL)
ON DUPLICATE KEY UPDATE
  dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value);

-- 2.2 退回单位配置 djs_return_unit：甲方「单位的配置数据源从出库去向里获取具体值，
--     配置到退回单位配置里」→ 本迁移把当前 djs_stock_out_dest 全量拷一份作**初始示例**，
--     之后由客户在 admin「字典管理 → 退回单位配置」自行增删改。
--     dict_code 用 1091000 + 出库去向的 dict_sort 固定派生，故可重复执行且不会重复插入；
--     客户自己加的行拿雪花 dict_code，与本段不冲突（**绝不 DELETE**，否则部署会抹掉客户配置）。
INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
SELECT
  1091000 + d.dict_sort, '1001', d.dict_sort, d.dict_label, d.dict_value, 'djs_return_unit',
  '', 'primary', 'N', NULL, NOW(), '初始示例（拷贝自出库去向 djs_stock_out_dest），可按需增删'
FROM sys_dict_data d
WHERE d.dict_type = 'djs_stock_out_dest'
  AND 1091000 + d.dict_sort BETWEEN 1091000 AND 1099999
ON DUPLICATE KEY UPDATE
  dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value);

-- ---------------------------------------------------------------------------
-- 3) 退回状态 label：待仓库确认/已入库 → 待处理/已处理（**value 不动**）
--    甲方 row213 第 2/3 条写的是「待处理 / 已处理」；djs_store_return_status 的
--    pending / received 两个 value 已落库且 mp / 仓库侧都在用，只改展示 label。
-- ---------------------------------------------------------------------------
UPDATE sys_dict_data SET dict_label = '待处理' WHERE dict_type = 'djs_store_return_status' AND dict_value = 'pending';
UPDATE sys_dict_data SET dict_label = '已处理' WHERE dict_type = 'djs_store_return_status' AND dict_value = 'received';

-- ---------------------------------------------------------------------------
-- 4) sys_menu：9302「库存管理」下新增 9152「门店退回操作」（紧随「盘点记录」9250 order_num=12）
-- ---------------------------------------------------------------------------

-- 4.1 腾位：新菜单取 order_num=13，原 >=13 整体后移一位。
--     必须带「新菜单还不存在」的守卫：这一步不是幂等的，重跑一次会再整体 +1，
--     把「盘点记录(12) 之后就排到门店退回操作(13)」这个顺序顶乱（同段落里已有的 13/13 会再往后跑）。
SET @menu_exists := (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 9152);
UPDATE sys_menu SET order_num = order_num + 1
 WHERE parent_id = 9302 AND order_num >= 13 AND @menu_exists = 0;

-- 4.2 菜单 seed
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9152, '门店退回操作', 9302, 13, 'storeReturn', 'djs-warehouse/storeReturn/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:storeReturn:list', 'shopping', 1, NOW(), 'STR-RETURN-OPS-001'),
    (9153, '门店退回操作详情', 9152, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:storeReturn:query',   '#', 1, NOW(), 'STR-RETURN-OPS-001 查看详情'),
    (9154, '门店退回操作新增', 9152, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:storeReturn:add',     '#', 1, NOW(), 'STR-RETURN-OPS-001 新增单位退回'),
    (9155, '门店退回操作处理', 9152, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:storeReturn:confirm', '#', 1, NOW(), 'STR-RETURN-OPS-001 退回处理'),
    (9156, '门店退回操作导出', 9152, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:storeReturn:export',  '#', 1, NOW(), 'STR-RETURN-OPS-001'),
    (9157, '门店退回操作明细', 9152, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:storeReturn:detail',  '#', 1, NOW(), 'STR-RETURN-OPS-001 退回产品明细')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path      = VALUES(path),
    component = VALUES(component),
    menu_type = VALUES(menu_type),
    visible   = VALUES(visible),
    status    = VALUES(status),
    perms     = VALUES(perms),
    icon      = VALUES(icon);

-- 4.3 角色授权：业务角色白名单 + 超管（与其他仓库菜单迁移同口径）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (SELECT 9152 AS menu_id UNION ALL SELECT 9153 UNION ALL SELECT 9154
            UNION ALL SELECT 9155 UNION ALL SELECT 9156 UNION ALL SELECT 9157) m
WHERE r.del_flag = '0' AND r.role_id NOT IN (1, 101);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 9152), (1, 9153), (1, 9154), (1, 9155), (1, 9156), (1, 9157),
    (101, 9152), (101, 9153), (101, 9154), (101, 9155), (101, 9156), (101, 9157);

-- 验收 query
--   SELECT menu_id, menu_name, parent_id, order_num, path, component, perms
--     FROM sys_menu WHERE menu_id BETWEEN 9152 AND 9157 ORDER BY menu_id;
--   SELECT COUNT(*) FROM t_store_return WHERE del_flag='0' AND (return_type IS NULL OR return_type='');  -- 必须 0
--   SELECT COUNT(*) FROM t_store_return WHERE del_flag='0' AND return_type <> 'store';                    -- 迁移后必须 0
--   SELECT COUNT(*) FROM t_store_return WHERE return_type='unit' AND store_id IS NOT NULL;                -- 必须 0
--   SELECT dict_label, dict_value FROM sys_dict_data WHERE dict_type='djs_store_return_status';           -- 待处理 / 已处理
