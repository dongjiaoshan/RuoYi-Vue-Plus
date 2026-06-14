-- ============================================================
-- 0613-11 修复：需求确认页(9055)菜单挂载点
-- ============================================================
-- 9055 原挂 9040(需求管理) 下，但 9040 是 C 叶子(component=demand/index)，RuoYi 动态路由
-- 不给 C 叶子生成子路由 → /djs-warehouse/demand/confirm 404。
-- 改挂 9000(仓库 M 目录) 下、path='demand-confirm'，得有效路由 /djs-warehouse/demand-confirm。
-- 配套前端 demand/index.vue「查看需求」跳转 path 同步改为 /djs-warehouse/demand-confirm。
-- ============================================================
SET NAMES utf8mb4;

UPDATE sys_menu
SET parent_id = 9000, path = 'demand-confirm'
WHERE menu_id = 9055;
