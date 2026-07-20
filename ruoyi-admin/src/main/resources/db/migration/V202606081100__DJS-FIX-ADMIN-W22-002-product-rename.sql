-- D9X hotfix DJS-FIX-ADMIN-W22-002: 商品主数据 menu 改名 + route path 改名
-- 原因：原 menu_name "商品管理" + path "product" 与 D11+ TRC-CORE-001
--       "产品列表（生产追溯，serial 码）" 同名冲突，给追溯模块腾出
--       /djs/warehouse/product/... 路径空间。
--
-- 影响范围：
--   - 9030 一级菜单：menu_name 商品管理 → 商品主数据 / path product → product-master
--   - 9031-9035 5 个按钮权限：name 保持「商品查询/新增/修改/删除/导出」不变
--   - perms 字符串 djs:warehouse:product:* 全部保留（不破坏现有 service / @SaCheckPermission 绑定）
--   - component 路径 djs-warehouse/product/index 不变（不动目录名）

-- 注：sys_menu 是 ruoyi 框架表，无 tenant_id 列（菜单/权限全局共享，多租户走 sys_role_menu 维度隔离）
UPDATE sys_menu
SET menu_name = '商品主数据',
    path      = 'product-master'
WHERE menu_id = 9030;
