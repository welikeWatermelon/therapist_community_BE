INSERT INTO users (email, password_hash, nickname, role, created_at, updated_at)
VALUES ('ai-comment@system.local', NULL, 'Melonne AI', 'ADMIN', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;
