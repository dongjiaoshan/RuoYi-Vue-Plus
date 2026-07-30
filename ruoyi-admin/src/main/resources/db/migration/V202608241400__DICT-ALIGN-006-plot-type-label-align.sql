-- ============================================================================
-- DICT-ALIGN-006  地块类型 label 对齐甲方用词
--
-- 甲方「土地信息 (更新)」地块类型列的取值是 露地 / 连栋棚 / 单体棚 / 棚边，
-- 而字典 label 写的是「露天」「连体棚」——admin 地块列表显示的字面与甲方原表对不上，
-- 逐列核对时会被当成 bug 提。生成器侧本来就靠 PLOT_ALIAS 折叠别名（数据落库正确），
-- 这里只把展示用的 label 改成甲方用词。
--
-- 业务表存的是 dict_value（open / multi_shed），value 一律不动，改 label 零风险。
--
-- 跑完刷 Redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================================
SET NAMES utf8mb4;

UPDATE sys_dict_data
   SET dict_label = '露地', update_time = NOW()
 WHERE dict_type = 'djs_plot_type' AND tenant_id = '1001' AND dict_value = 'open';

UPDATE sys_dict_data
   SET dict_label = '连栋棚', update_time = NOW()
 WHERE dict_type = 'djs_plot_type' AND tenant_id = '1001' AND dict_value = 'multi_shed';
