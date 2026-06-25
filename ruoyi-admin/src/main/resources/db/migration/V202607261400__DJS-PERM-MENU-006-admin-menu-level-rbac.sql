-- DJS-PERM-MENU-006：admin 业务模块权限树收成菜单级 RBAC（到二级菜单/C 页面级，删按钮级 F）。
-- 机制：C 菜单 perms 改通配（Sa-Token vagueMatch + 前端 permMatch 均支持），删被覆盖的 F 按钮；
--   多资源页的跨资源 F、以及无覆盖通配的工具 F（农场切换/OSS 凭证）保留颗粒度。零 403、零按钮失效。
-- 覆盖范围：通用主数据5000/养殖7000/种植8000/仓库9000/门店10000；ruoyi 系统模块(1/2/3)不动。

-- 1) C 菜单 perms → 通配
UPDATE sys_menu SET perms='djs:common:store:*' WHERE menu_id=5002;  -- 门店管理
UPDATE sys_menu SET perms='djs:common:supplier:*' WHERE menu_id=5003;  -- 供应商管理
UPDATE sys_menu SET perms='djs:common:image:*' WHERE menu_id=5500;  -- 公共图库
UPDATE sys_menu SET perms='djs:common:defaultImage:*' WHERE menu_id=5520;  -- 分类默认图
UPDATE sys_menu SET perms='djs:breed:breeding:*' WHERE menu_id=7016;  -- 品种信息管理
UPDATE sys_menu SET perms='djs:breed:breeding:*' WHERE menu_id=7017;  -- 品系信息管理
UPDATE sys_menu SET perms='djs:breed:breeding:*' WHERE menu_id=7018;  -- 品种配种信息管理
UPDATE sys_menu SET perms='djs:breed:breeding:*' WHERE menu_id=7019;  -- 品系配种信息管理
UPDATE sys_menu SET perms='djs:breed:farm-info:*' WHERE menu_id=7020;  -- 农场栋舍管理
UPDATE sys_menu SET perms='djs:breed:barn:*' WHERE menu_id=7023;  -- 栏位详情
UPDATE sys_menu SET perms='djs:breed:production-cycle:*' WHERE menu_id=7050;  -- 生产配置管理
UPDATE sys_menu SET perms='djs:breed:med:*' WHERE menu_id=7060;  -- 药品库
UPDATE sys_menu SET perms='djs:breed:med-batch:*' WHERE menu_id=7070;  -- 药品批次
UPDATE sys_menu SET perms='djs:breed:med-usage:*' WHERE menu_id=7076;  -- 药品领用
UPDATE sys_menu SET perms='djs:breed:med-record:*' WHERE menu_id=7085;  -- 用药治疗
UPDATE sys_menu SET perms='djs:breed:pig:*' WHERE menu_id=7145;  -- 事件台账
UPDATE sys_menu SET perms='djs:breed:pig:*' WHERE menu_id=7200;  -- 猪只主表
UPDATE sys_menu SET perms='djs:breed:pig:*' WHERE menu_id=7205;  -- 猪只详情页
UPDATE sys_menu SET perms='djs:breed:dashboard:*' WHERE menu_id=7400;  -- 养殖看板
UPDATE sys_menu SET perms='djs:plant:plot:*' WHERE menu_id=8010;  -- 地块管理
UPDATE sys_menu SET perms='djs:plant:plot:*' WHERE menu_id=8018;  -- 片区管理
UPDATE sys_menu SET perms='djs:plant:crop:*' WHERE menu_id=8020;  -- 作物管理
UPDATE sys_menu SET perms='djs:plant:team:*' WHERE menu_id=8030;  -- 班组管理
UPDATE sys_menu SET perms='djs:plant:plotOrganic:*' WHERE menu_id=8050;  -- 土地有机认证
UPDATE sys_menu SET perms='djs:plant:cropOrganic:*' WHERE menu_id=8060;  -- 果蔬有机认证
UPDATE sys_menu SET perms='djs:plant:plan:*' WHERE menu_id=8070;  -- 种植计划
UPDATE sys_menu SET perms='djs:plant:plan:*' WHERE menu_id=8075;  -- 种植计划创建
UPDATE sys_menu SET perms='djs:plant:plan:*' WHERE menu_id=8078;  -- 计划详情
UPDATE sys_menu SET perms='djs:plant:pick:*' WHERE menu_id=8082;  -- 采摘计划
UPDATE sys_menu SET perms='djs:plant:pick:activity:*' WHERE menu_id=8084;  -- 采摘活动
UPDATE sys_menu SET perms='djs:plant:farm:*' WHERE menu_id=8098;  -- 农事记录
UPDATE sys_menu SET perms='djs:plant:farm:disaster:*' WHERE menu_id=8100;  -- 灾害记录
UPDATE sys_menu SET perms='djs:plant:dashboard:*' WHERE menu_id=8110;  -- 种植看板
UPDATE sys_menu SET perms='djs:plant:overview:*' WHERE menu_id=8112;  -- 种植总览
UPDATE sys_menu SET perms='djs:plantPerformance:*' WHERE menu_id=8200;  -- 绩效管理
UPDATE sys_menu SET perms='djs:warehouse:location:*' WHERE menu_id=9010;  -- 库位配置管理
UPDATE sys_menu SET perms='djs:warehouse:stock:*' WHERE menu_id=9020;  -- 库存查询
UPDATE sys_menu SET perms='djs:warehouse:location:*' WHERE menu_id=9026;  -- 库位总览
UPDATE sys_menu SET perms='djs:warehouse:outsourcePig:*' WHERE menu_id=9028;  -- 外购猪只
UPDATE sys_menu SET perms='djs:warehouse:product:*' WHERE menu_id=9030;  -- 商品主数据
UPDATE sys_menu SET perms='djs:warehouse:product:*' WHERE menu_id=9036;  -- 产品配置
UPDATE sys_menu SET perms='djs:warehouse:product:*' WHERE menu_id=9037;  -- 商品配置
UPDATE sys_menu SET perms='djs:warehouse:product:*' WHERE menu_id=9038;  -- 礼盒配置
UPDATE sys_menu SET perms='djs:warehouse:demand:*' WHERE menu_id=9040;  -- 需求管理
UPDATE sys_menu SET perms='djs:warehouse:demand:*' WHERE menu_id=9041;  -- 白条需求
UPDATE sys_menu SET perms='djs:warehouse:demand:*' WHERE menu_id=9042;  -- 蔬菜需求
UPDATE sys_menu SET perms='djs:warehouse:demand:*' WHERE menu_id=9043;  -- 礼盒需求
UPDATE sys_menu SET perms='djs:warehouse:demand:*' WHERE menu_id=9044;  -- 其他需求
UPDATE sys_menu SET perms='djs:warehouse:demand:*' WHERE menu_id=9055;  -- 需求确认
UPDATE sys_menu SET perms='djs:warehouse:purchaseIn:*' WHERE menu_id=9060;  -- 采购入库
UPDATE sys_menu SET perms='djs:warehouse:pigBurn:*' WHERE menu_id=9070;  -- 燎毛记录
UPDATE sys_menu SET perms='djs:warehouse:pigCut:*' WHERE menu_id=9076;  -- 分割记录
UPDATE sys_menu SET perms='djs:warehouse:vegHandle:*' WHERE menu_id=9090;  -- 毛菜处理
UPDATE sys_menu SET perms='djs:warehouse:mat:pack:*' WHERE menu_id=9100;  -- 包材领用
UPDATE sys_menu SET perms='djs:warehouse:stockFlow:*' WHERE menu_id=9110;  -- 出入库流水
UPDATE sys_menu SET perms='djs:warehouse:matPick:*' WHERE menu_id=9118;  -- 生产物资领用
UPDATE sys_menu SET perms='djs:warehouse:stockOverview:*' WHERE menu_id=9123;  -- 库存总览
UPDATE sys_menu SET perms='djs:warehouse:loss:overview:*' WHERE menu_id=9126;  -- 损耗总览
UPDATE sys_menu SET perms='djs:warehouse:return:*' WHERE menu_id=9210;  -- 退货记录
UPDATE sys_menu SET perms='djs:warehouse:shipment:*' WHERE menu_id=9217;  -- 发货流水
UPDATE sys_menu SET perms='djs:warehouse:production:*' WHERE menu_id=9230;  -- 产品生产
UPDATE sys_menu SET perms='djs:warehouse:packEntry:*' WHERE menu_id=9233;  -- 肉品打包管理
UPDATE sys_menu SET perms='djs:warehouse:packEntry:*' WHERE menu_id=9234;  -- 其他产品打包管理
UPDATE sys_menu SET perms='djs:warehouse:packEntry:*' WHERE menu_id=9236;  -- 果蔬打包管理
UPDATE sys_menu SET perms='djs:warehouse:packEntry:*' WHERE menu_id=9237;  -- 白条分割管理
UPDATE sys_menu SET perms='djs:warehouse:packEntry:*' WHERE menu_id=9238;  -- 分割白条领用
UPDATE sys_menu SET perms='djs:warehouse:flowIn:*' WHERE menu_id=9240;  -- 入库记录
UPDATE sys_menu SET perms='djs:warehouse:flowOut:*' WHERE menu_id=9241;  -- 出库记录
UPDATE sys_menu SET perms='djs:warehouse:packEntry:*' WHERE menu_id=9249;  -- 礼盒打包管理
UPDATE sys_menu SET perms='djs:warehouse:check:*' WHERE menu_id=9250;  -- 盘点记录
UPDATE sys_menu SET perms='djs:warehouse:dashboard:*' WHERE menu_id=9400;  -- 仓库总览
UPDATE sys_menu SET perms='djs:store:demand:*' WHERE menu_id=10002;  -- 需求下单
UPDATE sys_menu SET perms='djs:storeRelation:*' WHERE menu_id=10110;  -- 产品关联
UPDATE sys_menu SET perms='djs:storeSale:*' WHERE menu_id=10120;  -- 经营明细
UPDATE sys_menu SET perms='djs:store:check:*' WHERE menu_id=10200;  -- 门店盘点
UPDATE sys_menu SET perms='djs:warehouse:product:*' WHERE menu_id=10210;  -- 产品管理
UPDATE sys_menu SET perms='djs:store:split:*' WHERE menu_id=10300;  -- 门店分割
UPDATE sys_menu SET perms='djs:store:trace:*' WHERE menu_id=10310;  -- 门店猪肉打包
UPDATE sys_menu SET perms='djs:store:member:*' WHERE menu_id=10320;  -- 会员档案
UPDATE sys_menu SET perms='djs:store:return:*' WHERE menu_id=10350;  -- 退回记录
UPDATE sys_menu SET perms='djs:store:return:*' WHERE menu_id=10360;  -- 退回操作
UPDATE sys_menu SET perms='djs:store:dashboard:*' WHERE menu_id=10400;  -- 门店总览
UPDATE sys_menu SET perms='djs:warehouse:trace:*' WHERE menu_id=10501;  -- 果蔬追溯码管理
UPDATE sys_menu SET perms='djs:store:trace:*' WHERE menu_id=10520;  -- 猪肉追溯码管理

