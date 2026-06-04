-- ============================================================
-- 东角山 测试演示数据 seed（养殖闭环 + 仓库）
-- ============================================================
-- 目的：给测试环境一次性铺齐"前置数据"，测试人员可直接测任意阶段，
--       不必先手工创建上一阶段数据（避免把"缺前置数据"误报成 bug）。
--
-- 范围（本批）：养殖闭环（各状态猪 + 事件）+ 仓库（库存/需求/生产/流水）
--             + 支撑仓库需求的最小 demo 门店。种植/门店/追溯后续再补。
--
-- 设计：
--   1. 自包含 —— 自带农场底座（猪舍/栏/库位/产品/门店）+ 业务数据，不依赖环境现有数据。
--   2. 只引用字典 value（djs_pig_lifecycle / djs_belong_type 等，Flyway 保证存在），不引用动态主数据 ID。
--   3. 全部用预留 ID 段（1e17 base，9 2xxxx~9 3xxxx 子段），与雪花 ID（~2e18）+ 既有种子（...0001~9099）不冲突。
--   4. 幂等：开头按 ID 段 DELETE 旧 demo 行再 INSERT，可反复跑、可整体删除（DELETE 各段即可清空）。
--   5. 非 Flyway（不进 migration，避免污染 prod）。手动跑：
--        docker exec -i <mysql> mysql -uroot -p<pwd> <db> < seed-demo-data.sql
--      或 DB 客户端直接执行。
--
-- ID 段约定（base = 100000000000000000）：
--   910001-910099  demo 门店          | 930001-930099  仓库库位
--   920001-920010  猪舍               | 931001-931999  仓库产品
--   920011-920099  栏位               | 932001-932999  库存
--   921001-921999  猪只               | 933001-933999  需求
--   922001-922999  养殖事件(配种/分娩/查情/断奶/阉割) | 934001-934999 生产记录
--                                      | 935001-935999  出入库流水
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 0. 幂等：清旧 demo 行（按预留 ID 段，只删 demo，不碰真实数据）
-- ------------------------------------------------------------
DELETE FROM t_md_store                    WHERE id BETWEEN 100000000000910001 AND 100000000000910099;
DELETE FROM t_farm_barn_info              WHERE id BETWEEN 100000000000920001 AND 100000000000920010;
DELETE FROM t_farm_barn_pen               WHERE id BETWEEN 100000000000920011 AND 100000000000920099;
DELETE FROM t_farm_pig_info               WHERE id BETWEEN 100000000000921001 AND 100000000000921999;
DELETE FROM t_farm_pig_breeding           WHERE id BETWEEN 100000000000922001 AND 100000000000922099;
DELETE FROM t_farm_pig_farrow             WHERE id BETWEEN 100000000000922101 AND 100000000000922199;
DELETE FROM t_farm_pig_heat               WHERE id BETWEEN 100000000000922201 AND 100000000000922299;
DELETE FROM t_farm_pig_weaning            WHERE id BETWEEN 100000000000922301 AND 100000000000922399;
DELETE FROM t_farm_castrate_record        WHERE id BETWEEN 100000000000922401 AND 100000000000922499;
DELETE FROM t_warehouse_location_info     WHERE id BETWEEN 100000000000930001 AND 100000000000930099;
DELETE FROM t_warehouse_product_info      WHERE id BETWEEN 100000000000931001 AND 100000000000931999;
DELETE FROM t_warehouse_location_stock    WHERE id BETWEEN 100000000000932001 AND 100000000000932999;
DELETE FROM t_warehouse_demand_manage     WHERE id BETWEEN 100000000000933001 AND 100000000000933999;
DELETE FROM t_warehouse_product_production WHERE id BETWEEN 100000000000934001 AND 100000000000934999;
DELETE FROM t_warehouse_stock_flow        WHERE id BETWEEN 100000000000935001 AND 100000000000935999;

-- ============================================================
-- A. demo 门店（支撑仓库需求/发货的 store_id）
-- ============================================================
INSERT INTO t_md_store
  (id,tenant_id,store_code,store_name,store_type,business_status,address,create_by,create_time,update_by,update_time,del_flag,del_unique,create_dept) VALUES
