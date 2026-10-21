-- Adopt the old integrated schema without deleting its tables, columns or rows.
-- Fresh standalone databases have neither category_id nor these foreign keys.
SET @course_category_fk = (
    SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses'
      AND COLUMN_NAME = 'category_id' AND REFERENCED_TABLE_NAME = 'categories'
    LIMIT 1
);
SET @course_category_ddl = IF(
    @course_category_fk IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE courses DROP FOREIGN KEY `', @course_category_fk, '`')
);
PREPARE standalone_migration FROM @course_category_ddl;
EXECUTE standalone_migration;
DEALLOCATE PREPARE standalone_migration;

SET @course_category_nullable_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses'
             AND COLUMN_NAME = 'category_id'),
    'ALTER TABLE courses MODIFY COLUMN category_id BIGINT NULL', 'SELECT 1'
);
PREPARE standalone_migration FROM @course_category_nullable_ddl;
EXECUTE standalone_migration;
DEALLOCATE PREPARE standalone_migration;

SET @enrollment_course_fk = (
    SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollments'
      AND COLUMN_NAME = 'course_id' AND REFERENCED_TABLE_NAME = 'courses'
    LIMIT 1
);
SET @enrollment_course_ddl = IF(
    @enrollment_course_fk IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE enrollments DROP FOREIGN KEY `', @enrollment_course_fk, '`')
);
PREPARE standalone_migration FROM @enrollment_course_ddl;
EXECUTE standalone_migration;
DEALLOCATE PREPARE standalone_migration;