-- 2) 安全授权：持有被删 F 的角色补授其覆盖 C（避免边角角色丢按钮）
-- （无：所有持 F 角色都已持对应 C）

-- 3) 删除被通配覆盖的 F 按钮 + 其授权
DELETE FROM sys_role_menu WHERE menu_id IN (5020,5021,5022,5023,5024,5025,5026,5030,5031,5032,5033,5034,5501,5502,5503,5504,5505,5506,5521,5522,7011,7012,7013,7014,7015,7021,7022,7051,7052,7053,7054,7055,7061,7062,7063,7064,7065,7071,7072,7073,7074,7075,7077,7078,7079,7086,7087,7088,7089,7146,7201,7202,7204,7410,7411,7412,7413,7414,8014,8015,8016,8017,8021,8022,8023,8024,8031,8032,8033,8034,8051,8052,8053,8054,8055,8061,8062,8063,8064,8065,8071,8072,8073,8074,8076,8077,8079,8080,8083,8085,8099,8101,8102,8111,8114,8201,8202,8203,9011,9012,9013,9014,9015,9021,9022,9025,9027,9031,9032,9033,9034,9035,9045,9046,9047,9048,9049,9051,9052,9053,9054,9061,9062,9063,9071,9075,9077,9081,9091,9095,9101,9105,9111,9112,9114,9116,9117,9124,9125,9127,9128,9211,9212,9213,9214,9215,9216,9218,9219,9231,9235,9239,9242,9243,9244,9245,9251,9252,9253,9254,9255,9290,9291,9292,9293,10003,10004,10005,10006,10007,10008,10009,10010,10011,10111,10112,10121,10122,10123,10124,10201,10202,10203,10204,10205,10206,10211,10212,10301,10302,10303,10321,10322,10323,10324,10325,10326,10327,10351,10352,10353,10354,10355,10356,10502,10503,10504,10505);
DELETE FROM sys_menu      WHERE menu_id IN (5020,5021,5022,5023,5024,5025,5026,5030,5031,5032,5033,5034,5501,5502,5503,5504,5505,5506,5521,5522,7011,7012,7013,7014,7015,7021,7022,7051,7052,7053,7054,7055,7061,7062,7063,7064,7065,7071,7072,7073,7074,7075,7077,7078,7079,7086,7087,7088,7089,7146,7201,7202,7204,7410,7411,7412,7413,7414,8014,8015,8016,8017,8021,8022,8023,8024,8031,8032,8033,8034,8051,8052,8053,8054,8055,8061,8062,8063,8064,8065,8071,8072,8073,8074,8076,8077,8079,8080,8083,8085,8099,8101,8102,8111,8114,8201,8202,8203,9011,9012,9013,9014,9015,9021,9022,9025,9027,9031,9032,9033,9034,9035,9045,9046,9047,9048,9049,9051,9052,9053,9054,9061,9062,9063,9071,9075,9077,9081,9091,9095,9101,9105,9111,9112,9114,9116,9117,9124,9125,9127,9128,9211,9212,9213,9214,9215,9216,9218,9219,9231,9235,9239,9242,9243,9244,9245,9251,9252,9253,9254,9255,9290,9291,9292,9293,10003,10004,10005,10006,10007,10008,10009,10010,10011,10111,10112,10121,10122,10123,10124,10201,10202,10203,10204,10205,10206,10211,10212,10301,10302,10303,10321,10322,10323,10324,10325,10326,10327,10351,10352,10353,10354,10355,10356,10502,10503,10504,10505);

