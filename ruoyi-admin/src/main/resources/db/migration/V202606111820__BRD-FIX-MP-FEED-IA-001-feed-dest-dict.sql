-- BRD-FIX-MP-FEED-IA-001：饲料领用补「投喂」出库去向字典值
-- 决策：Kevin 2026-05-30 拍板 = 路径 b（饲料保留出库去向语义，补「投喂」字典值；非删去向）
-- 背景：
--   - djs_stock_out_dest 已 seed 4 值（kitchen/mine/daye_store/personal，V202606081500）
--   - 饲料领用语义是「投喂给猪」，现有 4 值无对应项 → 补第 5 值 feed=投喂
-- 落地：
--   1. 本 SQL 在 djs_stock_out_dest 追加 1 条值（feed / 投喂）
--   2. 跑后需 flush redis 字典缓存（_post-init.sh）—— 否则 mp 下拉读不到新值

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, remark, create_time)
VALUES
  (1006304, '1001', 4, '投喂', 'feed', 'djs_stock_out_dest', '', 'success', 'N', '饲料领用出库去向：投喂给畜禽', NOW());
