-- ============================================================
-- SYS-FIX-005 废除 t_md_person + 重洗 sys_post 农场岗位
--
-- 决策：回归 doc/06 SYS-MD-001 决策（"所有业务里的人员全部用 sys_user，不要新建 t_employee 表"）。
-- ruoyi sa-token 已经支持多 client 区分（admin / mp-staff / mp-customer），
-- B 端员工走 sys_user + sys_role + sys_post；C 端客户未来单独建 t_md_customer，
-- 与 sys_user 物理分离（D7+ 单独 ticket 处理）。
--
-- 改造：
--   1. 删人员管理菜单（5001 + 5010-5014 共 6 条）
--   2. DROP TABLE t_md_person（含 V202605210800 / V202605211800 / V202605281000 / V202605282000 累计的改造）
--   3. 重洗 sys_post：清掉 ruoyi 默认 4 个 seed（董事长/项目经理/人力资源/普通员工），换 5 个农场岗位（场长/饲养员/兽医/仓库管理员/技术员）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 删人员管理菜单 seed（D02 V202605210800 / V202605211200 写入的 5001 + 5010-5014）
-- ------------------------------------------------------------
DELETE FROM sys_menu WHERE menu_id IN (5001, 5010, 5011, 5012, 5013, 5014);
DELETE FROM sys_role_menu WHERE menu_id IN (5001, 5010, 5011, 5012, 5013, 5014);

-- ------------------------------------------------------------
-- 2. DROP TABLE t_md_person（V202605210800 建表 + 后续 3 次改造历史一并清掉）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_md_person;

-- ------------------------------------------------------------
-- 3. 重洗 sys_post：清 ruoyi 默认 4 seed → 5 个农场岗位
--    （sys_post 是 ruoyi 全局共享表，tenant_id 默认 '000000'，dept_id 用 100 顶级部门）
-- ------------------------------------------------------------
DELETE FROM sys_user_post WHERE post_id IN (1, 2, 3, 4);
DELETE FROM sys_post WHERE post_id BETWEEN 1 AND 4;

INSERT INTO sys_post (post_id, tenant_id, dept_id, post_code, post_name, post_sort, status, create_dept, create_by, create_time, remark) VALUES
  (1, '000000', 100, 'farm_manager', '场长',         1, '0', 100, 1, NOW(), '农场负责人 / 农场综合管理'),
  (2, '000000', 100, 'feeder',       '饲养员',       2, '0', 100, 1, NOW(), '日常喂养 / 圈舍清洁 / 巡检'),
  (3, '000000', 100, 'vet',          '兽医',         3, '0', 100, 1, NOW(), '疫病诊断 / 用药 / 防疫'),
  (4, '000000', 100, 'warehouse',    '仓库管理员',   4, '0', 100, 1, NOW(), '物资出入库 / 库存盘点'),
  (5, '000000', 100, 'technician',   '技术员',       5, '0', 100, 1, NOW(), '配种 / 育种 / 生产技术指导');
