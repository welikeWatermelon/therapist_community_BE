// 반응 토글 시나리오 — PUT /api/v1/posts/{id}/reaction (쓰기 부하)
// 토글 후 popularity_score 재계산, 알림 이벤트 발행 포함.
//
// 검증 포인트:
// - UNIQUE(post_id, user_id) 충돌 처리 (같은 유저 중복 토글)
// - TherapyPostRepository.recalculatePopularityScore 쿼리 속도
// - GROUP BY countGroupedByPostId 쿼리 (응답 생성 경로)
// - 트랜잭션 이벤트 발행 (@TransactionalEventListener AFTER_COMMIT)

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN;
if (!TOKEN) throw new Error('TOKEN env required. See k6/README.md');

export const options = {
    scenarios: {
        reaction_toggle: {
            executor: 'constant-vus',
            vus: 15,          // 쓰기 부하라 보수적으로
            duration: '60s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
    },
};

const headers = {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json',
};

const TYPES = ['LIKE', 'CURIOUS', 'USEFUL'];

export default function () {
    const postId = 1 + Math.floor(Math.random() * 8500);
    const reactionType = TYPES[Math.floor(Math.random() * TYPES.length)];

    const res = http.put(
        `${BASE_URL}/api/v1/posts/${postId}/reaction`,
        JSON.stringify({ reactionType }),
        { headers, tags: { name: 'reaction_toggle', type: reactionType } }
    );

    check(res, {
        'status 200 or 403': (r) => r.status === 200 || r.status === 403,
        'response has reactionCounts when 200': (r) => {
            if (r.status !== 200) return true;
            try {
                const c = r.json('data.reactionCounts');
                return c && typeof c === 'object';
            } catch (_) {
                return false;
            }
        },
    });

    sleep(Math.random() * 0.3);
}
