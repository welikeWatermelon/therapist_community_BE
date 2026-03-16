CREATE TABLE therapist_verifications (
     id BIGSERIAL PRIMARY KEY,
     user_id BIGINT NOT NULL,
     license_code VARCHAR(100) NOT NULL,
     license_image_path TEXT NOT NULL,
     license_image_original_name VARCHAR(255) NOT NULL,
     license_image_content_type VARCHAR(100) NOT NULL,
     status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
     reviewed_by BIGINT NULL,
     reviewed_at TIMESTAMP NULL,
     reject_reason TEXT NULL,
     created_at TIMESTAMP NOT NULL,
     updated_at TIMESTAMP NOT NULL,

     CONSTRAINT fk_therapist_verifications_user
         FOREIGN KEY (user_id)
             REFERENCES users(id),

     CONSTRAINT fk_therapist_verifications_reviewed_by
         FOREIGN KEY (reviewed_by)
             REFERENCES users(id),

     CONSTRAINT uk_therapist_verifications_user
         UNIQUE (user_id),

     CONSTRAINT uk_therapist_verifications_license_code
         UNIQUE (license_code)
);

CREATE INDEX idx_therapist_verifications_status_created_at
    ON therapist_verifications(status, created_at DESC);

CREATE INDEX idx_therapist_verifications_reviewed_by
    ON therapist_verifications(reviewed_by);