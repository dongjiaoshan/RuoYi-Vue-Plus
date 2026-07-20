-- FIX-WMS-FLOWDICT-DEPT-RETURN-001：养殖/种植物资退回 入库方式 label「领用退回」→「部门退回」
-- （测试 row38#3；Kevin 2026-06-24 口径）
--
-- 背景
--   mp 物资领用/退回（养殖/种植）= 部门领用 / 部门退回；仓库领用/退回（针对门店需求打包、实时）
--   = 生产领用 / 生产退回，二者业务不同、须区分。
--   领用侧已对齐：dept_pick_out=部门领用（养殖/种植） · prod_pick_out=生产领用（仓库打包）。
--   退回侧 pick_return_in（resolveReturnFlowType 非 warehouse 分支 = 养殖/种植 + 来源未知兜底）
--   的 label 仍叫「领用退回」→ 改「部门退回」，与「部门领用」对称、与仓库的「生产退回」区分。
--   仓库 prod_return_in=生产退回、门店 store_return_in=门店退回 不动。
--   仅改 dict_label（dict_value=pick_return_in 不变，不动任何代码 / 统计逻辑）。
--   改后须 flush *:sys_dict redis 缓存（script/sql/djs/_post-init.sh）才即时生效，否则等字典缓存 TTL。
SET NAMES utf8mb4;

UPDATE sys_dict_data SET dict_label = '部门退回'
 WHERE dict_type = 'djs_flow_type' AND dict_value = 'pick_return_in';
