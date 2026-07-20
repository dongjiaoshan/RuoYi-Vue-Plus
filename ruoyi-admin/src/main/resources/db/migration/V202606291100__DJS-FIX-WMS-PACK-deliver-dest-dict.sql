-- DJS-FIX-WMS-PACK：生产管理「打包录入」发送方式/位置（发货月台 / 邮寄 / 礼盒）对齐原型。
--
-- 原型「肉品打包管理」「果蔬打包管理」右侧「发送位置」= 发货月台 / 邮寄 / 礼盒（三选）；
-- 「其他产品打包管理」「发送方式」= 发货月台 / 邮寄（二选）。
-- 现有 djs_deliver_type（发货 / 邮寄 / 销售）语义是「发货方式」，不含「发货月台 / 礼盒」，
-- 与原型「发送位置」不是一回事，故新建 djs_pack_send_dest 字典专表「打包发送目的地」。
--
-- platform = 发货月台（打包后送发货月台，走正常发货链）
-- mail     = 邮寄（个人邮寄）
-- gift     = 礼盒（礼盒装；其他产品打包二选时不出现）
--
-- 同步给 t_warehouse_product_production 加 deliver_dest VARCHAR(16) 列落库发送目的地
-- （区别于既有 deliver_type TINYINT 发货方式列，互不覆盖）。
--
-- 幂等：dict 用 INSERT IGNORE（dict_code / dict_id 主键去重）；列用 information_schema 存在性判断后再 ADD。

SET NAMES utf8mb4;

-- ============== 1. djs_pack_send_dest 字典（3 值）==============
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102270, '1001', '打包发送位置', 'djs_pack_send_dest', 1, NOW(), 'DJS-FIX-WMS-PACK 生产管理打包录入发送位置');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1022700, '1001', 0, '发货月台', 'platform', 'djs_pack_send_dest', '', 'primary', 'Y', 1, NOW(), '送发货月台'),
    (1022701, '1001', 1, '邮寄',     'mail',     'djs_pack_send_dest', '', 'warning', 'N', 1, NOW(), '个人邮寄'),
    (1022702, '1001', 2, '礼盒',     'gift',     'djs_pack_send_dest', '', 'success', 'N', 1, NOW(), '礼盒装');

-- ============== 2. t_warehouse_product_production 加 deliver_dest 列（幂等）==============
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_warehouse_product_production'
      AND COLUMN_NAME = 'deliver_dest'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE t_warehouse_product_production ADD COLUMN deliver_dest VARCHAR(16) NULL COMMENT ''发送位置字典 djs_pack_send_dest：platform 发货月台 / mail 邮寄 / gift 礼盒'' AFTER deliver_type',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
