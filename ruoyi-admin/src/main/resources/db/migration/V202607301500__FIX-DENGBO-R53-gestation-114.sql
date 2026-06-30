-- r53（邓博）：母猪妊娠期 default_value 141 是 114 的笔误（猪妊娠期约 114 天）。
-- 预产期 = 配种日 + sow_breed_to_farrow_days（PigCoreServiceImpl.computeDueDateMap FARROW 分支读此 key）。
UPDATE t_farm_production_cycle_config
SET default_value = 114
WHERE config_key = 'sow_breed_to_farrow_days' AND default_value = 141;

-- r53/r54：清掉 admin「母猪生产配置」表单误存的 custom_value=0（天数 0 无业务含义=未定制），
-- 回退 default_value（妊娠 114 / 哺乳 25）。配合 getValue 修复：custom_value<=0 视为未定制。
UPDATE t_farm_production_cycle_config
SET custom_value = NULL
WHERE config_key IN ('sow_breed_to_farrow_days', 'sow_farrow_to_wean_days') AND custom_value = 0;
