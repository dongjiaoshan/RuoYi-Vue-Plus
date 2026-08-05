-- 有机饲喂「按日汇总 + 仓库确认框数」
-- admin 行199：有机饲喂记录由明细列表改按日汇总，列增「仓库确认框数 / 仓库确认人」。
-- 小程序 行268：mp 同页改日汇总卡片，第三行提供【框数录入】入口（框数 + 确认人员）。
--
-- 为什么单独建表而不是给 t_warehouse_feed_log 加列：
--   feed_log 是**明细**表（一天多条：毛菜间送的 + 仓库领用的，实测 8/1 两种来源并存），
--   而框数是**按日一条**的人工确认量。挂明细表上就要么随便挑一行存、要么每行冗余，
--   两种都会在「同日多来源」时对不上账。故按确认粒度单独建日维度表，与 feed_log 按 feed_date 左联。

CREATE TABLE IF NOT EXISTS t_warehouse_feed_daily_confirm (
    id              BIGINT          NOT NULL COMMENT '主键（雪花）',
    feed_date       DATE            NOT NULL COMMENT '饲喂日期（日维度，一天一条）',
    box_count       DECIMAL(10, 2)  NULL     COMMENT '仓库确认框数（可小数）',
    confirm_user_id BIGINT          NULL     COMMENT '确认人 user_id（默认录入人，可选养殖部 dept_id=200 人员）',
    confirm_time    DATETIME        NULL     COMMENT '确认时间',
    remark          VARCHAR(255)    NULL     COMMENT '备注',
    tenant_id       VARCHAR(20)     NOT NULL DEFAULT '1001' COMMENT '租户编号',
    create_by       BIGINT          NULL     COMMENT '创建者',
    create_time     DATETIME        NULL     COMMENT '创建时间',
    update_by       BIGINT          NULL     COMMENT '更新者',
    update_time     DATETIME        NULL     COMMENT '更新时间',
    create_dept     BIGINT          NULL     COMMENT '创建部门',
    del_flag        CHAR(1)         NOT NULL DEFAULT '0' COMMENT '删除标记（0 未删 / 1 已删）',
    del_unique      BIGINT          NOT NULL DEFAULT 0 COMMENT '软删占位（0=有效；删除时置为 id，让唯一键放行同日重录）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_feed_date (tenant_id, feed_date, del_unique)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '有机饲喂日确认（框数 + 确认人，一天一条）';
