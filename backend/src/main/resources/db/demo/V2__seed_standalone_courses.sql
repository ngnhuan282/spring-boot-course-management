-- Flyway applies this local demo seed once, under the dev profile.
INSERT INTO courses (id, title, description, price, level, status, created_at, updated_at)
VALUES
    (1, 'Spring Boot Fundamentals', 'Build REST APIs with Spring Boot.', 499000.00, 'BEGINNER', 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'Spring Data JPA Essentials', 'Practice repositories and persistence.', 599000.00, 'INTERMEDIATE', 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'MySQL for Beginners', 'Practice relational database migrations.', 399000.00, 'BEGINNER', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
