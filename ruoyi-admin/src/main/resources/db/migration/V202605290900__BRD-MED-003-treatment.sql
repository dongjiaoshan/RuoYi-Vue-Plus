-- ============================================================
-- BRD-MED-003 用药治疗（单只 + 批量）DDL
--
-- 目标：
--   1. 新建 t_breed_medicine_record 用药治疗流水表（单 master + N detail 拆批量）
--   2. t_breed_medicine_batch 加 stock_qty（D6 carryover #19 原始入库量绝对上界，
--      service 校验 quantity ≤ stock_qty 防退回累计污染）
--   3. seed 4 字典：djs_medicine_use_type / djs_medicine_reason / djs_medicine_way / djs_drug_type
--   4. seed sys_menu 7085-7089 段（CLAUDE.md §6 已分配的治疗台账段）
--
-- 字段权威：以本 DDL 为最终；prompt §Step 1.a 与 doc/06 §BRD-MED-003 命名分歧
-- （`medicine_id/medicine_name` vs `goods_id/goods_name`）按 prompt 命名（与
-- BRD-MED-001/002 同表系一致）。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_breed_medicine_record 用药治疗流水
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_breed_medicine_record;
CREATE TABLE t_breed_medicine_record (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id        VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    use_date         DATETIME      NOT NULL COMMENT '用药日期（业务日期，非 create_time）',
    pig_id           BIGINT        NULL COMMENT '猪只 ID（drug_type=1 必填；drug_type=2 master 行 NULL；drug_type=3 detail 行必填）',
    ear_no           VARCHAR(32)   NULL COMMENT '猪只耳号冗余（drug_type=1/3 时存）',
    master_id        BIGINT        NULL COMMENT '批量用药 master id（drug_type=3 detail 行回指）',
    drug_type        TINYINT       NOT NULL COMMENT '1=单只 / 2=批量 master / 3=批量 detail（字典 djs_drug_type）',
    medicine_type    VARCHAR(32)   NOT NULL COMMENT '用药类型（djs_medicine_use_type: health/treatment/vaccine）',
    medicine_reason  VARCHAR(32)   NULL COMMENT '用药原因（djs_medicine_reason）',
    medicine_way     VARCHAR(32)   NOT NULL COMMENT '用药方式（djs_medicine_way）',
    medicine_id      BIGINT        NOT NULL COMMENT '药品 FK → t_breed_medicine_info.id',
    medicine_name    VARCHAR(128)  NOT NULL COMMENT '药品名冗余（防药品改名影响历史）',
    batch_id         BIGINT        NOT NULL COMMENT '批次 FK → t_breed_medicine_batch.id（必填，从 3 天内已领批次中选）',
    usage_id         BIGINT        NULL COMMENT '领用记录 FK → t_breed_medicine_usage.id（追溯 3 天领用源头，V1 可选）',
    schedule_id      BIGINT        NULL COMMENT '用药 schedule FK → t_farm_production_cycle_config.id（V1 仅记录不强校验归属，预留 V2）',
    medicine_dosage  DECIMAL(12,3) NOT NULL COMMENT '用药剂量',
    operator_id      BIGINT        NOT NULL COMMENT '操作人 user_id（FK → sys_user.user_id，ADR-0007）',
    operator_name    VARCHAR(64)   NULL COMMENT '操作人冗余姓名（sys_user.nick_name 快照）',
    remark           VARCHAR(500)  NULL COMMENT '备注',
    create_dept      BIGINT        NULL COMMENT '创建部门',
    create_by        BIGINT        NULL COMMENT '创建人',
    create_time      DATETIME      NULL COMMENT '创建时间',
    update_by        BIGINT        NULL COMMENT '更新人',
    update_time      DATETIME      NULL COMMENT '更新时间',
    del_flag         CHAR(1)       DEFAULT '0' COMMENT '删除标志',
    del_unique       BIGINT        NOT NULL DEFAULT 0 COMMENT "软删 token（软删时同步 SET del_unique=id）",
    PRIMARY KEY (id),
    KEY idx_tenant_use_date (tenant_id, use_date),
    KEY idx_pig (tenant_id, pig_id),
    KEY idx_master (master_id),
    KEY idx_batch (batch_id),
    KEY idx_medicine (medicine_id),
    KEY idx_operator (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用药治疗流水（单只 + 批量 master/detail，BRD-MED-003）';

-- ------------------------------------------------------------
-- 2. t_breed_medicine_batch ALTER 加 stock_qty
-- ------------------------------------------------------------
ALTER TABLE t_breed_medicine_batch
    ADD COLUMN stock_qty DECIMAL(18,3) NOT NULL DEFAULT 0
    COMMENT '原始入库量（绝对上界；service 校验 quantity ≤ stock_qty 防退回累计污染）' AFTER quantity;

-- 历史回填：旧批次没记原始量，把当前 quantity 当作原始入库量
UPDATE t_breed_medicine_batch SET stock_qty = quantity WHERE stock_qty = 0;

-- ------------------------------------------------------------
-- 3. 字典 seed（ADR-0004 §2.2 显式 dict_id / dict_code）
-- ------------------------------------------------------------

-- 3.1 djs_medicine_use_type 用药类型（保健 / 治疗 / 疫苗，与药品库 djs_med_type 区分语义）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_medicine_use_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_medicine_use_type';
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, remark)
VALUES (100901, '1001', '用药类型', 'djs_medicine_use_type', 1, 'BRD-MED-003 保健/治疗/疫苗');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, remark) VALUES
    (1009010, '1001', 1, '保健', 'health',    'djs_medicine_use_type', '', 'success', 'N', 1, '保健用药'),
    (1009011, '1001', 2, '治疗', 'treatment', 'djs_medicine_use_type', '', 'warning', 'Y', 1, '治疗用药（默认）'),
    (1009012, '1001', 3, '疫苗', 'vaccine',   'djs_medicine_use_type', '', 'primary', 'N', 1, '疫苗接种');

