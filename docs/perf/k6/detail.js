// 게시글 상세 조회 시나리오 — GET /api/v1/posts/{id}
// 조회수 증가(Redis SETNX) + reaction grouped count + comment count + attachments 조회 포함.
//
// 검증 포인트:
// - reactionCounts EnumMap 빌드 쿼리
// - myReactionType 조회 (post_id + user_id)
// - commentCount (deleted_at 필터)
// - attachments findByPostIdOrderByCreatedAtAsc (N+1 가능성)

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN;
if (!TOKEN) throw new Error('TOKEN env required. See k6/README.md');

export const options = {
    scenarios: {
        detail_read: {
            executor: 'constant-vus',
            vus: 40,
            duration: '60s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<400', 'p(99)<800'],
    },
};

const headers = { Authorization: `Bearer ${TOKEN}` };

// seed: 10,000개 게시글. visibility 분포상 PUBLIC 대상만 랜덤 선택해 403 최소화.
// 임의로 1~8500 범위에서 선택 (통계적으로 PUBLIC 비율 85%).
export default function () {
    const postId = 1 + Math.floor(Math.random() * 8500);
    const url = `${BASE_URL}/api/v1/posts/${postId}`;

    const res = http.get(url, { headers, tags: { name: 'detail' } });

    check(res, {
        'status 200 or 403': (r) => r.status === 200 || r.status === 403,
        'has commentCount when 200': (r) => {
            if (r.status !== 200) return true;
            try {
                return typeof r.json('data.commentCount') === 'number';
            } catch (_) {
                return false;
            }
        },
        'has reactionCounts map when 200': (r) => {
            if (r.status !== 200) return true;
            try {
                const c = r.json('data.reactionCounts');
                return c && typeof c === 'object';
            } catch (_) {
                return false;
            }
        },
    });

    sleep(Math.random() * 0.5);
}
