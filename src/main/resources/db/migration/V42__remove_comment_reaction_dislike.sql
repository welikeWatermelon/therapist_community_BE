-- MEL-36: 댓글 reaction 종류 통일 (LIKE/DISLIKE → LIKE/CURIOUS/USEFUL)
--
-- 게시글 PostReactionType과 일관성을 위해 CommentReactionType을 3종으로 통일.
-- DISLIKE는 LIKE와 의미가 반대라 LIKE/CURIOUS/USEFUL로 의미 변환이 부적절 → 그대로 삭제.
-- 출시 초기라 데이터 영향 적음.
--
-- idempotent: 재실행해도 안전 (이미 DISLIKE row가 없으면 0개 삭제).

DELETE FROM therapy_post_comment_reactions
WHERE reaction_type = 'DISLIKE';
