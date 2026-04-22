// 게시글 목록/검색 시나리오 — GET /api/v1/posts
// 키워드 + therapyArea 필터 혼합. pg_trgm / trigram 인덱스 활용 측정.
//
// 검증 포인트:
// - searchByKeyword LIKE 성능
// - visibility 필터 + 인덱스
// - likeCount / commentCount 배치 쿼리

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN;
if (!TOKEN) throw new Error('TOKEN env required. See k6/README.md');

export const options = {
    scenarios: {
        search_mixed: {
            executor: 'constant-vus',
            vus: 20,
            duration: '60s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<600', 'p(99)<1200'],
    },
};

const headers = {
    Authorization: `Bearer ${TOKEN}`,
};

const KEYWORDS = ['감각', '언어', '작업', '인지', '놀이', '테스트', '게시글'];
const AREAS = ['SPEECH', 'OCCUPATIONAL', 'COGNITIVE', 'PLAY', 'SENSORY_INTEGRATION'];

export default function () {
    const scenario = Math.random();

    let url;
    let tag;
    if (scenario < 0.4) {
        // 40% — 키워드만
        const kw = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
        url = `${BASE_URL}/api/v1/posts?page=0&size=10&sortType=LATEST&keyword=${encodeURIComponent(kw)}`;
        tag = 'keyword_only';
    } else if (scenario < 0.7) {
        // 30% — therapyArea 필터만
        const area = AREAS[Math.floor(Math.random() * AREAS.length)];
        url = `${BASE_URL}/api/v1/posts?page=0&size=10&sortType=LATEST&therapyArea=${area}`;
        tag = 'area_only';
    } else if (scenario < 0.9) {
        // 20% — 키워드 + therapyArea
        const kw = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
        const area = AREAS[Math.floor(Math.random() * AREAS.length)];
        url = `${BASE_URL}/api/v1/posts?page=0&size=10&sortType=LATEST&keyword=${encodeURIComponent(kw)}&therapyArea=${area}`;
        tag = 'keyword_area';
    } else {
        // 10% — 필터 없음 (전체 페이지)
        url = `${BASE_URL}/api/v1/posts?page=0&size=10&sortType=LATEST`;
        tag = 'no_filter';
    }

    const res = http.get(url, { headers, tags: { name: 'search', variant: tag } });

    check(res, {
        'status 200': (r) => r.status === 200,
        'items array': (r) => {
            try {
                return Array.isArray(r.json('data.items'));
            } catch (_) {
                return false;
            }
        },
    });

    sleep(Math.random() * 0.5);
}
