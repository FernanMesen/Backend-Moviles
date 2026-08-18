-- ============================================================
--  ApuntelA Database Schema — PostgreSQL  (v3)
--  Generado desde el ERD: Base de datos en inglés
--  con cardinalidades principales
--  v2: agrega sección 5. NOTIFICATIONS & REMINDERS
--  v3: sección 4 (PRACTICE & REINFORCEMENT) rediseñada
--      para guardar quizzes generados por IA como JSONB
-- ============================================================

-- ============================================================
-- EXTENSIONS
-- ============================================================
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- búsqueda de texto


-- ============================================================
-- 1. ACCESS & SECURITY
-- ============================================================

CREATE TABLE users (
                       id          BIGSERIAL    PRIMARY KEY,
                       full_name   VARCHAR(255) NOT NULL,
                       email       VARCHAR(255) NOT NULL UNIQUE,
                       status      VARCHAR(50)  NOT NULL DEFAULT 'active',
                       created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
                       id          BIGSERIAL    PRIMARY KEY,
                       name        VARCHAR(100) NOT NULL UNIQUE,
                       description TEXT
);

CREATE TABLE permissions (
                             id          BIGSERIAL    PRIMARY KEY,
                             code        VARCHAR(100) NOT NULL UNIQUE,
                             description TEXT
);

-- Junction: users ↔ roles  (N:M)
CREATE TABLE user_roles (
                            user_id  BIGINT NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
                            role_id  BIGINT NOT NULL REFERENCES roles(id)  ON DELETE CASCADE,
                            PRIMARY KEY (user_id, role_id)
);

