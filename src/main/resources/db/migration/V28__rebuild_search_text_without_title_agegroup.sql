-- search_text 재구성: title, age_group 제외 → content(100자) + therapy_area 한글만 포함
UPDATE therapy_posts
SET search_text = TRIM(
    COALESCE(LEFT(content, 100), '') || ' ' ||
    CASE therapy_area
        WHEN 'UNSPECIFIED'         THEN '선택안함'
        WHEN 'SENSORY_INTEGRATION' THEN '감각통합'
        WHEN 'SPEECH'              THEN '언어치료'
        WHEN 'OCCUPATIONAL'        THEN '작업치료'
        WHEN 'COGNITIVE'           THEN '인지치료'
        WHEN 'PHYSICAL'            THEN '물리치료'
        WHEN 'ART'                 THEN '미술치료'
        WHEN 'MUSIC'               THEN '음악치료'
        WHEN 'PLAY'                THEN '놀이치료'
        WHEN 'BEHAVIOR'            THEN '행동치료'
        ELSE ''
    END
);
