-- STORE-RETURN-UNIFY-001 门店退货到仓库统一：退回操作历史方向纠偏
--
-- 背景：门店「退回操作」(batchCreate) 曾把「门店退货给仓库」的方向硬编码成 customer_to_store
-- （应为 store_to_warehouse）。纠偏后：
--   1) 仓库「退货记录」(admin /djs/store/return/store-daily) 与 mp 退货管理可见这些数据；
--   2) 门店盘点「退回量」(wh_return_qty = Σ return_direction='store_to_warehouse') 恢复正确（原恒 0）。
--
-- 安全性：前端唯一批量入口 batchCreate 全落 customer_to_store，实践即门店退仓库误标，可全量纠正。
-- 幂等：仅改 customer_to_store 行；重复执行无副作用。
UPDATE t_store_return
SET return_direction = 'store_to_warehouse'
WHERE return_direction = 'customer_to_store'
  AND del_flag = '0';

-- 注：旧 t_warehouse_return_product 中 store_to_warehouse 行为开发期测试数据（仓库退货页已改读
-- t_store_return），不迁移、留作历史；新流程起点干净。如后续确认有真实业务再单独 INSERT 迁移。
