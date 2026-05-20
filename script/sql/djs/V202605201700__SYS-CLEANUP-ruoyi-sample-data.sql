-- =============================================================================
-- SYS-CLEANUP-ruoyi-sample-data  清理 ruoyi 自带 sample 数据
-- =============================================================================
-- ruoyi base seed (ry_vue_5.X.sql) 自带的演示用 sample 数据，V1 业务不用。
-- 单租户合并（V202605201500）后 admin 视角能看到，造成 UI noise。本脚本清理它们。
--
-- 删除范围：
--   - 2 个 sample 角色: role_id=3 (test1) / role_id=4 (test2)
--   - 2 个 sample 用户: user_id=3 (test) / user_id=4 (test1)
--   - 3 类工作流字典 (wf_business_status / wf_form_type / wf_task_status，菜单已在 V202605201600 删除)
--   - sys_notice 全部 sample 数据 (ruoyi 自带 2 条演示通知)
--
-- 保留（V1 必需）:
--   - 10 个 sys_* 系统字典 (sys_user_sex / sys_show_hide / sys_normal_disable 等，ruoyi UI 组件依赖)
--   - sys_dept 部门树 / sys_post 岗位（admin.dept_id 引用，留待 SYS-MD-001 业务人员管理落地时 rename）
-- =============================================================================

-- 1. 解 FK 关联（ruoyi 无 CASCADE）
DELETE FROM sys_user_role WHERE user_id IN (3, 4) OR role_id IN (3, 4);
DELETE FROM sys_role_menu WHERE role_id IN (3, 4);
DELETE FROM sys_role_dept WHERE role_id IN (3, 4);
DELETE FROM sys_user_post WHERE user_id IN (3, 4);

-- 2. 删 sample 角色 + 用户
DELETE FROM sys_role WHERE role_id IN (3, 4);
DELETE FROM sys_user WHERE user_id IN (3, 4);

-- 3. 删工作流字典（菜单已在 V202605201600 删除）
DELETE FROM sys_dict_data WHERE dict_type IN ('wf_business_status', 'wf_form_type', 'wf_task_status');
DELETE FROM sys_dict_type WHERE dict_type IN ('wf_business_status', 'wf_form_type', 'wf_task_status');

-- 4. 删 sample 通知
DELETE FROM sys_notice;

-- =============================================================================
-- 验收 query
--   SELECT role_id, role_key FROM sys_role ORDER BY role_id;
--   -- 期望: 1 superadmin + 12 djs (101-112) = 13 rows
--   SELECT user_id, user_name FROM sys_user;
--   -- 期望: 1 row (admin)
--   SELECT COUNT(*) FROM sys_dict_type WHERE dict_type LIKE 'wf_%';
--   -- 期望: 0
--   SELECT COUNT(*) FROM sys_notice;
--   -- 期望: 0
-- =============================================================================
