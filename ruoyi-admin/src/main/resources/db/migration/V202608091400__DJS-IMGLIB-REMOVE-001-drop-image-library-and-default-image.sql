-- DJS-IMGLIB-REMOVE-001：移除「公共图库 + 分类默认图」整套逻辑
-- 产品 / 作物的图统一在各自「配置」表单里手动上传（product_thumb / image_oss_id），
-- 不再有图库按名自动匹配 + 分类默认图兜底。ImageUrlResolver 保留（简化为 image_oss_id → OSS url）。
--
-- 删两张主数据表（图库 16 行 / 分类默认图 7 行全 NULL）+ 两个 admin 菜单及其角色授权。
-- 产品已配的图存 sys_oss，不受影响（product_thumb / image_oss_id 指向 sys_oss，本迁移不动）。

DROP TABLE IF EXISTS t_md_image_library;
DROP TABLE IF EXISTS t_md_default_image;

DELETE FROM sys_role_menu WHERE menu_id IN (5500, 5520);
DELETE FROM sys_menu WHERE menu_id IN (5500, 5520);