-- Junction: roles ↔ permissions  (N:M)
CREATE TABLE role_permissions (
                                  role_id       BIGINT NOT NULL REFERENCES roles(id)       ON DELETE CASCADE,
                                  permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
                                  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE auth_accounts (
                               id               BIGSERIAL    PRIMARY KEY,
                               user_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               provider         VARCHAR(50)  NOT NULL,
                               provider_user_id VARCHAR(255),
                               password_hash    VARCHAR(255)
);

CREATE TABLE password_reset_tokens (
                                       id         BIGSERIAL    PRIMARY KEY,
                                       user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                       token_hash VARCHAR(255) NOT NULL,
                                       expires_at TIMESTAMPTZ  NOT NULL,
                                       used_at    TIMESTAMPTZ
);

-- 0..1 : 1  (optional one-to-one)
CREATE TABLE user_preferences (
                                  user_id                  BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                                  preferred_language       VARCHAR(10)  NOT NULL DEFAULT 'es',
                                  study_goal               TEXT,
                                  preferred_learning_format VARCHAR(50),
                                  notifications_enabled    BOOLEAN      NOT NULL DEFAULT TRUE
);


-- ============================================================
-- 2. COURSES & SYLLABUS
-- ============================================================

CREATE TABLE courses (
                         id             BIGSERIAL    PRIMARY KEY,
                         created_by     BIGINT       NOT NULL REFERENCES users(id),
                         name           VARCHAR(255) NOT NULL,
                         course_code    VARCHAR(50),
                         professor_name VARCHAR(255),
                         semester       VARCHAR(50),
                         is_archived    BOOLEAN      NOT NULL DEFAULT FALSE,
                         created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE syllabi (
                         id          BIGSERIAL   PRIMARY KEY,
                         course_id   BIGINT      NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                         uploaded_by BIGINT      NOT NULL REFERENCES users(id),
                         source_type VARCHAR(50),
                         storage_url TEXT,
                         version     INT         NOT NULL DEFAULT 1,
                         is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
                         created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE syllabus_topics (
                                 id          BIGSERIAL    PRIMARY KEY,
                                 syllabus_id BIGINT       NOT NULL REFERENCES syllabi(id) ON DELETE CASCADE,
                                 name        VARCHAR(255) NOT NULL,
                                 position    INT          NOT NULL DEFAULT 0
);

-- Junction: courses ↔ users  (N:M membership)
CREATE TABLE course_memberships (
                                    course_id BIGINT      NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                                    user_id   BIGINT      NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
                                    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                    status    VARCHAR(50) NOT NULL DEFAULT 'active',
                                    PRIMARY KEY (course_id, user_id)
);


-- ============================================================
-- 3. NOTES & AI PROCESSING
-- ============================================================

CREATE TABLE tags (
                      id   BIGSERIAL    PRIMARY KEY,
                      name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE notes (
                       id                 BIGSERIAL    PRIMARY KEY,
                       course_id          BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                       author_id          BIGINT       NOT NULL REFERENCES users(id),
                       title              VARCHAR(255) NOT NULL,
                       recognized_text    TEXT,
                       structured_content JSONB,
                       class_name         VARCHAR(255),
                       material_type      VARCHAR(50),
                       visibility         VARCHAR(50)  NOT NULL DEFAULT 'private',
                       note_date          DATE,
                       created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE note_pages (
                            id           BIGSERIAL   PRIMARY KEY,
                            note_id      BIGINT      NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
                            page_number  INT         NOT NULL,
                            storage_url  TEXT,
                            original_text TEXT,
                            created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Junction: notes ↔ tags  (N:M)
CREATE TABLE note_tags (
                           note_id BIGINT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
                           tag_id  BIGINT NOT NULL REFERENCES tags(id)  ON DELETE CASCADE,
                           PRIMARY KEY (note_id, tag_id)
);

-- Junction: users ↔ notes favorites  (N:M)
CREATE TABLE note_favorites (
                                user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                note_id    BIGINT      NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                PRIMARY KEY (user_id, note_id)
);

-- Junction: users ↔ notes ratings  (N:M)
CREATE TABLE note_ratings (
                              user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                              note_id    BIGINT      NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
                              rating     SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
                              created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                              updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                              PRIMARY KEY (user_id, note_id)
);

CREATE TABLE recognition_reports (
                                     id          BIGSERIAL   PRIMARY KEY,
                                     note_id     BIGINT      NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
                                     reported_by BIGINT      NOT NULL REFERENCES users(id),
                                     description TEXT,
                                     status      VARCHAR(50) NOT NULL DEFAULT 'pending',
                                     created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Junction: notes ↔ syllabus_topics  (N:M with AI metadata)
CREATE TABLE note_topic_coverage (
                                     note_id             BIGINT         NOT NULL REFERENCES notes(id)           ON DELETE CASCADE,
                                     topic_id            BIGINT         NOT NULL REFERENCES syllabus_topics(id) ON DELETE CASCADE,
                                     coverage_percentage NUMERIC(5,2),
                                     coverage_status     VARCHAR(50),
                                     ai_confidence       NUMERIC(5,2),
                                     PRIMARY KEY (note_id, topic_id)
);

-- Junction: users ↔ syllabus_topics progress  (N:M)
CREATE TABLE user_topic_progress (
                                     user_id            BIGINT         NOT NULL REFERENCES users(id)           ON DELETE CASCADE,
                                     topic_id           BIGINT         NOT NULL REFERENCES syllabus_topics(id) ON DELETE CASCADE,
                                     mastery_percentage NUMERIC(5,2)   NOT NULL DEFAULT 0,
                                     mastery_status     VARCHAR(50)    NOT NULL DEFAULT 'not_started',
                                     last_practiced_at  TIMESTAMPTZ,
                                     PRIMARY KEY (user_id, topic_id)
);

CREATE TABLE note_adaptations (
                                  id           BIGSERIAL    PRIMARY KEY,
                                  note_id      BIGINT       NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
                                  user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                  format_type  VARCHAR(50),
                                  content_text TEXT,
                                  content_json JSONB,
                                  asset_url    TEXT,
                                  ai_model     VARCHAR(100),
                                  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);


-- ============================================================
-- 4. PRACTICE & REINFORCEMENT  (v3 — quizzes en JSON)
-- ============================================================

CREATE TABLE quizzes (
                         id                BIGSERIAL    PRIMARY KEY,
                         user_id           BIGINT       NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
                         course_id         BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                         note_id           BIGINT                REFERENCES notes(id)           ON DELETE SET NULL,   -- NULL allowed
                         topic_id          BIGINT                REFERENCES syllabus_topics(id) ON DELETE SET NULL,   -- NULL allowed
                         difficulty        VARCHAR(50),
                         mode              VARCHAR(50),
                         weak_topics_only  BOOLEAN      NOT NULL DEFAULT FALSE,
                         previous_mistakes_only BOOLEAN NOT NULL DEFAULT FALSE,
                         timer_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
                         ai_model          VARCHAR(100),
                         content           JSONB        NOT NULL,
                         score             NUMERIC(5,2),
                         started_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                         completed_at      TIMESTAMPTZ
);

CREATE TABLE quiz_answers (
                              id                 BIGSERIAL    PRIMARY KEY,
                              quiz_id            BIGINT       NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
                              user_id            BIGINT       NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
                              question_id        VARCHAR(50)  NOT NULL,
                              selected_option_id VARCHAR(50),   -- NULL allowed
                              text_answer        TEXT,
                              is_correct         BOOLEAN,
                              answered_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE quizzes IS 'Quizzes generados por IA; content guarda preguntas/opciones como JSONB';
COMMENT ON COLUMN quizzes.content IS 'Estructura: {"questions":[{"id","type","text","options":[{"id","text","correct"}],"explanation"}]}';
COMMENT ON TABLE quiz_answers IS 'Respuestas del usuario a un quiz; question_id/selected_option_id referencian ids dentro del JSON de quizzes.content';


-- ============================================================
-- 5. NOTIFICATIONS & REMINDERS
-- ============================================================

CREATE TABLE notifications (
                               id           BIGSERIAL    PRIMARY KEY,
                               user_id      BIGINT       NOT NULL REFERENCES users(id)           ON DELETE CASCADE,
    -- FKs opcionales (0..1): la notificación puede o no estar vinculada
                               course_id    BIGINT                REFERENCES courses(id)         ON DELETE SET NULL,
                               note_id      BIGINT                REFERENCES notes(id)           ON DELETE SET NULL,
                               topic_id     BIGINT                REFERENCES syllabus_topics(id) ON DELETE SET NULL,
    -- Contenido
                               type         VARCHAR(50)  NOT NULL,          -- e.g. 'reminder', 'alert', 'badge'
                               title        VARCHAR(255) NOT NULL,
                               message      TEXT,
                               is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Programación y envío
                               scheduled_at TIMESTAMPTZ,                    -- NULL = envío inmediato
                               sent_at      TIMESTAMPTZ,                    -- NULL = pendiente de envío
                               created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE notifications IS 'Notificaciones y recordatorios para el usuario, opcionalmente vinculadas a curso, apunte o tema';
COMMENT ON COLUMN notifications.type         IS 'Tipo de notificación: reminder, alert, badge, system, etc.';
COMMENT ON COLUMN notifications.scheduled_at IS 'Cuándo debe enviarse; NULL = inmediato';
COMMENT ON COLUMN notifications.sent_at      IS 'Cuándo fue efectivamente enviado; NULL = aún pendiente';


-- ============================================================
-- INDEXES
-- ============================================================

-- users
CREATE INDEX idx_users_status          ON users(status);

-- auth_accounts
CREATE INDEX idx_auth_accounts_user_id ON auth_accounts(user_id);
CREATE INDEX idx_auth_accounts_provider ON auth_accounts(provider, provider_user_id);

-- password_reset_tokens
CREATE INDEX idx_prt_user_id           ON password_reset_tokens(user_id);
CREATE INDEX idx_prt_expires_at        ON password_reset_tokens(expires_at);

-- courses
CREATE INDEX idx_courses_created_by    ON courses(created_by);
CREATE INDEX idx_courses_is_archived   ON courses(is_archived);

-- syllabi
CREATE INDEX idx_syllabi_course_id     ON syllabi(course_id);
CREATE INDEX idx_syllabi_is_active     ON syllabi(is_active);

-- syllabus_topics
CREATE INDEX idx_syllabus_topics_syl   ON syllabus_topics(syllabus_id);

-- course_memberships
CREATE INDEX idx_cm_user_id            ON course_memberships(user_id);

-- notes
CREATE INDEX idx_notes_course_id       ON notes(course_id);
CREATE INDEX idx_notes_author_id       ON notes(author_id);
CREATE INDEX idx_notes_visibility      ON notes(visibility);
CREATE INDEX idx_notes_note_date       ON notes(note_date);
CREATE INDEX idx_notes_title_trgm      ON notes USING gin (title gin_trgm_ops);

-- note_pages
CREATE INDEX idx_note_pages_note_id    ON note_pages(note_id);

-- recognition_reports
CREATE INDEX idx_recog_note_id         ON recognition_reports(note_id);
CREATE INDEX idx_recog_status          ON recognition_reports(status);

-- note_adaptations
CREATE INDEX idx_nadapt_note_id        ON note_adaptations(note_id);
CREATE INDEX idx_nadapt_user_id        ON note_adaptations(user_id);

-- quizzes
CREATE INDEX idx_quizzes_user_id       ON quizzes(user_id);
CREATE INDEX idx_quizzes_course_id     ON quizzes(course_id);
CREATE INDEX idx_quizzes_note_id       ON quizzes(note_id);
CREATE INDEX idx_quizzes_topic_id      ON quizzes(topic_id);
CREATE INDEX idx_quizzes_completed_at  ON quizzes(completed_at);
CREATE INDEX idx_quizzes_content_gin   ON quizzes USING gin (content);

-- quiz_answers
CREATE INDEX idx_quiz_answers_quiz_id  ON quiz_answers(quiz_id);
CREATE INDEX idx_quiz_answers_user_id  ON quiz_answers(user_id);


-- ============================================================
-- TRIGGER: auto-update updated_at
-- ============================================================

CREATE OR REPLACE FUNCTION trg_set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := NOW();
RETURN NEW;
END;
$$;

CREATE TRIGGER set_updated_at_users
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TRIGGER set_updated_at_notes
    BEFORE UPDATE ON notes
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TRIGGER set_updated_at_note_ratings
    BEFORE UPDATE ON note_ratings
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();


-- ============================================================
-- COMMENTS
-- ============================================================

COMMENT ON TABLE users                IS 'Usuarios del sistema';
COMMENT ON TABLE roles                IS 'Roles asignables a usuarios';
COMMENT ON TABLE permissions          IS 'Permisos granulares del sistema';
COMMENT ON TABLE user_roles           IS 'Relación N:M usuarios-roles';
COMMENT ON TABLE role_permissions     IS 'Relación N:M roles-permisos';
COMMENT ON TABLE auth_accounts        IS 'Cuentas de autenticación (OAuth / password)';
COMMENT ON TABLE password_reset_tokens IS 'Tokens temporales para reset de contraseña';
COMMENT ON TABLE user_preferences     IS 'Preferencias de estudio por usuario (1:1)';

COMMENT ON TABLE courses              IS 'Cursos creados por los usuarios';
COMMENT ON TABLE syllabi              IS 'Programas académicos subidos por curso';
COMMENT ON TABLE syllabus_topics      IS 'Temas del programa de un curso';
COMMENT ON TABLE course_memberships   IS 'Membresías de usuarios en cursos';

COMMENT ON TABLE tags                 IS 'Etiquetas reutilizables para apuntes';
COMMENT ON TABLE notes                IS 'Apuntes cargados por los usuarios';
COMMENT ON TABLE note_pages           IS 'Páginas individuales de cada apunte';
COMMENT ON TABLE note_tags            IS 'Etiquetas asociadas a un apunte';
COMMENT ON TABLE note_favorites       IS 'Apuntes favoritos por usuario';
COMMENT ON TABLE note_ratings         IS 'Calificaciones de apuntes (1-5)';
COMMENT ON TABLE recognition_reports  IS 'Reportes de problemas en el OCR de apuntes';
COMMENT ON TABLE note_topic_coverage  IS 'Cobertura de temas del programa por apunte (IA)';
COMMENT ON TABLE user_topic_progress  IS 'Progreso de dominio por tema y usuario';
COMMENT ON TABLE note_adaptations     IS 'Adaptaciones de apuntes generadas por IA (resúmenes, mapas conceptuales, flashcards, audio, etc.)';