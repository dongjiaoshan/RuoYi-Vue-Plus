-- ============================================================
-- STR-USER-REL-001 (STORE-PERM-001)  门店-人员关联中间表
-- ============================================================
-- 多对多：一个门店绑多个系统人员（sys_user），一人可绑多店。
-- 驱动全局门店选择器的「我有权限门店」数据源（listStoreIdsByUser）。
-- 软删走 del_flag + del_unique（应用层在软删时 SET del_unique=id）。
-- UNIQUE 末列含 del_unique，保证「解绑后重绑同一人」不冲突。
-- ============================================================
SET NAMES utf8mb4;

DROP TABLE IF EXISTS t_store_user_relation;
CREATE TABLE t_store_user_relation (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id   VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户 ID',
    store_id    BIGINT       NOT NULL COMMENT '门店 ID（t_md_store.id）',
    user_id     BIGINT       NOT NULL COMMENT '人员 sys_user.user_id',
    create_dept BIGINT       NULL COMMENT '创建部门',
    create_by   BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    del_flag    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '软删标记（0 未删 / 1 已删）',
    remark      VARCHAR(500) NULL COMMENT '备注',
    del_unique  BIGINT       NOT NULL DEFAULT 0 COMMENT '软删唯一性辅助列（软删时应用层 SET=id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_user (tenant_id, store_id, user_id, del_unique),
    KEY idx_user (user_id),
    KEY idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店-人员关联（STR-USER-REL-001）';
