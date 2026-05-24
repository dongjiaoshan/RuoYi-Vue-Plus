SET NAMES utf8mb4;
-- Removes post_id (sys_post foreign key) from t_md_person.
-- post_id was removed from the Person domain; column should not exist in fresh installs.
-- For environments where it was added by an earlier migration, drop it here.
SET @sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_md_person'
          AND COLUMN_NAME = 'post_id'
    ),
    'ALTER TABLE t_md_person DROP COLUMN post_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
