UPDATE therapy_posts SET therapy_area = 'UNSPECIFIED' WHERE therapy_area IS NULL;
ALTER TABLE therapy_posts ALTER COLUMN therapy_area DROP NOT NULL;