-- 3.2 djs_medicine_reason 用药原因（origin/字典项整理.xlsx L15 + 常见症状）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_medicine_reason';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_medicine_reason';
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, remark)
VALUES (100902, '1001', '用药原因', 'djs_medicine_reason', 1, 'BRD-MED-003 治疗事件原因');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, remark) VALUES
    (1009020, '1001', 1, '腹泻',     'diarrhea',  'djs_medicine_reason', '', 'warning', 'N', 1, '消化道症状'),
    (1009021, '1001', 2, '发热',     'fever',     'djs_medicine_reason', '', 'danger',  'N', 1, '体温异常'),
    (1009022, '1001', 3, '咳嗽',     'cough',     'djs_medicine_reason', '', 'warning', 'N', 1, '呼吸道症状'),
    (1009023, '1001', 4, '皮肤病',   'skin',      'djs_medicine_reason', '', 'info',    'N', 1, '皮肤病变'),
    (1009024, '1001', 5, '关节炎',   'arthritis', 'djs_medicine_reason', '', 'info',    'N', 1, '运动系统'),
    (1009025, '1001', 6, '产后保健', 'postpartum','djs_medicine_reason', '', 'success', 'N', 1, '产后/恢复期'),
    (1009026, '1001', 7, '预防保健', 'prevent',   'djs_medicine_reason', '', 'success', 'N', 1, '常规预防'),
    (1009027, '1001', 8, '其他',     'other',     'djs_medicine_reason', '', 'default', 'N', 1, '其他原因');

-- 3.3 djs_medicine_way 用药方式（origin/字典项整理.xlsx L19）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_medicine_way';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_medicine_way';
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, remark)
VALUES (100903, '1001', '用药方式', 'djs_medicine_way', 1, 'BRD-MED-003 给药途径');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, remark) VALUES
    (1009030, '1001', 1, '拌料',   'mix_feed',  'djs_medicine_way', '', 'primary', 'Y', 1, '混入饲料（默认）'),
    (1009031, '1001', 2, '饮水',   'in_water',  'djs_medicine_way', '', 'primary', 'N', 1, '混入饮用水'),
    (1009032, '1001', 3, '注射',   'injection', 'djs_medicine_way', '', 'warning', 'N', 1, '肌肉/皮下/静脉注射'),
    (1009033, '1001', 4, '口服',   'oral',      'djs_medicine_way', '', 'primary', 'N', 1, '直接口服'),
    (1009034, '1001', 5, '喷雾',   'spray',     'djs_medicine_way', '', 'info',    'N', 1, '气雾给药'),
    (1009035, '1001', 6, '涂抹',   'topical',   'djs_medicine_way', '', 'info',    'N', 1, '外用涂抹');

-- 3.4 djs_drug_type 用药记录类型（1=单只 / 2=批量 master / 3=批量 detail）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_drug_type';
DELETE FROM sys_dict_type WHERE dict_type = 'djs_drug_type';
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, remark)
VALUES (100904, '1001', '用药记录类型', 'djs_drug_type', 1, 'BRD-MED-003 单只 vs 批量 master/detail');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, remark) VALUES
    (1009040, '1001', 1, '单只用药',     '1', 'djs_drug_type', '', 'primary', 'Y', 1, '一头猪一条记录'),
    (1009041, '1001', 2, '批量主记录',   '2', 'djs_drug_type', '', 'warning', 'N', 1, '批量用药 master，pig_id=null'),
    (1009042, '1001', 3, '批量明细记录', '3', 'djs_drug_type', '', 'info',    'N', 1, '批量用药 detail，回指 master_id');

-- ------------------------------------------------------------
-- 4. sys_menu seed（CLAUDE.md §6 治疗台账分支段 7085-7089）
-- ------------------------------------------------------------
DELETE FROM sys_role_menu WHERE menu_id IN (7085, 7086, 7087, 7088, 7089);
DELETE FROM sys_menu WHERE menu_id IN (7085, 7086, 7087, 7088, 7089);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, remark) VALUES
    (7085, '用药治疗', 7000, 11, 'med-record', 'djs-breed/med/record/index', '', 1, 0, 'C', '0', '0', 'djs:breed:med-record:list',  'edit',   100, 1, 'BRD-MED-003 治疗台账'),
    (7086, '治疗查询', 7085, 1,  '',           '',                            '', 1, 0, 'F', '0', '0', 'djs:breed:med-record:list',  '#',      100, 1, 'BRD-MED-003'),
    (7087, '治疗新增', 7085, 2,  '',           '',                            '', 1, 0, 'F', '0', '0', 'djs:breed:med-record:add',   '#',      100, 1, 'BRD-MED-003'),
    (7088, '治疗删除', 7085, 3,  '',           '',                            '', 1, 0, 'F', '0', '0', 'djs:breed:med-record:remove','#',      100, 1, 'BRD-MED-003'),
    (7089, '治疗导出', 7085, 4,  '',           '',                            '', 1, 0, 'F', '0', '0', 'djs:breed:med-record:export','#',      100, 1, 'BRD-MED-003');

-- 角色 1（超管）默认拥有全部
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 7085), (1, 7086), (1, 7087), (1, 7088), (1, 7089);
