-- V8__create_library_tables.sql

-- 전제:
-- 기존 migration에서 users 테이블이 이미 존재한다고 가정

CREATE TYPE library_resource_type AS ENUM (
  'ACTIVITY_WORKSHEET',
  'ADMIN_TEMPLATE',
  'TEACHING_MATERIAL',
  'ETC'
);

CREATE TYPE library_resource_status AS ENUM (
  'DRAFT',
  'PUBLISHED',
  'HIDDEN',
  'DELETED'
);

CREATE TYPE library_access_type AS ENUM (
  'FREE',
  'PAID'
);

CREATE TYPE library_report_reason AS ENUM (
  'COPYRIGHT',
  'PRIVACY',
  'INAPPROPRIATE',
  'SPAM',
  'ETC'
);

CREATE TYPE report_status AS ENUM (
  'PENDING',
  'RESOLVED',
  'DISMISSED'
);

CREATE TABLE library_resources (
                                   id BIGSERIAL PRIMARY KEY,
                                   uploader_id BIGINT NOT NULL REFERENCES users(id),

                                   title VARCHAR(255) NOT NULL,
                                   description TEXT,

                                   therapy_area VARCHAR(50) NOT NULL DEFAULT 'UNSPECIFIED',
                                   age_group VARCHAR(50) NOT NULL DEFAULT 'UNSPECIFIED',
                                   resource_type library_resource_type NOT NULL,

                                   status library_resource_status NOT NULL DEFAULT 'PUBLISHED',
                                   access_type library_access_type NOT NULL DEFAULT 'FREE',
                                   price_amount INT NOT NULL DEFAULT 0 CHECK (price_amount >= 0),

                                   thumbnail_image_path TEXT,
                                   thumbnail_image_original_name VARCHAR(255),
                                   thumbnail_image_content_type VARCHAR(255),

                                   view_count BIGINT NOT NULL DEFAULT 0 CHECK (view_count >= 0),
                                   download_count BIGINT NOT NULL DEFAULT 0 CHECK (download_count >= 0),
                                   scrap_count INT NOT NULL DEFAULT 0 CHECK (scrap_count >= 0),
                                   reaction_count INT NOT NULL DEFAULT 0 CHECK (reaction_count >= 0),

                                   created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                   updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                   deleted_at TIMESTAMP
);

CREATE INDEX idx_library_resources_uploader_id
    ON library_resources (uploader_id);

CREATE INDEX idx_library_resources_status_created_at
    ON library_resources (status, created_at DESC);

CREATE INDEX idx_library_resources_type_area_created_at
    ON library_resources (resource_type, therapy_area, created_at DESC);

CREATE TABLE library_resource_files (
                                        id BIGSERIAL PRIMARY KEY,
                                        resource_id BIGINT NOT NULL REFERENCES library_resources(id) ON DELETE CASCADE,

                                        stored_path TEXT NOT NULL,
                                        original_filename VARCHAR(255) NOT NULL,
                                        content_type VARCHAR(255) NOT NULL,
                                        size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
                                        extension VARCHAR(20),
                                        file_order INT NOT NULL DEFAULT 0 CHECK (file_order >= 0),
                                        preview_supported BOOLEAN NOT NULL DEFAULT FALSE,

                                        created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_library_resource_files_resource_order
    ON library_resource_files (resource_id, file_order ASC);

CREATE TABLE library_resource_reactions (
                                            id BIGSERIAL PRIMARY KEY,

                                            user_id BIGINT NOT NULL REFERENCES users(id),
                                            resource_id BIGINT NOT NULL REFERENCES library_resources(id) ON DELETE CASCADE,

                                            reaction_type VARCHAR(50) NOT NULL,
                                            created_at TIMESTAMP NOT NULL DEFAULT NOW(),

                                            CONSTRAINT uq_library_resource_reactions_user_resource UNIQUE (user_id, resource_id)
);

CREATE INDEX idx_library_resource_reactions_resource_id
    ON library_resource_reactions (resource_id);

CREATE TABLE library_resource_scraps (
                                         id BIGSERIAL PRIMARY KEY,

                                         user_id BIGINT NOT NULL REFERENCES users(id),
                                         resource_id BIGINT NOT NULL REFERENCES library_resources(id) ON DELETE CASCADE,

                                         created_at TIMESTAMP NOT NULL DEFAULT NOW(),

                                         CONSTRAINT uq_library_resource_scraps_user_resource UNIQUE (user_id, resource_id)
);

CREATE INDEX idx_library_resource_scraps_resource_id
    ON library_resource_scraps (resource_id);

CREATE TABLE library_download_logs (
                                       id BIGSERIAL PRIMARY KEY,

                                       user_id BIGINT NOT NULL REFERENCES users(id),
                                       resource_id BIGINT NOT NULL REFERENCES library_resources(id) ON DELETE CASCADE,
                                       file_id BIGINT NOT NULL REFERENCES library_resource_files(id) ON DELETE CASCADE,

                                       downloaded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_library_download_logs_resource_downloaded_at
    ON library_download_logs (resource_id, downloaded_at DESC);

CREATE INDEX idx_library_download_logs_user_downloaded_at
    ON library_download_logs (user_id, downloaded_at DESC);

CREATE INDEX idx_library_download_logs_file_downloaded_at
    ON library_download_logs (file_id, downloaded_at DESC);

CREATE TABLE library_resource_reports (
                                          id BIGSERIAL PRIMARY KEY,

                                          reporter_id BIGINT NOT NULL REFERENCES users(id),
                                          resource_id BIGINT NOT NULL REFERENCES library_resources(id) ON DELETE CASCADE,

                                          reason_code library_report_reason NOT NULL,
                                          reason_detail TEXT,
                                          status report_status NOT NULL DEFAULT 'PENDING',

                                          processed_by BIGINT REFERENCES users(id),
                                          processed_at TIMESTAMP,

                                          created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_library_resource_reports_resource_status_created_at
    ON library_resource_reports (resource_id, status, created_at DESC);

-- updated_at 자동 갱신 함수가 없으면 생성
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_library_resources_set_updated_at
    BEFORE UPDATE ON library_resources
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
