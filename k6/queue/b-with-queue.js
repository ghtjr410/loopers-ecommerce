import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

/**
 * Test B: 대기열 경유 주문 — 서버가 안 터지는 것을 증명
 *
 * 목적: 500명이 대기열을 통해 입장 → 구경 → 주문하면
 *       p99 ≤ 500ms, 에러율 ≤ 0.1%로 SLO를 유지함을 보인다.
 *
 * 유저 플로우:
 *   POST /queue/enter → poll /queue/position → GET /products → POST /orders
 *
 * 사전 조건:
 *   1. docker-compose-loadtest.yml 기동
 *   2. setup-loadtest-data.sh 실행
 *   3. 서버 모드: NORMAL → 테스트 setup()에서 EVENT로 전환
 *   4. queue.admission-batch-size: 125 (1/10 축소, application-loadtest.yml)
 *      queue.admission-interval-ms: 10000
 *
 * 실행:
 *   k6 run k6/test-b-with-queue.js
 */

// ── 커스텀 메트릭 ──
const queueEnterSuccess = new Rate('queue_enter_success');
const orderSuccess = new Rate('order_success_rate');
const orderDuration = new Trend('order_duration', true);
const waitTime = new Trend('queue_wait_time', true);      // 대기열 대기 시간 (ms)
const e2eDuration = new Trend('e2e_duration', true);       // 진입 → 주문완료 전체 시간
const orderSloViolation = new Counter('order_slo_violations');
const orderErrors = new Counter('order_errors');

// ── 설정 ──
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_HEADER = 'admin-ldap';
const NORMAL_PRODUCT_IDS = [4, 5, 6, 7, 8, 9, 10];
const POLL_INTERVAL_SEC = 1;       // position 폴링 간격 (rate limit: 2/sec)
const MAX_WAIT_SEC = 600;          // 최대 대기 시간

export const options = {
  scenarios: {
    queue_flow: {
      executor: 'per-vu-iterations',
      vus: 500,
      iterations: 1,            // 각 VU가 정확히 1번 전체 플로우 실행
      maxDuration: '15m',
    },
  },
  thresholds: {
    order_success_rate: ['rate>0.95'],
    order_duration: ['p(99)<500'],      // SLO: p99 ≤ 500ms
  },
};

// ── Setup: EVENT 모드 전환 ──
export function setup() {
  const res = http.post(`${BASE_URL}/api-admin/v1/queue/mode`,
    JSON.stringify({ mode: 'EVENT' }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Loopers-Ldap': ADMIN_HEADER,
      },
    }
  );
  check(res, { 'mode switch to EVENT': (r) => r.status === 200 });
  console.log(`[setup] EVENT 모드 전환: status=${res.status}`);
  sleep(2); // 모드 전환 안정화
  return { startTime: Date.now() };
}

export default function (data) {
  const userId = __VU;
  const e2eStart = Date.now();

  // ── 1. 대기열 진입 ──
  // VU별로 진입 시점을 약간 분산 (IP rate limit 50/sec 회피)
  sleep(Math.random() * 10);

  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
    headers: { 'X-Loopers-UserId': String(userId) },
    tags: { name: 'POST_queue_enter' },
  });

  const entered = check(enterRes, {
    'queue enter 200': (r) => r.status === 200,
  });
  queueEnterSuccess.add(entered ? 1 : 0);

  if (!entered) {
    console.log(`[VU${userId}] 대기열 진입 실패: status=${enterRes.status} body=${(enterRes.body || '').substring(0, 200)}`);
    return;
  }

  // ── 2. 순번 폴링 — 입장까지 대기 ──
  const waitStart = Date.now();
  let admitted = false;

  for (let i = 0; i < MAX_WAIT_SEC / POLL_INTERVAL_SEC; i++) {
    sleep(POLL_INTERVAL_SEC);

    const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, {
      headers: { 'X-Loopers-UserId': String(userId) },
      tags: { name: 'GET_queue_position' },
    });

    if (posRes.status === 200) {
      try {
        const body = JSON.parse(posRes.body);
        if (body.data && body.data.status === 'READY') {
          admitted = true;
          break;
        }
      } catch (e) { /* parse error — continue polling */ }
    }
  }

  const waitDuration = Date.now() - waitStart;
  waitTime.add(waitDuration);

  if (!admitted) {
    console.log(`[VU${userId}] 입장 타임아웃 (${MAX_WAIT_SEC}초 초과)`);
    return;
  }

  // ── 3. 상품 구경 (현실적 유저 행동 시뮬레이션) ──
  const browseDelay = 2 + Math.random() * 8;  // 2~10초 구경
  sleep(browseDelay);

  http.get(`${BASE_URL}/api/v1/products`, {
    headers: { 'X-Loopers-UserId': String(userId) },
    tags: { name: 'GET_products' },
  });

  // ── 4. 주문 ──
  const productId = NORMAL_PRODUCT_IDS[Math.floor(Math.random() * NORMAL_PRODUCT_IDS.length)];
  const orderPayload = JSON.stringify({
    orderItems: [{ productId, quantity: 1 }],
  });

  const orderRes = http.post(`${BASE_URL}/api/v1/orders`, orderPayload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Loopers-UserId': String(userId),
    },
    tags: { name: 'POST_orders' },
  });

  const orderOk = check(orderRes, {
    'order status 200': (r) => r.status === 200,
  });

  orderSuccess.add(orderOk ? 1 : 0);
  orderDuration.add(orderRes.timings.duration);

  if (orderRes.timings.duration > 500) {
    orderSloViolation.add(1);
  }

  if (!orderOk) {
    orderErrors.add(1);
    if (__ITER < 3) {
      console.log(`[VU${userId}] 주문 실패: status=${orderRes.status} body=${(orderRes.body || '').substring(0, 200)}`);
    }
  }

  e2eDuration.add(Date.now() - e2eStart);
}

// ── Teardown: NORMAL 모드 복원 ──
export function teardown(data) {
  const res = http.post(`${BASE_URL}/api-admin/v1/queue/mode`,
    JSON.stringify({ mode: 'NORMAL' }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Loopers-Ldap': ADMIN_HEADER,
      },
    }
  );
  console.log(`[teardown] NORMAL 모드 복원: status=${res.status}`);
  console.log(`[teardown] 총 소요 시간: ${((Date.now() - data.startTime) / 1000).toFixed(1)}초`);
}
