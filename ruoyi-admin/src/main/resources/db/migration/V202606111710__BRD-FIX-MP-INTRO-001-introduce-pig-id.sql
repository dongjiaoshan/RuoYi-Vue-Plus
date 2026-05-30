-- BRD-FIX-MP-INTRO-001：内部引种「登记一头已存在的猪进引种台账」需要关联已存在 pig_id。
-- 外部引种（一次引种 → 新建 N 头猪）pig_id 为 NULL；内部引种 pig_id 指向已存在猪只。
ALTER TABLE t_farm_pig_introduce
  ADD COLUMN pig_id BIGINT NULL COMMENT '内部引种关联的已存在猪只 ID（外部引种为 NULL，BRD-FIX-MP-INTRO-001）' AFTER pen_id;

-- 引种人员（原型 86：引种信息段「引种人员」，V1 mp 无员工 picker，落自由文本/编码）。
ALTER TABLE t_farm_pig_introduce
  ADD COLUMN operator VARCHAR(64) NULL COMMENT '引种人员（原型 86，V1 自由文本，BRD-FIX-MP-INTRO-001）' AFTER pig_id;

-- 内部引种体重（kg），原型 86「引种猪只体重」。
ALTER TABLE t_farm_pig_introduce
  ADD COLUMN introduce_weight DECIMAL(8,2) NULL COMMENT '引种体重 kg（原型 86，内部引种登记，BRD-FIX-MP-INTRO-001）' AFTER operator;

CREATE INDEX idx_introduce_pig_id ON t_farm_pig_introduce (pig_id);
