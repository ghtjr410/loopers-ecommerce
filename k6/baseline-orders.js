import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ── 커스텀 메트릭 ──
const orderSuccess = new Rate('order_success');
const orderDuration = new Trend('order_duration', true);
const orderErrors = new Counter('order_errors');
const connRefused = new Counter('connection_refused');  // TCP backlog overflow
const serverErrors = new Counter('server_errors');      // 5xx (Hikari/Tomcat saturation)

// ── 설정 ──
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// NORMAL 상품 ID (setup-loadtest-data.sh에서 생성된 상품 4~10)
// ddl-auto: create이므로 ID는 1부터 순차 → NORMAL = 4~10
const NORMAL_PRODUCT_IDS = [4, 5, 6, 7, 8, 9, 10];

export const options = {
  scenarios: {
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 200,
      stages: [
        { duration: '30s', target: 200 },
        { duration: '30s', target: 300 },
        { duration: '30s', target: 400 },
        { duration: '30s', target: 500 },
        { duration: '10s', target: 0 },   // ramp-down
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<5000'],     // p99 < 5초
    order_success: ['rate>0.8'],           // 성공률 80% 이상
  },
};

export default function () {
  // VU별 고유 유저 — VU ID(1~200) 기반
  const userId = __VU;

  // NORMAL 상품 중 랜덤 1~2개 주문
  const numItems = Math.random() < 0.7 ? 1 : 2;
  const orderItems = [];
  const usedProducts = new Set();

  for (let i = 0; i < numItems; i++) {
    let productId;
    do {
      productId = NORMAL_PRODUCT_IDS[Math.floor(Math.random() * NORMAL_PRODUCT_IDS.length)];
    } while (usedProducts.has(productId));
    usedProducts.add(productId);
    orderItems.push({ productId: productId, quantity: 1 });
  }

  const payload = JSON.stringify({ orderItems: orderItems });

  const res = http.post(`${BASE_URL}/api/v1/orders`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Loopers-UserId': String(userId),  // loadtest bypass — BCrypt skip, 순수 주문 TPS 측정
    },
    tags: { name: 'POST_orders' },
  });

  const success = check(res, {
    'status 200': (r) => r.status === 200,
    'has order id': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.id != null;
      } catch (e) {
        return false;
      }
    },
  });

  orderSuccess.add(success ? 1 : 0);
  orderDuration.add(res.timings.duration);

  if (!success) {
    orderErrors.add(1);

    if (res.status === 0) {
      // status 0 = TCP 연결 자체 실패 (connection refused, reset, timeout)
      connRefused.add(1);
    } else if (res.status >= 500) {
      // 5xx = 서버가 연결은 받았지만 처리 실패 (Hikari 고갈, OOM 등)
      serverErrors.add(1);
    }

    // 에러 샘플링: VU당 첫 3건만 출력 (로그 폭주 방지)
    if (__ITER < 3) {
      console.log(`[VU${__VU}] status=${res.status} error=${res.error || 'none'} body=${(res.body || '').substring(0, 200)}`);
    }
  }

  // sleep 제거 — B안: VU가 쉬지 않고 연속 요청하여 서버 한계점 탐색
}
