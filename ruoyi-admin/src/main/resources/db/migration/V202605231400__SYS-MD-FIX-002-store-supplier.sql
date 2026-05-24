-- ============================================================
-- SYS-MD-FIX-002 门店 + 供应商 V1 验收 gap（D03 testing-human #4 hetao 反馈）
--
-- 改动总览：
--   1. t_md_store：重命名 contact_* → manager_*；新增 short_name/open_date/pos_system_id
--      /image_oss_id/manager_user_id；business_status TINYINT → VARCHAR(16) 走字典
--      djs_store_status
--   2. t_md_supplier：重命名 contact_* → liaison_*；新增 license_no/license_image_oss_id
--      /business_license_no/cooperation_start_date/deal_count/purchase_qty；business_status
--      TINYINT → VARCHAR(16) 走字典 djs_supplier_status；settle_type 走字典 djs_settle_type
--   3. 字典调整：djs_store_status label 改 / djs_store_type 新建 / djs_supplier_type label 改
--      + 数据 backfill / djs_supplier_status 新建 / djs_settle_type 新建
--
-- 跑完后必须 flush redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================
SET NAMES utf8mb4;

-- ===========================
-- 1. t_md_store 改造
-- ===========================
-- 1.1 字段重命名 contact_* → manager_*
ALTER TABLE t_md_store CHANGE COLUMN contact_name manager_name VARCHAR(32) NULL COMMENT '店长姓名';
ALTER TABLE t_md_store CHANGE COLUMN contact_phone manager_phone VARCHAR(20) NULL COMMENT '店长电话';

-- 1.2 business_status TINYINT(1=合作中/0=已终止) → VARCHAR(16) 走 djs_store_status 字典
--     旧值 1 → '0'（合作中），旧值 0 → '1'（已终止）
ALTER TABLE t_md_store ADD COLUMN business_status_new VARCHAR(16) NOT NULL DEFAULT '0' AFTER store_type;
UPDATE t_md_store SET business_status_new = CASE WHEN business_status=1 THEN '0' WHEN business_status=0 THEN '1' ELSE '0' END;
ALTER TABLE t_md_store DROP COLUMN business_status;
ALTER TABLE t_md_store CHANGE COLUMN business_status_new business_status VARCHAR(16) NOT NULL DEFAULT '0' COMMENT '合作状态（字典 djs_store_status: 0=合作中/1=已终止/2=装修中）';

-- 1.3 新加字段
ALTER TABLE t_md_store ADD COLUMN short_name VARCHAR(64) NULL COMMENT '门店简称' AFTER store_name;
ALTER TABLE t_md_store ADD COLUMN open_date DATE NULL COMMENT '开业日期' AFTER short_name;
ALTER TABLE t_md_store ADD COLUMN pos_system_id VARCHAR(64) NULL COMMENT '收银系统 ID' AFTER manager_phone;
ALTER TABLE t_md_store ADD COLUMN image_oss_id BIGINT NULL COMMENT '门店图片（引用 sys_oss.oss_id）' AFTER pos_system_id;
ALTER TABLE t_md_store ADD COLUMN manager_user_id BIGINT NULL COMMENT '店长 sys_user.user_id（NULL=未设置）' AFTER manager_phone;
ALTER TABLE t_md_store ADD INDEX idx_store_manager (manager_user_id);

-- ===========================
-- 2. t_md_supplier 改造
-- ===========================
-- 2.1 字段重命名 contact_* → liaison_*
ALTER TABLE t_md_supplier CHANGE COLUMN contact_name liaison_name VARCHAR(32) NULL COMMENT '联系负责人';
ALTER TABLE t_md_supplier CHANGE COLUMN contact_phone liaison_phone VARCHAR(20) NULL COMMENT '负责人电话';

-- 2.2 业务数据 backfill：pack（包材）映射 → other（其他），再删 pack 字典
UPDATE t_md_supplier SET supplier_type='other' WHERE supplier_type='pack';

-- 2.3 business_status TINYINT → VARCHAR(16) 走 djs_supplier_status 字典
--     旧值 1 → '0'（合作中），旧值 0 → '1'（已终止）
ALTER TABLE t_md_supplier ADD COLUMN business_status_new VARCHAR(16) NOT NULL DEFAULT '0' AFTER address;
UPDATE t_md_supplier SET business_status_new = CASE WHEN business_status=1 THEN '0' WHEN business_status=0 THEN '1' ELSE '0' END;
ALTER TABLE t_md_supplier DROP COLUMN business_status;
ALTER TABLE t_md_supplier CHANGE COLUMN business_status_new business_status VARCHAR(16) NOT NULL DEFAULT '0' COMMENT '合作状态（字典 djs_supplier_status: 0=合作中/1=已终止）';

