-- ============================================================
-- WMS-OUTSOURCE-DELGUARD-001  外购猪只删除拦截（已进入下游处理流程不允许删）
-- 问题：外购猪只录入后经燎毛/分割（白条进入下游态），admin「外购猪只」列表点删除仍能删成功，无拦截。
-- 解法：给 t_warehouse_outsource_pig 加 bar_id 列（= 镜像白条 t_warehouse_bar_info.bar_id 业务码），
--   录入时 createOutsourceBar 回写 barId，建立外购台账 ↔ 白条精确单头关联；删除前按 bar_id 反查
--   bar_info.status，非 pending_singe（已燎毛/分割/入库等下游态）则拦截。
-- 存量回填：按 supplier_id + buy_date + buy_weight 1:1 匹配镜像白条（已核实 4 行 100% 唯一可匹配）。
-- ============================================================

SET NAMES utf8mb4;

-- 1) 加列（VARCHAR 存白条业务码 BAR+yyMMdd+4；NULL 允许，存量回填 + 新录入写值）
ALTER TABLE t_warehouse_outsource_pig
    ADD COLUMN bar_id VARCHAR(32) NULL COMMENT '镜像白条业务码（= t_warehouse_bar_info.bar_id；录入时回写，删除前据此反查白条状态做下游拦截）' AFTER supplier_id;

-- 2) 索引：删除拦截按 bar_id 反查 bar_info
ALTER TABLE t_warehouse_outsource_pig
    ADD KEY idx_tenant_barid (tenant_id, bar_id);

-- 3) 存量回填：按 supplier_id + buy_date + buy_weight 唯一匹配镜像白条
UPDATE t_warehouse_outsource_pig op
JOIN t_warehouse_bar_info b
  ON b.supplier_id = op.supplier_id
 AND b.buy_date = op.purchase_date
 AND b.buy_weight = op.pig_weight
 AND b.del_flag = '0'
SET op.bar_id = b.bar_id
WHERE op.bar_id IS NULL;
