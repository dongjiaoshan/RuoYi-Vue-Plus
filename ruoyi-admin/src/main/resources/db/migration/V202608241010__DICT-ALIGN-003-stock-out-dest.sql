-- ============================================================================
-- DICT-ALIGN-003  出库去向 djs_stock_out_dest：追加甲方 34 个具体收货单位
--
-- 用途：仓库出库 / 白条领用 / 生产领用等出库单据的「去向」下拉。
--   label = 甲方原单位名，value = 拼音短码（业务表存 value，中文名以后要改只动 label）。
--
-- 取号：1029100-1029133（两库实查为空），sort 12-45 接现有 0-11 之后，照甲方清单原顺序。
--
-- 纯追加，不删任何现有行：现有 12 项里 9 个是 Java / mp 侧硬编码自动回填的工序内部值
--   （ship_dock / dept_pick / bar_cut / prod_pick / check_loss / frozen_store / feed / dept / kitchen），
--   另 3 个（mine / daye_store / personal）在 t_warehouse_stock_flow 有历史引用；
--   sys_dict_data 没有 status 列不能停用，删了历史流水就翻译不出中文。
--   下拉里的内部值由前端 HIDDEN 集合过滤，不在本迁移处理。
--
-- 幂等：INSERT IGNORE（dict_code 主键冲突即跳过），重跑安全。
--
-- 跑完刷 Redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================================
SET NAMES utf8mb4;

INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1029100, '1001', 12, '大冶两湖店',     'dy_lianghu_dian',    'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029101, '1001', 13, '黄石青龙店',     'hs_qinglong_dian',   'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029102, '1001', 14, '武汉二七滨江店', 'wh_27binjiang_dian', 'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029103, '1001', 15, '劲牌公司',       'jinpai_gongsi',      'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029104, '1001', 16, '大冶农业局',     'daye_nongyeju',      'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029105, '1001', 17, '大冶纪委',       'daye_jiwei',         'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029106, '1001', 18, '黄石财政',       'huangshi_caizheng',  'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029107, '1001', 19, '大冶湖学校',     'dayehu_xuexiao',     'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029108, '1001', 20, '鑫成矿业',       'xincheng_kuangye',   'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029109, '1001', 21, '鑫华矿业',       'xinhua_kuangye',     'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029110, '1001', 22, '大林山矿业',     'dalinshan_kuangye',  'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029111, '1001', 23, '九州矿业',       'jiuzhou_kuangye',    'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029112, '1001', 24, '投资公司',       'touzi_gongsi',       'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029113, '1001', 25, '鑫鸿矿业',       'xinhong_kuangye',    'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029114, '1001', 26, '阳光沙滩',       'yangguang_shatan',   'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029115, '1001', 27, '九州井建',       'jiuzhou_jianjian',   'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029116, '1001', 28, '阳新置业',       'yangxin_zhiye',      'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029117, '1001', 29, '天实',           'tianshi',            'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029118, '1001', 30, '天安',           'tianan',             'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029119, '1001', 31, '大箕政府',       'daji_zhengfu',       'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029120, '1001', 32, '大冶市政府',     'dayeshi_zhengfu',    'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029121, '1001', 33, '老板订单',       'laoban_dingdan',     'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029122, '1001', 34, '其它单位',       'other_unit',         'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029123, '1001', 35, '其它个人',       'other_personal',     'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029124, '1001', 36, '东角山村',       'dongjiaoshan_cun',   'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029125, '1001', 37, '方至畈村',       'fangzhifan_cun',     'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029126, '1001', 38, '叶家庄村',       'yejiazhuang_cun',    'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029127, '1001', 39, '小箕铺村',       'xiaojipu_cun',       'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029128, '1001', 40, '养殖场',         'yangzhichang',       'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029129, '1001', 41, '其它处理',       'other_handle',       'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029130, '1001', 42, '公司招待',       'gongsi_zhaodai',     'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029131, '1001', 43, '员工福利',       'yuangong_fuli',      'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029132, '1001', 44, '员工食堂',       'yuangong_shitang',   'djs_stock_out_dest', '', 'info', 'N', NULL, NOW()),
  (1029133, '1001', 45, '车间零售',       'chejian_lingshou',   'djs_stock_out_dest', '', 'info', 'N', NULL, NOW());
