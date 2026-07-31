-- admin row164：栏位名称只存栏位名，不带栋舍名前缀。
--
-- t_farm_barn_pen.pen_name 权威口径 = 「{栏位类型中文}{序号}」（如「散栏01」「产床12」），
-- 栋舍归属由 barn_id 表达，展示端需要时自行拼「栋舍名 + 栏位名」，不在名称里冗余存一份。
-- 应用内新建栏位（BarnServiceImpl.generatePens）本来就按此口径写；带前缀的存量只来自初始数据生成器，
-- 本次把存量对齐到权威口径。
--
-- 截断规则：pen_name 的前 CHAR_LENGTH(barn_name) 个字符恰好等于本栋舍名时，截去该段。
-- 用 LEFT()=barn_name 而非 LIKE，避免栋舍名里的 % / _ 被当通配符。
-- 幂等：截断后名称不再以栋舍名开头，重复执行匹配 0 行；截去后为空串的行（pen_name 等于栋舍名）不动，
-- 避免把名称清空。
-- 含软删行一并处理，保持同一栋舍下命名口径一致。
-- 唯一约束是 uk_pen_code(tenant_id, barn_id, pen_code, del_unique)，不含 pen_name，截断不会撞唯一键。
UPDATE t_farm_barn_pen p
  JOIN t_farm_barn_info b ON b.id = p.barn_id
   SET p.pen_name = SUBSTRING(p.pen_name, CHAR_LENGTH(b.barn_name) + 1)
 WHERE b.barn_name IS NOT NULL
   AND b.barn_name <> ''
   AND CHAR_LENGTH(p.pen_name) > CHAR_LENGTH(b.barn_name)
   AND LEFT(p.pen_name, CHAR_LENGTH(b.barn_name)) = b.barn_name;
