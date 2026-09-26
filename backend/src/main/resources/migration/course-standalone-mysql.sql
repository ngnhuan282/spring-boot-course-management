-- Run once on an existing MySQL database before starting this branch.
-- Existing category_id values and all course rows are retained.
SET @course_category_fk = NULL;
SELECT CONSTRAINT_NAME INTO @course_category_fk
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'courses'
  AND COLUMN_NAME = 'category_id'
  AND REFERENCED_TABLE_NAME = 'categories'
LIMIT 1;

SET @drop_course_category_fk = IF(
    @course_category_fk IS NULL,
    'SELECT ''Course category foreign key already absent''',
    CONCAT('ALTER TABLE courses DROP FOREIGN KEY `', @course_category_fk, '`')
);
PREPARE course_migration FROM @drop_course_category_fk;
EXECUTE course_migration;
DEALLOCATE PREPARE course_migration;

ALTER TABLE courses MODIFY COLUMN category_id BIGINT NULL;

-- Keep legacy enrollment rows while allowing Course deletion independently.
SET @enrollment_course_fk = NULL;
SELECT CONSTRAINT_NAME INTO @enrollment_course_fk
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'enrollments'
  AND COLUMN_NAME = 'course_id'
  AND REFERENCED_TABLE_NAME = 'courses'
LIMIT 1;

SET @drop_enrollment_course_fk = IF(
    @enrollment_course_fk IS NULL,
    'SELECT ''Enrollment course foreign key already absent''',
    CONCAT('ALTER TABLE enrollments DROP FOREIGN KEY `', @enrollment_course_fk, '`')
);
PREPARE enrollment_migration FROM @drop_enrollment_course_fk;
EXECUTE enrollment_migration;
DEALLOCATE PREPARE enrollment_migration;
