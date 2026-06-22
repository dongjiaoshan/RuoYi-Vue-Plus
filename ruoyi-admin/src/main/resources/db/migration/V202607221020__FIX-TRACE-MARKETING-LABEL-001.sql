-- FIX-TRACE-MARKETING-LABEL-001：追溯事件 marketing 标签对齐两个客户面页（审计 P3-7）
--
-- djs_trace_content 的 marketing 原标签「出栏上市」与客户面追溯页文案不一致，统一为「营销出栏」。

UPDATE sys_dict_data
SET dict_label = '营销出栏'
WHERE dict_type = 'djs_trace_content' AND dict_value = 'marketing';

-- 应用后需跑 bash script/sql/djs/_post-init.sh flush redis 字典缓存
