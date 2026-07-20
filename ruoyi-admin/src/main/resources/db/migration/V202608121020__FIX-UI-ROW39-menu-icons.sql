-- UI问题 row39：部分菜单 icon 值不在 plus-ui svg 图标集里（如 shop/redo/goods/barcode…），
-- 侧边栏渲染成空白。统一改成 svg 集内的有效图标，保证「菜单前都有图标」的数据一致性。
-- （有效图标集见 plus-ui/src/assets/icons/svg/*.svg）

UPDATE sys_menu SET icon = 'shopping'      WHERE menu_id = 5002;   -- 门店管理  shop → shopping
UPDATE sys_menu SET icon = 'build'         WHERE menu_id = 7020;   -- 农场栋舍管理 building → build
UPDATE sys_menu SET icon = 'tree'          WHERE menu_id = 8020;   -- 作物管理  sprout → tree
UPDATE sys_menu SET icon = 'documentation' WHERE menu_id = 8100;   -- 灾害记录  warning → documentation
UPDATE sys_menu SET icon = 'shopping'      WHERE menu_id = 9028;   -- 外购猪只  pig → shopping
UPDATE sys_menu SET icon = 'category'      WHERE menu_id = 9037;   -- 商品配置  goods → category
UPDATE sys_menu SET icon = 'shopping'      WHERE menu_id = 9060;   -- 采购入库  shopping-bag-3 → shopping
UPDATE sys_menu SET icon = 'documentation' WHERE menu_id = 9210;   -- 退货记录  refresh → documentation
UPDATE sys_menu SET icon = 'tool'          WHERE menu_id = 9233;   -- 肉品打包管理 goods → tool
UPDATE sys_menu SET icon = 'component'     WHERE menu_id = 9249;   -- 礼盒打包管理 box → component
UPDATE sys_menu SET icon = 'code'          WHERE menu_id = 10501;  -- 果蔬追溯码管理 barcode → code
UPDATE sys_menu SET icon = 'code'          WHERE menu_id = 10520;  -- 猪肉追溯码管理 qrcode → code
UPDATE sys_menu SET icon = 'switch'        WHERE menu_id = 10601;  -- 退回管理  redo → switch
