-- FIX-DROP-DEAD-PICK-ACTIVITY-001：删除死表 t_plant_pick_activity（审计 P3-4）
--
-- t_plant_pick_activity 为死表，dashboard SUM 引用已在代码侧移除。

DROP TABLE IF EXISTS t_plant_pick_activity;
