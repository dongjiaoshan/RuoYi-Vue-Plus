SET NAMES utf8mb4;
-- SYS-FIX-004：人员表"岗位"由自由文本改为关联 sys_post 下拉。
-- 新增 post_id 列（关联 sys_post.post_id），并删除原 position 自由文本列（D02 testing-human #4 反馈：建模错位）。
ALTER TABLE t_md_person
    ADD COLUMN post_id BIGINT NULL COMMENT '岗位 ID（关联 sys_post.post_id）' AFTER id_card;

CREATE INDEX idx_post_id ON t_md_person(post_id);

ALTER TABLE t_md_person DROP COLUMN position;
