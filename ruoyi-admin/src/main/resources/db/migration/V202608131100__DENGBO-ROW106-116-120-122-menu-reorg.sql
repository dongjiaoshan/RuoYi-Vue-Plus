-- ============================================================
-- DENGBO-ROW106/116/120/122  admin 菜单顺序 + 命名 + 隐藏顶级目录
-- ============================================================
-- 纯 sys_menu UPDATE（不新增 / 不删除 / 不动 role_menu）。
-- ruoyi visible：'0'=显示 / '1'=隐藏。改后需重启（Flyway 自动跑）+ flush redis。
-- menu_id 权威源见 ADMIN-MENU-IA-001 / DJS-FIX-WMS-RALN / FIX-PLT-AD-IA-001。
-- ============================================================

-- ------------------------------------------------------------
-- ROW106 库存管理(9302) 子菜单顺序 + 「损耗总览」改名「损耗记录」
--   目标顺序：1库位总览 2库存日汇总 3采购入库 4库存查询 5入库记录
--             6出库记录 7盘点记录 8退货记录 9损耗记录 10有机饲喂记录
--   隐藏项（出入库流水9110 / 包材领用9100 visible='1'）不列入、保持隐藏；
--   仓库/作物/月报表(9131-9133) 顺延排在 10 项之后，保持 order_num 14-16 不动。
-- ------------------------------------------------------------
UPDATE sys_menu SET order_num = 1  WHERE menu_id = 9026;  -- 库位总览
UPDATE sys_menu SET order_num = 2  WHERE menu_id = 9123;  -- 库存日汇总
UPDATE sys_menu SET order_num = 3  WHERE menu_id = 9060;  -- 采购入库
UPDATE sys_menu SET order_num = 4  WHERE menu_id = 9020;  -- 库存查询
UPDATE sys_menu SET order_num = 5  WHERE menu_id = 9240;  -- 入库记录
UPDATE sys_menu SET order_num = 6  WHERE menu_id = 9241;  -- 出库记录
UPDATE sys_menu SET order_num = 7  WHERE menu_id = 9250;  -- 盘点记录
UPDATE sys_menu SET order_num = 8  WHERE menu_id = 9210;  -- 退货记录
UPDATE sys_menu SET order_num = 9  WHERE menu_id = 9126;  -- 损耗总览 → 损耗记录
UPDATE sys_menu SET order_num = 10 WHERE menu_id = 9130;  -- 有机饲喂记录

UPDATE sys_menu SET menu_name = '损耗记录' WHERE menu_id = 9126;  -- 原「损耗总览」

-- ------------------------------------------------------------
-- ROW116 「库位配置管理」改名「库位配置」（仓库配置管理 9303 下叶子 9010）
-- ------------------------------------------------------------
UPDATE sys_menu SET menu_name = '库位配置' WHERE menu_id = 9010;  -- 原「库位配置管理」

-- ------------------------------------------------------------
-- ROW120 种植(8000) 版块顺序调整
--   目标顺序：1种植看板 2种植管理 3采摘管理 4农事管理 5人员管理 6种植信息管理
--   （desc「农副管理」= 现「农事管理」8007，本条仅调顺序不改名）
-- ------------------------------------------------------------
UPDATE sys_menu SET order_num = 1 WHERE menu_id = 8110;  -- 种植看板
UPDATE sys_menu SET order_num = 2 WHERE menu_id = 8006;  -- 种植管理
UPDATE sys_menu SET order_num = 3 WHERE menu_id = 8004;  -- 采摘管理
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 8007;  -- 农事管理
UPDATE sys_menu SET order_num = 5 WHERE menu_id = 8008;  -- 人员管理
UPDATE sys_menu SET order_num = 6 WHERE menu_id = 8005;  -- 种植信息管理

-- ------------------------------------------------------------
-- ROW122 去掉「通用主数据」顶级目录（5000）
--   独占子页已全部迁走 / 删除：门店管理5002·供应商管理5003·定时任务重跑5600 → 系统管理(1)；
--   人员管理5001·公共图库5500·分类默认图5520 已删；仅剩隐藏 F 权限 5050-5053。
--   目录侧边栏隐藏（visible='1'），保留 F 权限串供农场切换 / OSS 直传接口鉴权。
-- ------------------------------------------------------------
UPDATE sys_menu SET visible = '1' WHERE menu_id = 5000;  -- 通用主数据 目录隐藏
