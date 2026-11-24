-- V7 第52行（甲方2026-11-07）：隐藏临时小屏测试入口；正式菜单、路由与授权保持原样。
UPDATE sys_menu
SET visible = '1', update_time = NOW()
WHERE menu_id = 9299
  AND component = 'djs-warehouse/production/packEntry/meatMini/index';
