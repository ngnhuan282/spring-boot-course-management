INSERT IGNORE INTO categories (id, name, description, created_at, updated_at)
VALUES
    (1, 'Backend', 'Courses about server-side development and APIs.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'Database', 'Courses about relational databases and data persistence.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE id = id;

INSERT IGNORE INTO courses (id, category_id, title, description, price, level, status, created_at, updated_at)
VALUES
    (1, 1, 'Spring Boot Fundamentals', 'Build REST APIs with Spring Boot and layered architecture.', 499000.00, 'BEGINNER', 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 1, 'Spring Data JPA Essentials', 'Practice entities, repositories, relationships, and JPQL queries.', 599000.00, 'INTERMEDIATE', 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 2, 'PostgreSQL for Beginners', 'Learn tables, keys, constraints, and common SQL queries.', 399000.00, 'BEGINNER', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE id = id;

INSERT IGNORE INTO students (id, full_name, email, phone, created_at, updated_at)
VALUES
    (1, 'Nguyen Van An', 'an.nguyen@example.com', '0901000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'Tran Thi Binh', 'binh.tran@example.com', '0901000002', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE id = id;

INSERT IGNORE INTO enrollments (id, student_id, course_id, enrolled_at, status, completed_at)
VALUES
    (1, 1, 1, CURRENT_TIMESTAMP, 'ACTIVE', NULL),
    (2, 1, 2, CURRENT_TIMESTAMP, 'ACTIVE', NULL),
    (3, 2, 1, CURRENT_TIMESTAMP, 'COMPLETED', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE id = id;
