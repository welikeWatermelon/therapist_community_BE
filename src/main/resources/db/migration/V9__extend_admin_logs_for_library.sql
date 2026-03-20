DO $$
BEGIN
    -- admin_logs 테이블이 없는 환경에서는 이 마이그레이션을 건너뛴다.
    IF to_regclass('public.admin_logs') IS NULL THEN
        RAISE NOTICE 'Skipping V9 migration: public.admin_logs does not exist.';
        RETURN;
    END IF;

    ALTER TABLE admin_logs
        ADD COLUMN IF NOT EXISTS library_resource_id BIGINT,
        ADD COLUMN IF NOT EXISTS library_resource_report_id BIGINT;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_admin_logs_library_resource_id'
    ) THEN
        ALTER TABLE admin_logs
            ADD CONSTRAINT fk_admin_logs_library_resource_id
                FOREIGN KEY (library_resource_id)
                REFERENCES library_resources(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_admin_logs_library_resource_report_id'
    ) THEN
        ALTER TABLE admin_logs
            ADD CONSTRAINT fk_admin_logs_library_resource_report_id
                FOREIGN KEY (library_resource_report_id)
                REFERENCES library_resource_reports(id);
    END IF;

    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_admin_logs_library_resource_id
             ON admin_logs (library_resource_id)';

    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_admin_logs_library_resource_report_id
             ON admin_logs (library_resource_report_id)';
END $$;
