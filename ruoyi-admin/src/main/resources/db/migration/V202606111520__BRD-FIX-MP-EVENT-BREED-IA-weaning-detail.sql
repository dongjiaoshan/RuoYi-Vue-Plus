-- BRD-FIX-MP-EVENT-BREED-IA-001：断奶逐头录重明细表（Kevin 2026-05-29 拍板，采用原型 91 逐头录重）
-- 替代 WeaningBo 原「OQ-11 fallback 仅汇总」：断奶仔猪逐头录体重 → FE 汇总总重 + 均重，
-- 提交 = 汇总值（t_farm_pig_weaning 不变）+ 明细数组（本表，每行一头仔猪）。
-- 含 6 公共字段 + tenant_id VARCHAR(20) DEFAULT '1001'（CLAUDE.md §6.2/§6.5）；UNIQUE 含 tenant_id（§6.3）。

CREATE TABLE t_farm_pig_weaning_detail (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  weaning_id      BIGINT       NOT NULL COMMENT '关联断奶记录 ID（FK → t_farm_pig_weaning.id）',
  piglet_seq      INT          NOT NULL COMMENT '仔猪序号（同一断奶记录内从 1 起递增）',
  ear_no          VARCHAR(32)  NULL COMMENT '仔猪耳号（可空，原型逐头行允许无耳号只录重）',
  weight          DECIMAL(8,3) NOT NULL COMMENT '断奶体重 kg',
  create_dept     BIGINT       NULL COMMENT '创建部门',
  create_by       BIGINT       NULL COMMENT '录入人',
  create_time     DATETIME     NULL COMMENT '录入时间',
  update_by       BIGINT       NULL COMMENT '更新人',
  update_time     DATETIME     NULL COMMENT '更新时间',
  del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志',
  remark          VARCHAR(255) NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_weaning_seq (tenant_id, weaning_id, piglet_seq),
  KEY idx_weaning (weaning_id),
  KEY idx_tenant_create (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='母猪断奶逐头录重明细（BRD-FIX-MP-EVENT-BREED-IA-001）';