(100000000000910001,'1001','DEMO-ST01','演示·徐汇旗舰店','direct',0,'上海市徐汇区演示路1号',1,NOW(),1,NOW(),'0',0,203),
(100000000000910002,'1001','DEMO-ST02','演示·浦东加盟店','franchise',0,'上海市浦东新区演示路2号',1,NOW(),1,NOW(),'0',0,203);

-- ============================================================
-- B. 养殖底座：猪舍 + 栏位
-- ============================================================
INSERT INTO t_farm_barn_info
  (id,tenant_id,barn_code,barn_name,barn_type,capacity,current_count,barn_status,create_by,create_time,update_by,update_time,del_flag,del_unique,create_dept) VALUES
(100000000000920001,'1001','DEMO-B1','演示猪舍','breeding',120,15,1,1,NOW(),1,NOW(),'0',0,200);

INSERT INTO t_farm_barn_pen
  (id,tenant_id,barn_id,pen_code,pen_name,pen_type,capacity,current_count,pen_status,create_by,create_time,update_by,update_time,del_flag,del_unique,create_dept) VALUES
(100000000000920011,'1001',100000000000920001,'DEMO-P1','演示母栏','female',60,11,1,1,NOW(),1,NOW(),'0',0,200),
(100000000000920012,'1001',100000000000920001,'DEMO-P2','演示公栏','male',20,2,1,1,NOW(),1,NOW(),'0',0,200),
(100000000000920013,'1001',100000000000920001,'DEMO-P3','演示育肥栏','group',40,2,1,1,NOW(),1,NOW(),'0',0,200);

-- ============================================================
-- C. 各状态猪（覆盖配种/分娩/查情/断奶/阉割/出栏 全部 录入测试 前置）
--    母猪：HB 后备 / PZ 配种待分娩 / DN 断奶 / KH 空怀 / FQ 返情 / LC 流产 / FM 分娩
--    公猪：BOAR_ACTIVE（配种选公猪 + 阉割）；育肥猪：HB（阉割/出栏/生长/转栏）
-- ============================================================
INSERT INTO t_farm_pig_info
  (id,tenant_id,ear_tag,ear_no,lifecycle_id,recyclable,pig_sex,pig_type,pig_breed_code,pig_strain_code,
   current_status,status_started_at,birth_date,introduce_date,introduce_type,parity,mating_count,
   barn_id,pen_id,is_appointed,create_by,create_time,update_by,update_time,del_flag,version,del_unique,create_dept) VALUES
