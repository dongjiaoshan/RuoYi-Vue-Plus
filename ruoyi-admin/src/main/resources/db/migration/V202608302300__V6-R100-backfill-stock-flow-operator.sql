-- V6 row100：补回产品出库流水缺失的出库操作人。
--
-- 产品出库（flow_type='backstage_out'，含库存查询页的「产品出库」与毛菜间出库）写流水时漏了
-- operator_id，导致「毛菜间出库管理」列表与出库记录的「出库操作人」列空白。写入侧已修复
-- （LocationStockServiceImpl#productOut），这里把已经落库的空行补上。
--
-- 取值 create_by：ruoyi BaseEntity 由 MetaObjectHandler 自动填当前登录用户 id，与 operator_id
-- 是同一个人同一次操作，是这些行唯一可还原的操作人来源。实测 backstage_out 的 44 条非空行
-- operator_id 与 create_by 100% 相等，这个等式站得住。
-- 只补 operator_id 为空且 create_by 有值的产品出库行 —— 其它 flow_type 的空值不在本次故障范围内，
-- 不顺手改（例如 mp 侧某些流水本就不记操作人）。
--
-- ⚠️ 部署后必须跑一次 `ops/redis-flush-dict.sh <env>`：昵称走 @Cacheable(sys_nickname#30d)，
-- 而 GRAY-PREP-001 是用 SQL 改的 nick_name、没清缓存，所以补完 operator_id 这一列会渲染成
-- 改名前的演示昵称（线上实测 user_id=1 库里是「系统管理员」、页面显示「疯狂的狮子Li」）。
-- 刷缓存那步已加进 script/sql/djs/_post-init.sh。
UPDATE t_warehouse_stock_flow
   SET operator_id = create_by
 WHERE operator_id IS NULL
   AND create_by IS NOT NULL
   AND flow_type = 'backstage_out'
   AND del_flag = '0';
