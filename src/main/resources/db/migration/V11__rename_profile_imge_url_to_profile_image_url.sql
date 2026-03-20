DO $$
BEGIN
    -- Legacy typo column rename: users.profile_imge_url -> users.profile_image_url
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'users'
          AND column_name = 'profile_imge_url'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'users'
          AND column_name = 'profile_image_url'
    ) THEN
        ALTER TABLE users RENAME COLUMN profile_imge_url TO profile_image_url;
    END IF;
END $$;
