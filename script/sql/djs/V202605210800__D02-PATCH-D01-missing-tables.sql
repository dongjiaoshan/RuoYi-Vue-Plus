-- ============================================================
-- D02 开工前置补丁 — 补建 D01 SYS-INIT-001 漏建的 3 张表 + 1 个菜单占位
--   修补目标：
--     1. t_md_biz_code_rule       —— SYS-INFRA-004 编码规则配置
--     2. t_md_biz_code_sequence   —— SYS-INFRA-004 编码序号（按日/按月/按年）
--     3. t_md_person              —— SYS-MD-001 人员主数据
--     4. sys_menu menu_id=5001    —— SYS-AUTH-001 人员管理二级菜单占位（5000 通用主数据下）
--   字段风格对齐 D01 SYS-INIT-001 common.sql 既有表（tenant_id VARCHAR(20)/del_unique BIGINT 应用层 fill）
-- ============================================================

-- ------------------------------------------------------------
-- 1. t_md_biz_code_rule（业务编码规则配置）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_md_biz_code_rule;
CREATE TABLE t_md_biz_code_rule (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id    VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  code_type    VARCHAR(32)  NOT NULL COMMENT '编码类型 EAR_NO/TRACE_CODE/DEMAND_NO/SHIP_NO/PACK_NO/STOCK_FLOW_NO ...',
  pattern      VARCHAR(255) NOT NULL COMMENT '编码格式串，支持占位符 {farmCode2}{barnCode2}{yyMM}{yyyyMMdd}{dailySeq4}{seq4}{seq6} 等',
  daily_reset  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否每日重置序号 1=是 0=否',
  prefix       VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '固定前缀（如 T/D/S/P/F）',
  seq_length   INT          NOT NULL DEFAULT 4 COMMENT '序号位数（4/6）',
  status       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态 0=启用 1=停用',
  create_dept  BIGINT       NULL COMMENT '创建部门',
  create_by    BIGINT       NULL COMMENT '创建人',
  create_time  DATETIME     NULL COMMENT '创建时间',
  update_by    BIGINT       NULL COMMENT '更新人',
  update_time  DATETIME     NULL COMMENT '更新时间',
  del_flag     CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark       VARCHAR(500) NULL COMMENT '备注',
  del_unique   BIGINT       NOT NULL DEFAULT 0 COMMENT '软删除生成 token（应用层 fill）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code_type (tenant_id, code_type, del_unique),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务编码规则配置（SYS-INFRA-004）';

-- ------------------------------------------------------------
-- 2. t_md_biz_code_sequence（业务编码序号表）
--   按 (tenant_id, code_type, seq_date) 维度计数；daily_reset=0 时 seq_date 填空串
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_md_biz_code_sequence;
CREATE TABLE t_md_biz_code_sequence (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id    VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  code_type    VARCHAR(32)  NOT NULL COMMENT '编码类型',
  seq_date     VARCHAR(8)   NOT NULL DEFAULT '' COMMENT '序号统计周期：yyyyMMdd / yyyyMM / yyyy / 空串（终生）',
  current_seq  BIGINT       NOT NULL DEFAULT 0 COMMENT '当前已用最大序号',
  create_dept  BIGINT       NULL COMMENT '创建部门',
  create_by    BIGINT       NULL COMMENT '创建人',
  create_time  DATETIME     NULL COMMENT '创建时间',
  update_by    BIGINT       NULL COMMENT '更新人',
  update_time  DATETIME     NULL COMMENT '更新时间',
  del_flag     CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark       VARCHAR(500) NULL COMMENT '备注',
  del_unique   BIGINT       NOT NULL DEFAULT 0 COMMENT '软删除生成 token（应用层 fill）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_code_seq (tenant_id, code_type, seq_date, del_unique)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务编码序号表（SYS-INFRA-004 并发安全计数）';

-- ------------------------------------------------------------
-- 3. t_md_person（人员主数据）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_md_person;
CREATE TABLE t_md_person (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id    VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  person_code  VARCHAR(32)  NOT NULL COMMENT '人员编号（SYS-INFRA-004 MEMBER_NO 生成）',
  name         VARCHAR(64)  NOT NULL COMMENT '姓名',
  gender       CHAR(1)      NULL COMMENT '性别 0=男 1=女 (字典 sys_user_sex)',
  phone        VARCHAR(20)  NULL COMMENT '联系电话',
  id_card      VARCHAR(20)  NULL COMMENT '身份证号',
  position     VARCHAR(64)  NULL COMMENT '岗位描述',
  post_id      BIGINT       NULL COMMENT '岗位（sys_post.post_id 外键，区分 admin 角色 vs 微信端角色）',
  hire_date    DATE         NULL COMMENT '入职日期',
  status       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态 0=在职 1=离职 2=试用 (字典 djs_person_status)',
  avatar_url   VARCHAR(500) NULL COMMENT '头像（OSS key，SYS-INFRA-002）',
  create_dept  BIGINT       NULL COMMENT '创建部门',
  create_by    BIGINT       NULL COMMENT '创建人',
  create_time  DATETIME     NULL COMMENT '创建时间',
  update_by    BIGINT       NULL COMMENT '更新人',
  update_time  DATETIME     NULL COMMENT '更新时间',
  del_flag     CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark       VARCHAR(500) NULL COMMENT '备注',
  del_unique   BIGINT       NOT NULL DEFAULT 0 COMMENT '软删除生成 token（应用层 fill）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_person_code (tenant_id, person_code, del_unique),
  KEY idx_post (post_id),
  KEY idx_status (status),
  KEY idx_tenant_create (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员主数据（SYS-MD-001）';

-- ------------------------------------------------------------
-- 4. menu_id 5001 = 人员管理（5000 通用主数据下的二级菜单占位）
--   按钮权限菜单 5010-5014 由 SYS-MD-001 自己 seed
--   role_id 101 (boss) / 102 (manager) 自动挂载（D01 SYS-AUTH-001 已用 BETWEEN 5000 AND 10999 兜底）
--     —— D01 把 role 101-103 全挂 5000-10999 区间，5001 INSERT 后无需补 sys_role_menu
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
   menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (5001, '人员管理', 5000, 1, 'person', 'djs-common/person/index', '', 1, 0,
   'C', '0', '0', 'djs:common:person:list', 'user', 1, NOW(), 'SYS-MD-001 占位');