(100000000000921001,'1001','DEMO0001','DEMO0001',1,0,'F','sow','01','1','HB',DATE_SUB(NOW(),INTERVAL 20 DAY),DATE_SUB(CURDATE(),INTERVAL 300 DAY),DATE_SUB(CURDATE(),INTERVAL 90 DAY),'internal',0,0,100000000000920001,100000000000920011,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921002,'1001','DEMO0002','DEMO0002',1,0,'F','sow','01','1','HB',DATE_SUB(NOW(),INTERVAL 18 DAY),DATE_SUB(CURDATE(),INTERVAL 290 DAY),DATE_SUB(CURDATE(),INTERVAL 88 DAY),'internal',0,0,100000000000920001,100000000000920011,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921003,'1001','DEMO0003','DEMO0003',1,0,'F','sow','02','2','PZ',DATE_SUB(NOW(),INTERVAL 12 DAY),DATE_SUB(CURDATE(),INTERVAL 420 DAY),DATE_SUB(CURDATE(),INTERVAL 200 DAY),'internal',2,3,100000000000920001,100000000000920011,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921004,'1001','DEMO0004','DEMO0004',1,0,'F','sow','01','1','PZ',DATE_SUB(NOW(),INTERVAL 10 DAY),DATE_SUB(CURDATE(),INTERVAL 400 DAY),DATE_SUB(CURDATE(),INTERVAL 190 DAY),'internal',1,2,100000000000920001,100000000000920011,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921005,'1001','DEMO0005','DEMO0005',1,0,'F','sow','01','1','DN',DATE_SUB(NOW(),INTERVAL 5 DAY),DATE_SUB(CURDATE(),INTERVAL 450 DAY),DATE_SUB(CURDATE(),INTERVAL 230 DAY),'internal',2,2,100000000000920001,100000000000920011,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921006,'1001','DEMO0006','DEMO0006',1,0,'F','sow','03','3','DN',DATE_SUB(NOW(),INTERVAL 4 DAY),DATE_SUB(CURDATE(),INTERVAL 440 DAY),DATE_SUB(CURDATE(),INTERVAL 220 DAY),'internal',1,1,100000000000920001,100000000000920011,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921007,'1001','DEMO0007','DEMO0007',1,0,'F','sow','01','1','KH',DATE_SUB(NOW(),INTERVAL 8 DAY),DATE_SUB(CURDATE(),INTERVAL 500 DAY),DATE_SUB(CURDATE(),INTERVAL 260 DAY),'internal',3,3,100000000000920001,100000000000920011,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921008,'1001','DEMO0008','DEMO0008',1,0,'F','sow','02','2','FQ',DATE_SUB(NOW(),INTERVAL 6 DAY),DATE_SUB(CURDATE(),INTERVAL 410 DAY),DATE_SUB(CURDATE(),INTERVAL 195 DAY),'internal',1,2,100000000000920001,100000000000920011,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921009,'1001','DEMO0009','DEMO0009',1,0,'F','sow','01','1','LC',DATE_SUB(NOW(),INTERVAL 7 DAY),DATE_SUB(CURDATE(),INTERVAL 430 DAY),DATE_SUB(CURDATE(),INTERVAL 210 DAY),'internal',2,2,100000000000920001,100000000000920011,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921010,'1001','DEMO0010','DEMO0010',1,0,'F','sow','01','1','FM',DATE_SUB(NOW(),INTERVAL 2 DAY),DATE_SUB(CURDATE(),INTERVAL 460 DAY),DATE_SUB(CURDATE(),INTERVAL 240 DAY),'internal',1,1,100000000000920001,100000000000920011,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921011,'1001','DEMO0011','DEMO0011',1,0,'F','sow','04','4','FM',DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_SUB(CURDATE(),INTERVAL 470 DAY),DATE_SUB(CURDATE(),INTERVAL 250 DAY),'internal',2,1,100000000000920001,100000000000920011,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921012,'1001','DEMO0012','DEMO0012',1,0,'M','boar','04','4','BOAR_ACTIVE',DATE_SUB(NOW(),INTERVAL 30 DAY),DATE_SUB(CURDATE(),INTERVAL 600 DAY),DATE_SUB(CURDATE(),INTERVAL 300 DAY),'internal',0,0,100000000000920001,100000000000920012,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921013,'1001','DEMO0013','DEMO0013',1,0,'M','boar','01','1','BOAR_ACTIVE',DATE_SUB(NOW(),INTERVAL 35 DAY),DATE_SUB(CURDATE(),INTERVAL 620 DAY),DATE_SUB(CURDATE(),INTERVAL 310 DAY),'internal',0,0,100000000000920001,100000000000920012,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921014,'1001','DEMO0014','DEMO0014',1,0,'M','fattening','01','1','HB',DATE_SUB(NOW(),INTERVAL 40 DAY),DATE_SUB(CURDATE(),INTERVAL 150 DAY),DATE_SUB(CURDATE(),INTERVAL 60 DAY),'internal',0,0,100000000000920001,100000000000920013,0,1,NOW(),1,NOW(),'0',0,0,200),
(100000000000921015,'1001','DEMO0015','DEMO0015',1,0,'F','fattening','01','1','HB',DATE_SUB(NOW(),INTERVAL 42 DAY),DATE_SUB(CURDATE(),INTERVAL 155 DAY),DATE_SUB(CURDATE(),INTERVAL 62 DAY),'internal',0,0,100000000000920001,100000000000920013,0,1,NOW(),1,NOW(),'0',0,0,200);

-- ============================================================
-- D. 养殖事件（让"记录"tab 有回看数据）
-- ============================================================
-- D1. 配种记录（PZ 母猪已配种）
INSERT INTO t_farm_pig_breeding
  (id,tenant_id,pig_id,ear_no,breeding_date,breeding_type,boar_ear_no,parity,operator_id,barn_name,pen_name,create_by,create_time,update_by,update_time,del_flag,create_dept,del_unique) VALUES
