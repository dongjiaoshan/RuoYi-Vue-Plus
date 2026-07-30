-- admin row87：内部引种复用既有猪只，旧实现只更新状态时间，未同步猪只主表 introduce_date。
-- 仅回填当前为空的字段，不覆盖人工修订值；同一猪有多条有效内部引种记录时取最近日期。
UPDATE t_farm_pig_info p
JOIN (
    SELECT tenant_id, pig_id, MAX(introduce_date) AS introduce_date
    FROM t_farm_pig_introduce
    WHERE introduce_type = 'internal'
      AND pig_id IS NOT NULL
      AND introduce_date IS NOT NULL
      AND del_flag = '0'
    GROUP BY tenant_id, pig_id
) i
  ON i.tenant_id = p.tenant_id
 AND i.pig_id = p.id
SET p.introduce_date = i.introduce_date
WHERE p.introduce_date IS NULL
  AND p.del_flag = '0';
