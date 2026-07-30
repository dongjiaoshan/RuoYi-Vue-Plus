-- WMS-PRODUCT-WORKSHOP-MULTI-001 生产车间单选改多选
--
-- 背景：同一猪肉成品既可在仓库「肉品打包间」生产、也可在门店「门店打包间」生产，
-- 单值 TINYINT 只能二选一。改为 CSV 多值（如 '3,5'），查询侧统一走 FIND_IN_SET。
--
-- 存储约定：
--   · 逗号分隔、无空格、无尾逗号（如 '3' / '3,5'）；空归属写 NULL 不写空串。
--   · 字典 djs_product_workshop 值域 1-7，单值最长 1 位、7 个车间全挂最长 13 字符，VARCHAR(32) 足量。
--   · 查询一律 FIND_IN_SET('<车间码>', product_workshop) > 0，禁用 = / IN（CSV 下语义错）。

-- 1. TINYINT -> VARCHAR(32)。MySQL 隐式把 3 转成 '3'，存量 695 行单值语义不变，无需额外 UPDATE。
ALTER TABLE t_warehouse_product_info
    MODIFY COLUMN product_workshop VARCHAR(32) NULL
    COMMENT '生产车间（字典 djs_product_workshop，CSV 多值）：1=燎毛间/2=分割间/3=肉品打包间/4=蔬菜打包间/5=门店打包间/6=其他产品打包间/7=礼盒打包间';

-- 2. 防御性清洗：把空串归一成 NULL（FIND_IN_SET 对空串恒不命中，但 NULL 语义更准，
--    且 admin 表单「未选车间」回写的是 NULL，两种空值混存会让「车间为空」筛选口径分裂）。
UPDATE t_warehouse_product_info
   SET product_workshop = NULL
 WHERE product_workshop = '';
