-- admin row176 收口：修「退货→退回」批量改名带出的字典 label 撞车 + 补三处漏网 remark。
--
-- 1) djs_flow_type 撞车
--    return_in（WMS-MAT-001 物资领用退回）原本叫「退回入库」，
--    return_goods_in（DJS-FIX-WMS-RALN-B 入库记录入库方式）原本叫「退货入库」，
--    统一改名后两项字面完全一样，入库记录「入库方式」下拉、出入库流水与打包流水的
--    「流水类型」多选里都出现两个「退回入库」，选哪个全靠猜。
--    两个 value 都已停写（后端无写入方；现行口径拆成 store_return_in / prod_return_in / pick_return_in），
--    但字典项按项目既有约定不删——历史流水仍要靠它翻译中文。
--    做法：给后出现、且已被三值拆分取代的 return_goods_in 加「(旧)」后缀。
--    只改 label 不动 value，不改任何下拉的成员集合。
--    幂等：加了后缀就不再等于「退回入库」，重复执行匹配 0 行。
UPDATE sys_dict_data
   SET dict_label = '退回入库(旧)'
 WHERE dict_type = 'djs_flow_type'
   AND dict_value = 'return_goods_in'
   AND dict_label = '退回入库';

-- 2) 上一版只扫了 sys_dict_type.remark，漏了另外两张表的 remark。两者都在 admin 可见：
--    sys_dict_data.remark → 系统管理>字典管理 列表「备注」列 + 编辑表单；
--    sys_menu.remark      → 系统管理>菜单管理 编辑表单（渲染成「激活路径」输入框）。
UPDATE sys_dict_data SET remark = REPLACE(remark, '退货', '退回') WHERE remark LIKE '%退货%';
UPDATE sys_menu      SET remark = REPLACE(remark, '退货', '退回') WHERE remark LIKE '%退货%';

-- sys_oper_log.title 里的历史「退货管理」不动：那是操作日志审计留痕，
-- 记录的是当时那次操作的模块名，改历史审计数据不合适。
