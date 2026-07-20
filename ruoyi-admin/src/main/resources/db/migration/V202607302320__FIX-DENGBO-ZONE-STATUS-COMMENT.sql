-- zone_status 列注释统一为权威口径：1=启用 / 0=停用（与片区数据实态 + 全部代码 + admin toggle 一致）。
-- 历史 V202606050900 误把列注释写成「字典 sys_normal_disable：1=正常 / 2=停用」——值（2）与口径（sys_normal_disable
-- 是 0=正常）双错，是 0/1 语义混乱的源头。zone_status 不走 sys_normal_disable，此处纠正避免后人踩坑。非破坏性（仅改注释）。
ALTER TABLE t_plant_plot_zone
    MODIFY COLUMN zone_status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1=启用 0=停用（不走 sys_normal_disable）';
