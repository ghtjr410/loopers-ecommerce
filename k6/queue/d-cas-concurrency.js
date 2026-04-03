import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

/**
 * Test D: CAS 동시 주문 — 1건만 성공하는 것을 증명
 *
 * 목적: 같은 userId로 10개 VU가 동시에 POST /orders를 쏘면
 *       CAS(ACTIVE→CONSUMED) 성공이 정확히 1건이고,
 *       나머지 9건은 409 Conflict임을 보인다.
 *
 * 플로우:
 *   setup: EVENT 전환 → userId 999로 대기열 진입 → 입장 대기
 *   default: 10 VU가 userId 999로 동시 주문
 *   검증: 200 정확히 1건, 409 9건
 *
 * 사전 조건:
 *   1. docker-compose-loadtest.yml 기동
 *   2. setup-loadtest-data.sh 실행 (userId 999 존재)
 *   3. queue.admission-batch-size: 125 이상
 *   4. queue.order-limit-per-minute: 99999 (rate limit 비활성)
 *
 * 실행:
 *   k6 run k6/test-d-cas-concurrency.js
 */

// ── 메트릭 ──
const casSuccess = new Counter('cas_success_200');
const casConflict = new Counter('cas_conflict_409');
const casOther = new Counter('cas_other');

// ── 설정 ──
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_HEADER = 'admin-ldap';
const TARGET_USER_ID = 999;
const PRODUCT_ID = 4;  // NORMAL product (재고 충분)

export const options = {
  scenarios: {
    concurrent_order: {
      executor: 'shared-iterations',
      vus: 10,
      iterations: 10,          // 10 VU × 1 iteration each = 10건 동시
      maxDuration: '30s',
    },
  },
  // threshold 없음 — 수동으로 결과 확인
};

export function setup() {
  // 1. EVENT 모드 전환
  let res = http.post(`${BASE_URL}/api-admin/v1/queue/mode`,
    JSON.stringify({ mode: 'EVENT' }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Loopers-Ldap': ADMIN_HEADER,
      },
    }
  );
  check(res, { 'EVENT mode': (r) => r.status === 200 });

  // 2. 대기열 진입
  res = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
    headers: { 'X-Loopers-UserId': String(TARGET_USER_ID) },
  });
  console.log(`[setup] 대기열 진입: status=${res.status}`);

  // 3. 입장 대기 (스케줄러가 세션 발급할 때까지)
  let ready = false;
  for (let i = 0; i < 30; i++) {
    sleep(2);
    const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, {
      headers: { 'X-Loopers-UserId': String(TARGET_USER_ID) },
    });
    try {
      const body = JSON.parse(posRes.body);
      if (body.data && body.data.status === 'READY') {
        ready = true;
        console.log(`[setup] 입장 완료 (${(i + 1) * 2}초 대기)`);
        break;
      }
    } catch (e) { /* continue */ }
  }

  if (!ready) {
    console.log('[setup] ❌ 입장 타임아웃 — 테스트 중단');
    return { ready: false };
  }

  return { ready: true };
}

export default function (data) {
  if (!data.ready) return;

  // 모든 VU가 동시에 같은 userId로 주문
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ orderItems: [{ productId: PRODUCT_ID, quantity: 1 }] }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Loopers-UserId': String(TARGET_USER_ID),
      },
      tags: { name: 'POST_orders_cas' },
    }
  );

  console.log(`[VU${__VU}] status=${res.status} duration=${res.timings.duration.toFixed(0)}ms`);

  if (res.status === 200) {
    casSuccess.add(1);
  } else if (res.status === 409) {
    casConflict.add(1);
  } else {
    casOther.add(1);
    console.log(`[VU${__VU}] 예상외 응답: body=${(res.body || '').substring(0, 300)}`);
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
  console.log('\n[결과 해석]');
  console.log('  cas_success_200 = 1 → CAS 단건 보장 성공');
  console.log('  cas_conflict_409 = 9 → 나머지 모두 정상 거부');
  console.log('  cas_other > 0 → 예상외 (세션 만료 등 확인 필요)');
}
