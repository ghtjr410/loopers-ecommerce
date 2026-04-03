import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

/**
 * Test C: 고정 rate 입장 시 주문 TPS 분산 — 시계열 그래프용
 *
 * 목적: 10초마다 125명 입장하는 고정 rate가
 *       Thundering Herd 없이 주문 TPS를 평탄하게 분산함을 보인다.
 *
 * 측정:
 *   - 10초 구간별 주문 건수 (console 출력 → 그래프 데이터)
 *   - 순간 피크 TPS vs 전체 평균 TPS
 *
 * 사전 조건: Test B와 동일
 *   queue.admission-batch-size: 125
 *
 * 실행:
 *   k6 run k6/test-c-tps-distribution.js
 *
 * 결과 분석:
 *   콘솔에 10초 단위 주문 건수가 출력됨.
 *   이를 그래프로 그리면 "평탄한 TPS 분포"가 보여야 함.
 *   (active count 기반이면 첫 구간에 스파이크가 발생)
 */

// ── 메트릭 ──
const orderCount = new Counter('order_total');
const orderDuration = new Trend('order_duration', true);

// 10초 구간별 주문 카운트 (in-memory, 그래프 데이터용)
const bucketCounts = {};

// ── 설정 ──
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_HEADER = 'admin-ldap';
const NORMAL_PRODUCT_IDS = [4, 5, 6, 7, 8, 9, 10];

export const options = {
  scenarios: {
    queue_flow: {
      executor: 'per-vu-iterations',
      vus: 500,
      iterations: 1,
      maxDuration: '15m',
    },
  },
};

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
  check(res, { 'EVENT mode': (r) => r.status === 200 });
  sleep(2);
  return { startEpoch: Date.now() };
}

export default function (data) {
  const userId = __VU;

  // 1. 진입 (분산)
  sleep(Math.random() * 10);
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
    headers: { 'X-Loopers-UserId': String(userId) },
  });
  if (enterRes.status !== 200) return;

  // 2. 입장 대기
  for (let i = 0; i < 600; i++) {
    sleep(1);
    const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, {
      headers: { 'X-Loopers-UserId': String(userId) },
    });
    try {
      const body = JSON.parse(posRes.body);
      if (body.data && body.data.status === 'READY') break;
    } catch (e) { /* continue */ }
  }

  // 3. 구경 시간 (현실적 분산: 2~15초)
  sleep(2 + Math.random() * 13);

  // 4. 주문 — 시각 기록
  const productId = NORMAL_PRODUCT_IDS[Math.floor(Math.random() * NORMAL_PRODUCT_IDS.length)];
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ orderItems: [{ productId, quantity: 1 }] }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Loopers-UserId': String(userId),
      },
      tags: { name: 'POST_orders' },
    }
  );

  if (orderRes.status === 200) {
    orderCount.add(1);
    orderDuration.add(orderRes.timings.duration);

    // 10초 버킷에 기록
    const elapsed = Math.floor((Date.now() - data.startEpoch) / 10000);
    const bucket = `${elapsed * 10}-${(elapsed + 1) * 10}s`;
    console.log(`[ORDER] bucket=${bucket} VU=${userId} duration=${orderRes.timings.duration.toFixed(0)}ms`);
  }
}

export function teardown(data) {
  http.post(`${BASE_URL}/api-admin/v1/queue/mode`,
    JSON.stringify({ mode: 'NORMAL' }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Loopers-Ldap': ADMIN_HEADER,
      },
    }
  );

  const totalSec = ((Date.now() - data.startEpoch) / 1000).toFixed(1);
  console.log(`\n[결과] 총 소요: ${totalSec}초`);
  console.log('[분석] 위 [ORDER] 로그를 10초 구간별로 집계하면 TPS 분포 그래프를 그릴 수 있음');
  console.log('[분석] 예: grep "\\[ORDER\\]" result.log | awk \'{print $2}\' | sort | uniq -c');
}
