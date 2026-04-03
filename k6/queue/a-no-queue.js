import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

/**
 * Test A: 대기열 없이 직접 주문 — 서버가 터지는 것을 증명
 *
 * 목적: 5,000명이 대기열 없이 직접 POST /orders를 때리면
 *       p99 폭등, 에러율 급증, Hikari 고갈이 발생함을 보인다.
 *
 * 사전 조건:
 *   1. docker-compose-loadtest.yml 기동
 *   2. setup-loadtest-data.sh 실행 (브랜드 + 상품 + 유저 생성)
 *   3. 서버 모드: NORMAL (기본값, 대기열 비활성)
 *   4. Rate Limit 비활성화 (application-loadtest.yml에 아래 추가):
 *        queue:
 *          order-limit-per-minute: 99999
 *          ip-rate-limit-per-second: 99999
 *      → 대기열 보호 없이 순수 서버 한계를 측정하기 위함
 *
 * 실행:
 *   k6 run k6/test-a-no-queue.js
 *
 * SLO 기준 (시나리오 문서):
 *   p99 ≤ 500ms, 에러율 ≤ 0.1%
 *   → 이 테스트에서 SLO가 위반되어야 정상
 */

// ── 커스텀 메트릭 ──
const orderSuccess = new Rate('order_success_rate');
const orderP99 = new Trend('order_p99_duration', true);
const sloViolations = new Counter('slo_violations');     // p99 > 500ms인 요청
const serverErrors = new Counter('server_5xx');
const connRefused = new Counter('conn_refused');

// ── 설정 ──
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const NORMAL_PRODUCT_IDS = [4, 5, 6, 7, 8, 9, 10];

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 200 },   // warm-up
        { duration: '20s', target: 500 },   // ramp to 500
        { duration: '60s', target: 500 },   // sustained load (핵심 구간)
        { duration: '30s', target: 300 },   // cool-down 시작
        { duration: '10s', target: 0 },     // ramp-down
      ],
    },
  },
  thresholds: {
    // 의도적으로 느슨하게 — 이 테스트는 "위반됨"을 보여주는 게 목적
    // 실제 SLO(p99 ≤ 500ms)는 리포트에서 수동 비교
    http_req_duration: ['p(99)<10000'],
    order_success_rate: ['rate>0.5'],
  },
};

export default function () {
  const userId = __VU;

  // 상품 1~2개 랜덤 선택
  const numItems = Math.random() < 0.7 ? 1 : 2;
  const orderItems = [];
  const usedProducts = new Set();

  for (let i = 0; i < numItems; i++) {
    let productId;
    do {
      productId = NORMAL_PRODUCT_IDS[Math.floor(Math.random() * NORMAL_PRODUCT_IDS.length)];
    } while (usedProducts.has(productId));
    usedProducts.add(productId);
    orderItems.push({ productId, quantity: 1 });
  }

  const res = http.post(`${BASE_URL}/api/v1/orders`, JSON.stringify({ orderItems }), {
    headers: {
      'Content-Type': 'application/json',
      'X-Loopers-UserId': String(userId),
    },
    tags: { name: 'POST_orders' },
  });

  const success = check(res, {
    'status 200': (r) => r.status === 200,
  });

  orderSuccess.add(success ? 1 : 0);
  orderP99.add(res.timings.duration);

  // SLO 위반 카운트 (p99 > 500ms)
  if (res.timings.duration > 500) {
    sloViolations.add(1);
  }

  if (res.status === 0) {
    connRefused.add(1);
  } else if (res.status >= 500) {
    serverErrors.add(1);
  }

  // 에러 샘플링 — VU당 첫 3건만 출력
  if (!success && __ITER < 3) {
    console.log(`[VU${__VU}] status=${res.status} duration=${res.timings.duration.toFixed(0)}ms body=${(res.body || '').substring(0, 200)}`);
  }

  // sleep 없음 — 서버 한계점을 탐색하기 위해 연속 요청
}
