-- 门店新增「店长微信图片」字段（测试问题 row164）。
-- 语义：店长微信二维码图，单图；追溯页销售门店块把它当扫码加店长的入口图展示（row165）。
-- 与 image_oss_id 同型：只存 sys_oss.oss_id，不存 URL；NULL = 未上传，前端整块隐藏。
ALTER TABLE t_md_store
    ADD COLUMN manager_wechat_oss_id BIGINT NULL COMMENT '店长微信二维码图（引用 sys_oss.oss_id）' AFTER image_oss_id;
