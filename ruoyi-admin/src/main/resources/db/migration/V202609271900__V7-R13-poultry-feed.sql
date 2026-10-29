-- V7 R13 / R17 / R18 / R19 家禽饲料
--
-- 甲方 V7「需求和问题（测试环境）」：
--   行13：果蔬出库管理去向增加「家禽饲料」，出库逻辑与猪饲料一致；饲喂记录区分猪 / 家禽，历史一律为猪。
--   行17：admin 有机饲喂记录按「日期 × 饲喂类型」分开统计，框数确认也分开。
--   行18：mp 有机饲喂记录只统计猪只饲料。
--   行19：库存查询「产品内部处理」出库去向拆成「猪只饲料」「家禽饲料」，两者都写出库流水 + 有机饲喂记录。
--
-- 1. t_warehouse_feed_log.feed_animal：饲喂动物（字典 djs_feed_animal：pig 猪只饲料 / poultry 家禽饲料），
--    DEFAULT 'pig' 让历史行全部落为猪。与 feed_type（饲喂来源：毛菜间 / 仓库）是两个独立维度。
-- 2. t_warehouse_feed_daily_confirm.feed_animal：框数确认按 (日期, 饲喂动物) 各一条，唯一键随之加入 feed_animal。
-- 3. 字典 djs_feed_animal（新类型）+ djs_stock_out_dest 追加 poultry_feed = 家禽饲料（既有 feed = 猪只饲料不动）。
--
-- 生效：djs_stock_out_dest 已在 redis 缓存，部署后必须 flush 字典缓存
--       （staging：ops/redis-flush-dict.sh staging；本地：script/sql/djs/_post-init.sh），否则下拉 / 翻译看不到「家禽饲料」。
--       djs_feed_animal 是新类型，首次读取才进缓存，flush 一并覆盖即可。
-- 幂等：加列 / 换唯一键先查 information_schema；字典 ON DUPLICATE KEY UPDATE。

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. feed_log 饲喂动物
-- ------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_feed_log'
               AND COLUMN_NAME = 'feed_animal');
SET @s1 := IF(@c1 > 0, 'SELECT 1',
  "ALTER TABLE t_warehouse_feed_log
     ADD COLUMN feed_animal VARCHAR(16) NOT NULL DEFAULT 'pig'
     COMMENT '饲喂动物（字典 djs_feed_animal：pig 猪只饲料 / poultry 家禽饲料）' AFTER feed_type");
PREPARE st1 FROM @s1;
EXECUTE st1;
DEALLOCATE PREPARE st1;

-- ------------------------------------------------------------
-- 2. 日确认按 (日期, 饲喂动物) 各一条
-- ------------------------------------------------------------
SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_feed_daily_confirm'
               AND COLUMN_NAME = 'feed_animal');
SET @s2 := IF(@c2 > 0, 'SELECT 1',
  "ALTER TABLE t_warehouse_feed_daily_confirm
     ADD COLUMN feed_animal VARCHAR(16) NOT NULL DEFAULT 'pig'
     COMMENT '饲喂动物（字典 djs_feed_animal：pig 猪只饲料 / poultry 家禽饲料）' AFTER feed_date");
PREPARE st2 FROM @s2;
EXECUTE st2;
DEALLOCATE PREPARE st2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_feed_daily_confirm'
               AND INDEX_NAME = 'uk_feed_date');
SET @s3 := IF(@c3 = 0, 'SELECT 1',
  'ALTER TABLE t_warehouse_feed_daily_confirm DROP INDEX uk_feed_date');
PREPARE st3 FROM @s3;
EXECUTE st3;
DEALLOCATE PREPARE st3;

SET @c4 := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_warehouse_feed_daily_confirm'
               AND INDEX_NAME = 'uk_feed_date_animal');
SET @s4 := IF(@c4 > 0, 'SELECT 1',
  'ALTER TABLE t_warehouse_feed_daily_confirm
     ADD UNIQUE KEY uk_feed_date_animal (tenant_id, feed_date, feed_animal, del_unique)');
PREPARE st4 FROM @s4;
EXECUTE st4;
DEALLOCATE PREPARE st4;

ALTER TABLE t_warehouse_feed_daily_confirm COMMENT = '有机饲喂日确认（框数 + 确认人，每日每饲喂动物一条）';

-- ------------------------------------------------------------
-- 3. 字典
-- ------------------------------------------------------------
INSERT INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (1026102701, '1001', '饲喂类型', 'djs_feed_animal', 1, NOW(), '有机饲喂的饲喂动物：猪只饲料 / 家禽饲料')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), remark=VALUES(remark);

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1026102711, '1001', 1, '猪只饲料', 'pig',     'djs_feed_animal', '', 'primary', 'Y', 1, NOW(), ''),
    (1026102712, '1001', 2, '家禽饲料', 'poultry', 'djs_feed_animal', '', 'warning', 'N', 1, NOW(), '')
ON DUPLICATE KEY UPDATE dict_label=VALUES(dict_label), dict_sort=VALUES(dict_sort), list_class=VALUES(list_class);

-- 出库去向：家禽饲料。排在猪只饲料(feed, sort 4) 旁边；值由果蔬出库 / 产品内部处理 / 毛菜间出库写入，
-- 与 feed 一样是「带有机饲喂下游」的去向，产品出库等手选下拉在前端 HIDDEN_OUT_DEST 里隐藏。
INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1026102721, '1001', 4, '家禽饲料', 'poultry_feed', 'djs_stock_out_dest', '', 'info', 'N', 1, NOW(), 'V7-R13')
ON DUPLICATE KEY UPDATE dict_label=VALUES(dict_label), dict_sort=VALUES(dict_sort);

-- 验收 query
--   SELECT feed_animal, COUNT(*) FROM t_warehouse_feed_log GROUP BY feed_animal;
--   SHOW INDEX FROM t_warehouse_feed_daily_confirm WHERE Key_name LIKE 'uk_feed%';
--   SELECT dict_type, dict_value, dict_label FROM sys_dict_data
--    WHERE dict_type = 'djs_feed_animal' OR (dict_type = 'djs_stock_out_dest' AND dict_value IN ('feed', 'poultry_feed'));
