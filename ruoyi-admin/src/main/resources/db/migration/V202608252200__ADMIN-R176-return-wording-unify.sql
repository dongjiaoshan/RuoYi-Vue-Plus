-- admin row176：admin 端「退货」统一改「退回」。
--
-- 口径：仓库侧「退货记录」与门店侧「退回管理」指的是同一条门店退回仓库的业务链，
-- 两套叫法并存会让操作员以为是两件事，统一到「退回」。
-- 前端文案走 i18n（plus-ui zh_CN.ts），落库的展示文案（菜单名 / 字典名 / 字典标签 / 字典说明）在此对齐。
--
-- 只改展示文案：dict_value（如 return_goods_in）、perms、path、component 一律不动，
-- 已落库的业务数据与权限串不受影响。
-- 幂等：改完不再有「退货」，重复执行匹配 0 行。

UPDATE sys_menu      SET menu_name  = REPLACE(menu_name, '退货', '退回')  WHERE menu_name  LIKE '%退货%';
UPDATE sys_dict_type SET dict_name  = REPLACE(dict_name, '退货', '退回')  WHERE dict_name  LIKE '%退货%';
UPDATE sys_dict_data SET dict_label = REPLACE(dict_label, '退货', '退回') WHERE dict_label LIKE '%退货%';

-- 字典说明是给操作员看的（DICT-REFACTOR-002 口径），一并对齐
UPDATE sys_dict_type SET remark = REPLACE(remark, '退货', '退回') WHERE remark LIKE '%退货%';

-- 仓库侧 9210 直译成「退回记录」会和门店侧 10350「退回记录」（门店管理 > 退回管理 > 退回记录）
-- 侧边栏同名。两者视角不同：9210 是仓库收退回的台账，10350 是门店发起退回的台账。仓库侧加前缀区分。
UPDATE sys_menu SET menu_name = '仓库退回记录' WHERE menu_id = 9210;
