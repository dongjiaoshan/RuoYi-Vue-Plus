ALTER TABLE t_farm_indicator_record
  ADD COLUMN begin_production_sow_count INT NULL DEFAULT NULL COMMENT '期初生产母猪存栏，前一自然日缺报为NULL',
  ADD COLUMN begin_fattening_count INT NULL DEFAULT NULL COMMENT '期初育肥猪存栏，前一自然日缺报为NULL',
  ADD COLUMN new_fattening_count INT NOT NULL DEFAULT 0 COMMENT '新进育肥猪头数（日内去重）',
  ADD COLUMN avg_live_born_per_litter DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '窝均活仔数（live_born_count除以farrow_sow_count）',
  ADD COLUMN death_cull_count INT NOT NULL DEFAULT 0 COMMENT '死淘数量（取同猪最后录入的有效死淘记录，按业务日统计）',
  ADD COLUMN feed_weight_kg DECIMAL(16,3) NOT NULL DEFAULT 0 COMMENT '猪用饲料消耗量（kg）',
  ADD COLUMN organic_feed_weight_kg DECIMAL(16,3) NOT NULL DEFAULT 0 COMMENT '猪用有机果蔬饲料消耗量（kg）',
  ADD COLUMN non_kg_feed_record_count INT NOT NULL DEFAULT 0 COMMENT '猪用仓库饲喂中有商品但无法折算kg的记录条数（含单位缺失）',
  ADD COLUMN pregnancy_loss_count INT NOT NULL DEFAULT 0 COMMENT '妊娠损失母猪头数（同一次配种首次损失，同猪同日去重）';
