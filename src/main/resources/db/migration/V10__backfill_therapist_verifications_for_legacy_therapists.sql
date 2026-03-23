-- role =THERAPIST 인데, therapist_verifications row가 없는 legacy 사용자 백필
INSERT INTO therapist_verifications(
                                    user_id,
                                    license_code,
                                    license_image_path,
                                    license_image_original_name,
                                    license_image_content_type,
                                    status,
                                    reviewed_by,
                                    reviewed_at,
                                    reject_reason,
                                    created_at,
                                    updated_at
)
SELECT
    u.id,
    CONCAT('LEGACY-BF-U', u.id),
    CONCAT('legacy/backfill/therapist-verifications/',u.id,'.bin'),
    'legacy-migrated.bin',
    'application/octet-stream',
    'APPROVED',
    NULL,
    COALESCE(u.updated_at, u.created_at, NOW()),
    'Backfilled from users.role=THERAPIST',
    COALESCE(u.created_at,NOW()),
    COALESCE(u.updated_at,NOW())
FROM users u
WHERE u.role = 'THERAPIST'
    AND u.deleted_at IS NULL
    AND NOT EXISTS (
        SELECT 1
        FROM therapist_verifications tv
        WHERE tv.user_id = u.id
);