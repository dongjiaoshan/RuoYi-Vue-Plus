-- V7 R2 去皮规格管理
--
-- 甲方 V7「需求和问题（测试环境）」行2：在【系统管理】下新增【去皮规格管理】，维护置物框等的去皮重量；
-- 行4：六个称重页按「可选位置」列出本页可用的去皮规格，选中后称重扣皮。
--
-- 表：t_warehouse_tare_spec（规格名称 / 去皮重量 kg 三位小数 / 是否默认 / 可选位置 CSV）。
-- 字典：djs_tare_position = 可选位置（六个称重页），值即前端称重页的 position 常量。
-- 菜单：系统管理(1) 下 5800（C）+ 5801-5804（F）。5800 段在 sys_menu 与 migration 目录均零占用。
--
-- 生效：新字典类型首次被读才进 redis，部署后无需 flush；sys_menu 重新登录即生效。
-- 幂等：CREATE TABLE IF NOT EXISTS + ON DUPLICATE KEY UPDATE + INSERT IGNORE。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_warehouse_tare_spec (
    id           BIGINT        NOT NULL COMMENT '主键（雪花）',
    tenant_id    VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    spec_name    VARCHAR(64)   NOT NULL COMMENT '规格名称',
    tare_weight  DECIMAL(12,3) NOT NULL COMMENT '去皮重量 kg（三位小数）',
    is_default   TINYINT       NOT NULL DEFAULT 0 COMMENT '是否默认 djs_yes_no 1=是/0=否；同一位置多个默认取更新时间最近的',
    positions    VARCHAR(255)  NOT NULL COMMENT '可选位置 CSV（字典 djs_tare_position 值，逗号分隔）',
    create_dept  BIGINT        NULL COMMENT '创建部门',
    create_by    BIGINT        NULL COMMENT '创建者',
    create_time  DATETIME      NULL COMMENT '创建时间',
    update_by    BIGINT        NULL COMMENT '更新者',
    update_time  DATETIME      NULL COMMENT '更新时间',
    del_flag     CHAR(1)       NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique   BIGINT        NOT NULL DEFAULT 0 COMMENT '软删唯一性辅助列',
    PRIMARY KEY (id),
    UNIQUE KEY uk_spec_name (tenant_id, spec_name, del_unique)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='去皮规格（称重页扣皮）';

INSERT INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (1026102601, '1001', '去皮可选位置', 'djs_tare_position', 1, NOW(), '去皮规格可用于哪些称重页（多选）')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), remark=VALUES(remark);

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1026102611, '1001', 1, '猪只入库管理',     'pig_in',     'djs_tare_position', '', 'primary', 'N', 1, NOW(), ''),
    (1026102612, '1001', 2, '白条分割出库管理', 'cut_out',    'djs_tare_position', '', 'primary', 'N', 1, NOW(), ''),
    (1026102613, '1001', 3, '果蔬出库管理',     'veg_out',    'djs_tare_position', '', 'primary', 'N', 1, NOW(), ''),
    (1026102614, '1001', 4, '果蔬入库管理',     'veg_in',     'djs_tare_position', '', 'primary', 'N', 1, NOW(), ''),
    (1026102615, '1001', 5, '白条出库领用',     'bar_pickup', 'djs_tare_position', '', 'primary', 'N', 1, NOW(), ''),
    (1026102616, '1001', 6, '白条分割管理',     'bar_cut',    'djs_tare_position', '', 'primary', 'N', 1, NOW(), '')
ON DUPLICATE KEY UPDATE dict_label=VALUES(dict_label), dict_sort=VALUES(dict_sort);

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (5800, '去皮规格管理', 1, 14, 'tareSpec', 'djs-warehouse/tareSpec/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:tareSpec:list', 'dict', 1, NOW(), 'V7-R2'),
    (5801, '去皮规格查询', 5800, 1, '', '', '', 1, 0, 'F', '0', '0', 'djs:warehouse:tareSpec:query', '#', 1, NOW(), 'V7-R2'),
    (5802, '去皮规格新增', 5800, 2, '', '', '', 1, 0, 'F', '0', '0', 'djs:warehouse:tareSpec:add', '#', 1, NOW(), 'V7-R2'),
    (5803, '去皮规格修改', 5800, 3, '', '', '', 1, 0, 'F', '0', '0', 'djs:warehouse:tareSpec:edit', '#', 1, NOW(), 'V7-R2'),
    (5804, '去皮规格删除', 5800, 4, '', '', '', 1, 0, 'F', '0', '0', 'djs:warehouse:tareSpec:remove', '#', 1, NOW(), 'V7-R2')
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

-- 系统管理是超管/管理员入口，只授超管(1)与管理员(101)；称重页读规格走登录态 options 接口，不需要本菜单权限。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 5800), (1, 5801), (1, 5802), (1, 5803), (1, 5804),
    (101, 5800), (101, 5801), (101, 5802), (101, 5803), (101, 5804);

-- 验收 query
--   SELECT menu_id, menu_name, parent_id, perms FROM sys_menu WHERE menu_id BETWEEN 5800 AND 5804;
--   SELECT dict_value, dict_label FROM sys_dict_data WHERE dict_type = 'djs_tare_position' ORDER BY dict_sort;
