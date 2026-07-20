-- ============================================================
-- 0613-05  门店需求删除：放开「待确认(SUBMITTED)」可删 + 删除即置 DELETED 终态留痕
-- ============================================================
-- 配套 DemandStatus 枚举新增 DELETED("已删除", true)：deleteWithValidByIds 删除前把行
-- demand_status 置 'DELETED' 再软删（del_flag），行从列表消失但 DB 可追溯。
-- djs_demand_status 字典补一行 DELETED/已删除（dict_sort=7 排末，list_class='info' 灰显），
-- 否则历史/导出场景对 DELETED 行展示空白 dict label。
-- INSERT IGNORE 幂等：dict_code 1003047 接续 SYS-INIT-002（1003040~1003046）。
-- migration 后跑 script/sql/djs/_post-init.sh flush redis 字典缓存。
-- ============================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1003047, '1001', 7, '已删除', 'DELETED', 'djs_demand_status', '', 'info', 'N', NULL, NOW());
