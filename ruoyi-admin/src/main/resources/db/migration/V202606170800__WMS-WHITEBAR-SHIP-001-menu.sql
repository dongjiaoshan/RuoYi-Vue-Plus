-- ============================================================================
-- WMS-WHITEBAR-SHIP-001：白条/猪肉出库(发货领用) mp 权限菜单
-- ----------------------------------------------------------------------------
-- 白条/猪肉出库发货 = pack 域发货出库口（白条整只/猪肉部位 inhouse → product_production，
-- 前缀 B/Z），权限串 djs:applet:warehouse:pack:whiteBarOut，挂 pack 父菜单 9220「打包工序」。
-- 与现有分割(pigCut)的「出库称重/出库完成」(9083/9084) 业务不同（那是白条切部位 inhouse 切块销售）。
-- 不绑 sys_role_menu：与现有 pack mp 权限(9221-9224)一致，V1 走 ADR-0003 mock auth *:*:* 兜底；
-- prod 关 mock 时统一补 mp menu 的 role_menu 白名单绑定（与现有 mp menu 同批，不单独处理）。
-- ============================================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9225, 'mp 白条出库发货', 9220, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:pack:whiteBarOut', '#', 1, NOW(), 'WMS-WHITEBAR-SHIP-001 mp');
