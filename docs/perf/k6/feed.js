// 피드 무한스크롤 시나리오 — GET /api/v1/posts/feed
// 커서 기반 페이지네이션. 커서를 받아 이어서 요청하는 실제 사용자 패턴 모사.
//
// 검증 포인트:
// - likeCount / commentCount 배치 쿼리 성능 (N+1 방지 효과)
// - popularityScore 커서 인덱스 활용

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN;
if (!TOKEN) throw new Error('TOKEN env required. See k6/README.md');

const pageLatency = new Trend('feed_page_latency', true);

export const options = {
    scenarios: {
        feed_scroll: {
            executor: 'constant-vus',
            vus: 30,
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

export default function () {
    // 랜덤 정렬 선택
    const sort = Math.random() < 0.5 ? 'LATEST' : 'POPULAR';

    group('feed_scroll_3_pages', () => {
        let cursor = null;
        for (let page = 0; page < 3; page++) {
            const url = cursor
                ? `${BASE_URL}/api/v1/posts/feed?size=20&sort=${sort}&cursor=${encodeURIComponent(cursor)}`
                : `${BASE_URL}/api/v1/posts/feed?size=20&sort=${sort}`;

            const start = Date.now();
            const res = http.get(url, { headers, tags: { name: 'feed_page', sort } });
            pageLatency.add(Date.now() - start);

            const ok = check(res, {
                'status is 200': (r) => r.status === 200,
                'has items array': (r) => {
                    try {
                        return Array.isArray(r.json('data.items'));
                    } catch (_) {
                        return false;
                    }
                },
            });

            if (!ok) {
                break;
            }

            cursor = res.json('data.nextCursor');
            if (!cursor) break;
        }
    });

    sleep(Math.random() * 0.3); // 0~300ms 사용자 관찰 시간
}
