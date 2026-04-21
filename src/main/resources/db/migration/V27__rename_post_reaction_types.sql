-- PostReactionType enum 리네임에 맞춰 기존 데이터 업데이트.
-- EMPATHY    → LIKE    (라벨: 공감      → 좋아요)
-- APPRECIATE → CURIOUS (라벨: 잘 봤어요 → 궁금해요)
-- HELPFUL    → USEFUL  (라벨: 유익      → 유용해요)
--
-- 운영 데이터는 아직 적고, 프론트와 동시 배포 전제.

UPDATE therapy_post_reactions SET reaction_type = 'LIKE'    WHERE reaction_type = 'EMPATHY';
UPDATE therapy_post_reactions SET reaction_type = 'CURIOUS' WHERE reaction_type = 'APPRECIATE';
UPDATE therapy_post_reactions SET reaction_type = 'USEFUL'  WHERE reaction_type = 'HELPFUL';