(100000000000922001,'1001',100000000000921003,'DEMO0003',DATE_SUB(NOW(),INTERVAL 12 DAY),'1','DEMO0012',2,1,'演示猪舍','演示母栏',1,NOW(),1,NOW(),'0',200,0),
(100000000000922002,'1001',100000000000921004,'DEMO0004',DATE_SUB(NOW(),INTERVAL 10 DAY),'AI',NULL,1,1,'演示猪舍','演示母栏',1,NOW(),1,NOW(),'0',200,0);

-- D2. 分娩记录（FM 母猪已分娩）
INSERT INTO t_farm_pig_farrow
  (id,tenant_id,pig_id,ear_no,breeding_id,farrow_date,total_born,live_born,dead_born,mummy_born,weak_born,
   healthy_male,healthy_female,weak_raised_male,weak_raised_female,weak_culled,deformed_born,male_count,female_count,
   total_weight,avg_weight,parity,operator_id,create_by,create_time,update_by,update_time,del_flag,create_dept,del_unique) VALUES
(100000000000922101,'1001',100000000000921010,'DEMO0010',0,DATE_SUB(NOW(),INTERVAL 2 DAY),12,11,1,0,0,5,6,0,0,0,0,5,6,16.50,1.50,1,1,1,NOW(),1,NOW(),'0',200,0),
(100000000000922102,'1001',100000000000921011,'DEMO0011',0,DATE_SUB(NOW(),INTERVAL 1 DAY),10,10,0,0,0,5,5,0,0,0,0,5,5,15.00,1.50,2,1,1,NOW(),1,NOW(),'0',200,0);

-- D3. 查情记录（DN 母猪查情）
INSERT INTO t_farm_pig_heat
  (id,tenant_id,pig_id,ear_no,heat_date,heat_result,is_pregnant_confirmed,operator_id,create_by,create_time,update_by,update_time,del_flag,remark,create_dept,del_unique) VALUES
(100000000000922201,'1001',100000000000921005,'DEMO0005',DATE_SUB(NOW(),INTERVAL 3 DAY),'positive',0,1,1,NOW(),1,NOW(),'0','演示查情记录',200,0),
(100000000000922202,'1001',100000000000921006,'DEMO0006',DATE_SUB(NOW(),INTERVAL 2 DAY),'positive',0,1,1,NOW(),1,NOW(),'0','演示查情记录',200,0);

-- D4. 断奶记录
INSERT INTO t_farm_pig_weaning
  (id,tenant_id,pig_id,ear_no,farrow_id,breeding_id,weaning_date,weaned_count,weaned_weight,avg_weaned_weight,operator_id,create_by,create_time,update_by,update_time,del_flag,create_dept,del_unique) VALUES
(100000000000922301,'1001',100000000000921005,'DEMO0005',0,0,DATE_SUB(NOW(),INTERVAL 5 DAY),10,62.00,6.200,1,1,NOW(),1,NOW(),'0',200,0);

-- D5. 阉割记录（育肥公猪）
INSERT INTO t_farm_castrate_record
  (id,tenant_id,pig_id,ear_no,castrate_date,castrater,create_by,create_time,update_by,update_time,del_flag,remark,create_dept) VALUES
(100000000000922401,'1001',100000000000921014,'DEMO0014',DATE_SUB(NOW(),INTERVAL 20 DAY),NULL,1,NOW(),1,NOW(),'0','演示阉割记录',200);

-- ============================================================
-- E. 仓库底座：库位 + 产品
-- ============================================================
INSERT INTO t_warehouse_location_info
  (id,tenant_id,location_code,location_name,location_type,location_status,capacity,create_dept,create_by,create_time,update_by,update_time,del_flag,del_unique) VALUES
(100000000000930001,'1001','DEMO-L1','演示冷冻库','frozen',1,500.00,202,1,NOW(),1,NOW(),'0',0),
(100000000000930002,'1001','DEMO-L2','演示蔬菜鲜品库','veg_fresh',1,1000.00,202,1,NOW(),1,NOW(),'0',0),
(100000000000930003,'1001','DEMO-L3','演示包材库','packaging',1,2000.00,202,1,NOW(),1,NOW(),'0',0);