-- 2.4 settle_type 自由文本 → VARCHAR(16) 走 djs_settle_type 字典
--     旧数据统一 backfill 为 cash（V1 默认现款现货）；已是 cash 重复 UPDATE 也安全
UPDATE t_md_supplier SET settle_type = 'cash' WHERE settle_type IS NOT NULL AND settle_type NOT IN ('cash','monthly','quarterly');
UPDATE t_md_supplier SET settle_type = 'cash' WHERE settle_type IS NULL OR settle_type = '';
ALTER TABLE t_md_supplier MODIFY COLUMN settle_type VARCHAR(16) NULL DEFAULT 'cash' COMMENT '结算方式（字典 djs_settle_type: cash=现款现货/monthly=月结/quarterly=季结）';

-- 2.5 新加字段
ALTER TABLE t_md_supplier ADD COLUMN license_no VARCHAR(64) NULL COMMENT '营业执照编号' AFTER supplier_name;
ALTER TABLE t_md_supplier ADD COLUMN license_image_oss_id BIGINT NULL COMMENT '营业执照图片（sys_oss.oss_id）' AFTER license_no;
ALTER TABLE t_md_supplier ADD COLUMN business_license_no VARCHAR(64) NULL COMMENT '经营许可证编号' AFTER license_image_oss_id;
ALTER TABLE t_md_supplier ADD COLUMN cooperation_start_date DATE NULL COMMENT '合作开始日期' AFTER business_license_no;
ALTER TABLE t_md_supplier ADD COLUMN deal_count INT NOT NULL DEFAULT 0 COMMENT '交易次数（聚合冗余 / V1 stub=0，下游 BRD-MED / WMS-PURCHASE 落地后回填）';
ALTER TABLE t_md_supplier ADD COLUMN purchase_qty DECIMAL(18,3) NOT NULL DEFAULT 0 COMMENT '累计购入商品数（V1 stub=0）';

-- ===========================
-- 3. 字典调整
-- ===========================
-- 3.1 djs_store_status label 改语义（0=启用→合作中 / 1=停用→已终止）；装修中 (value=2) 不变
UPDATE sys_dict_data SET dict_label='合作中', list_class='success' WHERE dict_type='djs_store_status' AND dict_value='0';
UPDATE sys_dict_data SET dict_label='已终止', list_class='danger'  WHERE dict_type='djs_store_status' AND dict_value='1';

-- 3.2 djs_store_type 新建（V1 直营 / 加盟）
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100700, '1001', '门店类型', 'djs_store_type', NULL, NOW(), '门店：直营 / 加盟');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1007000, '1001', 0, '直营', 'direct',    'djs_store_type', '', 'primary', 'Y', NULL, NOW()),
  (1007001, '1001', 1, '加盟', 'franchise', 'djs_store_type', '', 'success', 'N', NULL, NOW());

-- 3.3 djs_supplier_type label 改：饲料 → 饲料原材料 / 兽药 → 药品 / 蔬菜种子 → 种子
UPDATE sys_dict_data SET dict_label='药品'         WHERE dict_type='djs_supplier_type' AND dict_value='med';
UPDATE sys_dict_data SET dict_label='种猪'         WHERE dict_type='djs_supplier_type' AND dict_value='breed';
UPDATE sys_dict_data SET dict_label='饲料原材料'   WHERE dict_type='djs_supplier_type' AND dict_value='feed';
UPDATE sys_dict_data SET dict_label='种子'         WHERE dict_type='djs_supplier_type' AND dict_value='seed';
-- 删除 pack（V1 不要；t_md_supplier 中 pack 数据已在 §2.2 UPDATE 到 other）；保留 other 作兜底
DELETE FROM sys_dict_data WHERE dict_type='djs_supplier_type' AND dict_value='pack';

-- 3.4 djs_supplier_status 新建
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100701, '1001', '供应商合作状态', 'djs_supplier_status', NULL, NOW(), '供应商：合作中 / 已终止');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1007010, '1001', 0, '合作中', '0', 'djs_supplier_status', '', 'success', 'Y', NULL, NOW()),
  (1007011, '1001', 1, '已终止', '1', 'djs_supplier_status', '', 'danger',  'N', NULL, NOW());

-- 3.5 djs_settle_type 新建
INSERT IGNORE INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (100702, '1001', '结算方式', 'djs_settle_type', NULL, NOW(), '供应商：现款现货 / 月结 / 季结');
INSERT IGNORE INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1007020, '1001', 0, '现款现货', 'cash',      'djs_settle_type', '', 'primary', 'Y', NULL, NOW()),
  (1007021, '1001', 1, '月结',     'monthly',   'djs_settle_type', '', 'warning', 'N', NULL, NOW()),
  (1007022, '1001', 2, '季结',     'quarterly', 'djs_settle_type', '', 'info',    'N', NULL, NOW());

-- ===========================
-- 4. 菜单按钮权限（SYS-MD-FIX-002 新增"设置店长"按钮）
--    SYS-MD-002 已 seed menu_id 5002 (门店目录) + 5020-5024 (list/add/edit/remove/export)；
--    本 ticket 在 5002 下新增 menu_id 5025 设置店长
-- ===========================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (5025, '设置店长', 5002, 6, '', '', '',
   1, 0, 'F', '0', '0',
   'djs:common:store:setManager', '#', 1, NOW(), 'SYS-MD-FIX-002');
