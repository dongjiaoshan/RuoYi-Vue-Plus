-- 采购入库菜单归位：移到「库存管理(9302)」下并显示。
-- 原状态挂在「产品生产管理(9301)」且 visible='1'(隐藏)，导致库存管理里找不到采购入库入口（邓博反馈）。
-- 页面 component djs-warehouse/purchaseIn/index 已重做为「商品维度」采购入库，权限 9060-9063 已授全角色，无需改授权。
UPDATE sys_menu
   SET parent_id = 9302,
       visible   = '0',
       order_num = 0
 WHERE menu_id = 9060;
