INSERT IGNORE INTO courses (id, title, description, price, level, status, created_at, updated_at)
VALUES
    (1, 'Spring Boot Fundamentals', 'Build REST APIs with Spring Boot and layered architecture.', 499000.00, 'BEGINNER', 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'Spring Data JPA Essentials', 'Practice entities, repositories, relationships, and JPQL queries.', 599000.00, 'INTERMEDIATE', 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'PostgreSQL for Beginners', 'Learn tables, keys, constraints, and common SQL queries.', 399000.00, 'BEGINNER', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE id = id;
