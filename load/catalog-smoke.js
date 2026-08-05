// Smoke test for the catalog API.
//
// Usage:  k6 run load/catalog-smoke.js
//
// This is intentionally modest — the real load tests live in the same folder
// per-scenario (browse-heavy, write-heavy, cache-cold) and will be added as
// the other bounded contexts come online. See docs/architecture.md.

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    thresholds: {
        http_req_failed:   ['rate<0.01'],       // <1% errors
        http_req_duration: ['p(95)<250'],       // p95 under 250ms
    },
    scenarios: {
        browse: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20  },   // ramp-up
                { duration: '1m',  target: 100 },   // sustained
                { duration: '15s', target: 0   },   // ramp-down
            ],
            gracefulRampDown: '5s',
        },
    },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    const list = http.get(`${BASE}/api/v1/products?page=0&size=20`);
    check(list, {
        'GET /products 200': (r) => r.status === 200,
        'GET /products has body': (r) => r.body && r.body.length > 0,
    });
    sleep(0.5);
}
