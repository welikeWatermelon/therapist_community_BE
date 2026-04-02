-- therapy_posts: UNSPECIFIED → NULL
UPDATE therapy_posts
SET therapy_area = NULL
WHERE therapy_area = 'UNSPECIFIED';

-- library_resources: UNSPECIFIED → NULL, DEFAULT 제거
UPDATE library_resources
SET therapy_area = NULL
WHERE therapy_area = 'UNSPECIFIED';

ALTER TABLE library_resources
    ALTER COLUMN therapy_area DROP DEFAULT,
    ALTER COLUMN therapy_area DROP NOT NULL;
