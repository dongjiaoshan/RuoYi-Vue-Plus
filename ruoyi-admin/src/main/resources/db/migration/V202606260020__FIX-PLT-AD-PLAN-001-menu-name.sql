-- FIX-PLT-AD-PLAN-001：种植计划菜单
-- K199：向导菜单文案「计划新建向导」→「种植计划创建」。
-- K175/K176（404）：经 dev DB + RuoYi 路由生成逻辑核实，8070/8075/8078 子菜单
--   path（plan / plan/wizard / plan/detail）挂在 ParentView 分组 8006(plt-plan-mgmt)
--   下，filterChildren 拼接后注册路由 = /djs-plant/plt-plan-mgmt/plan{,/wizard,/detail}，
--   与前端 PLAN_BASE='/djs-plant/plt-plan-mgmt/plan' 完全对齐 → 路由无错位，
--   测试侧 404 系 staging 菜单未重 seed / 未重登录导致动态路由未刷新（重登录即恢复）。
--   故本迁移不改 path（改了反而破坏现有对齐），仅做 K199 文案。

UPDATE sys_menu SET menu_name = '种植计划创建' WHERE menu_id = 8075;