INSERT INTO t_warehouse_product_info
  (id,tenant_id,product_id,product_name,product_type,product_unit,belong_type,product_attr,is_delivery,product_status,create_dept,create_by,create_time,update_by,update_time,del_flag,del_unique) VALUES
(100000000000931001,'1001','DEMO-WB-01','演示·白条整只','1','kg','white_bar',1,0,0,202,1,NOW(),1,NOW(),'0',0),
(100000000000931002,'1001','DEMO-PK-01','演示·猪肉精瘦肉','1','kg','pork',1,1,0,202,1,NOW(),1,NOW(),'0',0),
(100000000000931003,'1001','DEMO-FD-01','演示·牧草饲料','2','kg','feed',2,0,0,202,1,NOW(),1,NOW(),'0',0),
(100000000000931004,'1001','DEMO-VG-01','演示·上海青','1','kg','vegetable',1,1,0,202,1,NOW(),1,NOW(),'0',0),
(100000000000931005,'1001','DEMO-PG-01','演示·包装纸箱','2','个','package',2,0,0,202,1,NOW(),1,NOW(),'0',0),
(100000000000931006,'1001','DEMO-DG-01','演示·腊肉','1','kg','dry_good',1,1,0,202,1,NOW(),1,NOW(),'0',0);

-- ============================================================
-- F. 库存（库存查询/盘点有数据；含 1 条按耳号的白条库存）
-- ============================================================
INSERT INTO t_warehouse_location_stock
  (id,tenant_id,location_id,product_id,ear_no,plot_id,product_name,product_stock,product_unit,is_end,operator_id,create_dept,create_by,create_time,update_by,update_time,del_flag,del_unique) VALUES
(100000000000932001,'1001',100000000000930001,100000000000931001,NULL,NULL,'演示·白条整只',320.000,'kg',0,1,202,1,NOW(),1,NOW(),'0',0),
(100000000000932002,'1001',100000000000930001,100000000000931002,NULL,NULL,'演示·猪肉精瘦肉',85.500,'kg',0,1,202,1,NOW(),1,NOW(),'0',0),
(100000000000932003,'1001',100000000000930002,100000000000931003,NULL,NULL,'演示·牧草饲料',5000.000,'kg',0,1,202,1,NOW(),1,NOW(),'0',0),
(100000000000932004,'1001',100000000000930002,100000000000931004,NULL,NULL,'演示·上海青',120.000,'kg',0,1,202,1,NOW(),1,NOW(),'0',0),
(100000000000932005,'1001',100000000000930003,100000000000931005,NULL,NULL,'演示·包装纸箱',2000.000,'个',0,1,202,1,NOW(),1,NOW(),'0',0),
(100000000000932006,'1001',100000000000930001,100000000000931006,NULL,NULL,'演示·腊肉',60.000,'kg',0,1,202,1,NOW(),1,NOW(),'0',0),
(100000000000932007,'1001',100000000000930001,NULL,'DEMO-WBX-01',NULL,'白条·DEMO-WBX-01',78.500,'kg',0,1,202,1,NOW(),1,NOW(),'0',0);

-- ============================================================
-- G. 需求（需求调度/管理有数据；覆盖 草稿/已提交/已确认/生产中 状态）
-- ============================================================
INSERT INTO t_warehouse_demand_manage
  (id,tenant_id,demand_no,demand_date,store_id,product_id,product_name,product_type,demand_quantity,product_unit,
   shipped_count,confirmed_count,demand_status,create_by,create_time,update_by,update_time,del_flag,version,del_unique,create_dept) VALUES
