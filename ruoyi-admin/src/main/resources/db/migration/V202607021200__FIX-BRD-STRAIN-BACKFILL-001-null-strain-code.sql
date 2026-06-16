-- FIX-BRD-STRAIN-BACKFILL-001：回填存量种猪 pig_strain_code 的 NULL/空串脏值（解阻塞仔猪耳号打标）。
-- 根因：选窝后 previewEarNos / batchEarTag 取 mother.getPigStrainCode() 传入 EarNoAllocator.buildPrefix()，
--   strainCode 为空即抛 ServiceException「品系码不能为空」，前端耳号列报「待生成」、toast 报错。
--   01PZ26060001 等 12 位旧格式老种猪在 ADR-0011/D12X 前引种入库，pig_strain_code 字段为 NULL；
--   V202606161010 只规范了 pig_breed_code='1'→'01'，未回填 strain_code=NULL 的行。
-- 回填值 '1' = 恩施黑猪（ADR-0011 §2.1，djs_pig_strain dict_value='1'），该农场主力品系、01PZ* 老号同批引入。
--   如需精确对应不同品系，Kevin 可在 admin 端逐头改准；backfill '1' 先解阻塞打标流程。
-- 校验逻辑本身正确，不动 EarNoAllocator / PigEarTagServiceImpl 代码——纯数据问题。
-- V202607021100 > 当前 flyway max V202607021000。

-- 1. 母猪/猪只主表：strain_code 空 → 回填 '1'（直接影响耳号生成）
UPDATE t_farm_pig_info
SET pig_strain_code = '1'
WHERE del_flag = '0'
  AND (pig_strain_code IS NULL OR pig_strain_code = '');

-- 2. 引种登记表：strain_code 空 → 回填 '1'（不影响耳号生成，仅 admin 列表展示字段一致性）
UPDATE t_farm_pig_introduce
SET pig_strain_code = '1'
WHERE del_flag = '0'
  AND (pig_strain_code IS NULL OR pig_strain_code = '');
