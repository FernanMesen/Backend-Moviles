
-- =========================================================
-- 1. ROLES
-- =========================================================

CREATE TABLE roles (
                       id BIGSERIAL PRIMARY KEY,
                       name VARCHAR(50) NOT NULL UNIQUE,
                       description VARCHAR(255)
);


-- =========================================================
-- 2. PRIVILEGES
-- =========================================================

CREATE TABLE privileges (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(50) NOT NULL UNIQUE,
                            description VARCHAR(255)
);


-- =========================================================
-- 3. USERS
-- =========================================================

CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,

                       role_id BIGINT NOT NULL,
                       privilege_id BIGINT NOT NULL,

                       email VARCHAR(150) NOT NULL UNIQUE,
                       password_hash VARCHAR(255),
                       full_name VARCHAR(150) NOT NULL,

                       auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
                       provider_user_id VARCHAR(255),

                       status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

                       last_login_at TIMESTAMP,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT fk_users_role
                           FOREIGN KEY (role_id)
                               REFERENCES roles(id),

                       CONSTRAINT fk_users_privilege
                           FOREIGN KEY (privilege_id)
                               REFERENCES privileges(id),

                       CONSTRAINT chk_users_auth_provider
                           CHECK (auth_provider IN ('LOCAL', 'GOOGLE')),

                       CONSTRAINT chk_users_status
                           CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED'))
);


-- =========================================================
-- 4. COURSES
-- =========================================================

CREATE TABLE courses (
                         id BIGSERIAL PRIMARY KEY,

                         created_by_user_id BIGINT NOT NULL,

                         name VARCHAR(150) NOT NULL,
                         professor_name VARCHAR(150),
                         semester VARCHAR(100),

                         join_code VARCHAR(30) NOT NULL UNIQUE,
                         status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

                         syllabus TEXT,

                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         CONSTRAINT fk_courses_user
                             FOREIGN KEY (created_by_user_id)
                                 REFERENCES users(id),

                         CONSTRAINT chk_courses_status
                             CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);


-- =========================================================
-- 5. ENROLLMENTS
-- Resolves the N:M relationship between users and courses
-- =========================================================

CREATE TABLE enrollments (
                             id BIGSERIAL PRIMARY KEY,

                             user_id BIGINT NOT NULL,
                             course_id BIGINT NOT NULL,

                             status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                             enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_enrollments_user
                                 FOREIGN KEY (user_id)
                                     REFERENCES users(id),

                             CONSTRAINT fk_enrollments_course
                                 FOREIGN KEY (course_id)
                                     REFERENCES courses(id),

                             CONSTRAINT uq_user_course
                                 UNIQUE (user_id, course_id),

                             CONSTRAINT chk_enrollment_status
                                 CHECK (status IN ('ACTIVE', 'INACTIVE', 'COMPLETED'))
);


-- =========================================================
-- 6. NOTES
-- =========================================================

CREATE TABLE notes (
                       id BIGSERIAL PRIMARY KEY,

                       user_id BIGINT NOT NULL,
                       course_id BIGINT NOT NULL,

                       title VARCHAR(200) NOT NULL,
                       content TEXT,

                       material_type VARCHAR(30),
                       class_name VARCHAR(100),
                       file_url TEXT,

                       processing_status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',

                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT fk_notes_user
                           FOREIGN KEY (user_id)
                               REFERENCES users(id),

                       CONSTRAINT fk_notes_course
                           FOREIGN KEY (course_id)
                               REFERENCES courses(id),

                       CONSTRAINT chk_material_type
                           CHECK (
                               material_type IS NULL
                                   OR material_type IN (
                                                        'HANDWRITTEN',
                                                        'TEXTBOOK',
                                                        'SLIDES',
                                                        'WHITEBOARD'
                                   )
                               ),

                       CONSTRAINT chk_processing_status
                           CHECK (
                               processing_status IN (
                                                     'UPLOADED',
                                                     'PROCESSING',
                                                     'COMPLETED',
                                                     'FAILED'
                                   )
                               )
);


-- =========================================================
-- 7. ASSESSMENTS
-- JSON IS USED HERE
-- =========================================================

CREATE TABLE assessments (
                             id BIGSERIAL PRIMARY KEY,

                             course_id BIGINT NOT NULL,
                             created_by_user_id BIGINT NOT NULL,

                             title VARCHAR(200) NOT NULL,

                             type VARCHAR(30) NOT NULL,
                             difficulty VARCHAR(20),

                             content JSON NOT NULL,

                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_assessments_course
                                 FOREIGN KEY (course_id)
                                     REFERENCES courses(id),

                             CONSTRAINT fk_assessments_user
                                 FOREIGN KEY (created_by_user_id)
                                     REFERENCES users(id),

                             CONSTRAINT chk_assessment_type
                                 CHECK (
                                     type IN (
                                              'QUIZ',
                                              'EXAM',
                                              'PRACTICE',
                                              'FLASHCARDS'
                                         )
                                     ),

                             CONSTRAINT chk_assessment_difficulty
                                 CHECK (
                                     difficulty IS NULL
                                         OR difficulty IN (
                                                           'EASY',
                                                           'MEDIUM',
                                                           'HARD'
                                         )
                                     ),

                             CONSTRAINT chk_assessment_content
                                 CHECK (json_typeof(content) = 'object')
);


-- =========================================================
-- 8. ASSESSMENT ATTEMPTS
-- JSON IS ALSO USED HERE
-- =========================================================

CREATE TABLE assessment_attempts (
                                     id BIGSERIAL PRIMARY KEY,

                                     assessment_id BIGINT NOT NULL,
                                     user_id BIGINT NOT NULL,

                                     score DECIMAL(5,2),

                                     results JSON,

                                     started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     completed_at TIMESTAMP,

                                     CONSTRAINT fk_attempt_assessment
                                         FOREIGN KEY (assessment_id)
                                             REFERENCES assessments(id),

                                     CONSTRAINT fk_attempt_user
                                         FOREIGN KEY (user_id)
                                             REFERENCES users(id),

                                     CONSTRAINT chk_score
                                         CHECK (
                                             score IS NULL
                                                 OR (score >= 0 AND score <= 100)
                                             ),

                                     CONSTRAINT chk_attempt_results
                                         CHECK (
                                             results IS NULL
                                                 OR json_typeof(results) = 'object'
                                             )
);


-- =========================================================
-- 9. SECURITY LOGS
-- =========================================================

CREATE TABLE security_logs (
                               id BIGSERIAL PRIMARY KEY,

                               user_id BIGINT NOT NULL,
                               action VARCHAR(50) NOT NULL,

                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_security_logs_user
                                   FOREIGN KEY (user_id)
                                       REFERENCES users(id),

                               CONSTRAINT chk_security_action
                                   CHECK (
                                       action IN (
                                       'LOGIN',
                                       'LOGIN_FAILED',
                                       'PASSWORD_CHANGED',
                                       'PASSWORD_RESET',
                                       'ROLE_CHANGED',
                                       'PRIVILEGE_CHANGED',
                                       'ACCOUNT_BLOCKED',
                                       'ACCOUNT_DELETED'
                                       )
)
    );