(100000000000933001,'1001','DEMO-D-WB01',DATE_ADD(CURDATE(),INTERVAL 2 DAY),100000000000910001,100000000000931001,'演示·白条整只','white_bar',50.000,'kg',0.000,0.000,'DRAFT',1,NOW(),1,NOW(),'0',0,0,202),
(100000000000933002,'1001','DEMO-D-WB02',DATE_ADD(CURDATE(),INTERVAL 3 DAY),100000000000910001,100000000000931001,'演示·白条整只','white_bar',80.000,'kg',0.000,0.000,'SUBMITTED',1,NOW(),1,NOW(),'0',0,0,202),
(100000000000933003,'1001','DEMO-D-VG01',DATE_ADD(CURDATE(),INTERVAL 1 DAY),100000000000910002,100000000000931004,'演示·上海青','vegetable',30.000,'kg',0.000,0.000,'CONFIRMED',1,NOW(),1,NOW(),'0',0,0,202),
(100000000000933004,'1001','DEMO-D-WB03',CURDATE(),100000000000910002,100000000000931001,'演示·白条整只','white_bar',60.000,'kg',0.000,0.000,'IN_PRODUCTION',1,NOW(),1,NOW(),'0',0,0,202);

-- ============================================================
-- H. 生产记录（生产记录/发货有数据；白条 B 前缀 + 蔬菜，绑 demo 门店）
-- ============================================================
INSERT INTO t_warehouse_product_production
  (id,tenant_id,produce_date,produce_no,product_id,product_name,product_type,product_unit,product_weight,produce_quantity,
   store_id,demand_id,produce_time,is_delivery_check,is_arrival_confirm,pack_status,create_dept,create_by,create_time,update_by,update_time,del_flag,del_unique) VALUES
(100000000000934001,'1001',CURDATE(),'DEMO-B260001',100000000000931001,'演示·白条整只','1','kg',78.500,1.000,100000000000910002,100000000000933004,NOW(),0,0,'packed',202,1,NOW(),1,NOW(),'0',0),
(100000000000934002,'1001',CURDATE(),'DEMO-V260001',100000000000931004,'演示·上海青','1','kg',30.000,1.000,100000000000910002,100000000000933003,NOW(),0,0,'packed',202,1,NOW(),1,NOW(),'0',0);

-- ============================================================
-- I. 出入库流水（采购入库流水，与库存对应）
-- ============================================================
INSERT INTO t_warehouse_stock_flow
  (id,tenant_id,flow_no,flow_date,product_id,warehouse_id,inout_type,flow_type,change_num,change_quantity,operator_id,create_by,create_time,update_by,update_time,del_flag,remark,del_unique,create_dept) VALUES
(100000000000935001,'1001','DEMO-F260001',NOW(),100000000000931001,100000000000930001,'IN','purchase_in',320.00,320.00,1,1,NOW(),1,NOW(),'0','演示采购入库',0,202),
(100000000000935002,'1001','DEMO-F260002',NOW(),100000000000931002,100000000000930001,'IN','purchase_in',85.50,85.50,1,1,NOW(),1,NOW(),'0','演示采购入库',0,202),
(100000000000935003,'1001','DEMO-F260003',NOW(),100000000000931003,100000000000930002,'IN','purchase_in',5000.00,5000.00,1,1,NOW(),1,NOW(),'0','演示采购入库',0,202),
(100000000000935004,'1001','DEMO-F260004',NOW(),100000000000931004,100000000000930002,'IN','purchase_in',120.00,120.00,1,1,NOW(),1,NOW(),'0','演示采购入库',0,202),
(100000000000935005,'1001','DEMO-F260005',NOW(),100000000000931005,100000000000930003,'IN','purchase_in',2000.00,2000.00,1,1,NOW(),1,NOW(),'0','演示采购入库',0,202),
(100000000000935006,'1001','DEMO-F260006',NOW(),100000000000931006,100000000000930001,'IN','purchase_in',60.00,60.00,1,1,NOW(),1,NOW(),'0','演示采购入库',0,202);

-- ============================================================
-- 完成。验证：
--   SELECT current_status,COUNT(*) FROM t_farm_pig_info WHERE id BETWEEN 100000000000921001 AND 100000000000921999 GROUP BY current_status;
--   SELECT COUNT(*) FROM t_warehouse_location_stock WHERE id BETWEEN 100000000000932001 AND 100000000000932999;
-- 整体删除 demo 数据：重跑本文件开头的 DELETE 段即可。
-- ============================================================
