import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

/**
 * Test G: 전원 입장 완료 시간 실측 — 역산값 검증
 *
 * 목적: 500명이 대기열 진입 후 전원 입장까지 걸리는 시간을 측정하여
 *       시나리오 역산값(5,000명/125명/10초 = 400초 ≈ 6.7분)과 대조한다.
 *
 * 역산 기대값 (1/10 축소):
 *   500명 ÷ 125명/배치 = 4배치 × 10초 = 40초
 *   (IP rate limit으로 진입 분산 시 +10초 → ~50초)
 *
 * 측정:
 *   - 각 VU의 대기 시간 (진입 → READY)
 *   - 마지막 VU가 READY되는 시점 = 전원 입장 시간
 *
 * 사전 조건: Test B와 동일
 *   queue.admission-batch-size: 125
 *
 * 실행:
 *   k6 run k6/test-g-drain-time.js
 */

// ── 메트릭 ──
const admissionWait = new Trend('admission_wait_seconds', true);  // 개인 대기 시간
const admittedCount = new Counter('admitted_total');
const failedCount = new Counter('failed_total');

// ── 설정 ──
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_HEADER = 'admin-ldap';

export const options = {
  scenarios: {
    drain_test: {
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

  // 1. 진입 시점 분산 (IP rate limit 고려)
  sleep(Math.random() * 10);

  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
    headers: { 'X-Loopers-UserId': String(userId) },
    tags: { name: 'POST_queue_enter' },
  });

  if (enterRes.status !== 200) {
    console.log(`[VU${userId}] 진입 실패: ${enterRes.status}`);
    failedCount.add(1);
    return;
  }

  // 2. 입장 대기 — 시간 측정
  const waitStart = Date.now();

  for (let i = 0; i < 600; i++) {
    sleep(1);
    const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, {
      headers: { 'X-Loopers-UserId': String(userId) },
      tags: { name: 'GET_queue_position' },
    });

    try {
      const body = JSON.parse(posRes.body);
      if (body.data && body.data.status === 'READY') {
        const waitSec = (Date.now() - waitStart) / 1000;
        const elapsedFromStart = (Date.now() - data.startEpoch) / 1000;

        admissionWait.add(waitSec * 1000); // Trend는 ms 단위
        admittedCount.add(1);

        console.log(`[ADMITTED] VU=${userId} wait=${waitSec.toFixed(1)}s elapsed=${elapsedFromStart.toFixed(1)}s`);
        return;
      }
    } catch (e) { /* continue */ }
  }

  // 타임아웃
  console.log(`[VU${userId}] 입장 타임아웃`);
  failedCount.add(1);
}

export function teardown(data) {
  const totalSec = ((Date.now() - data.startEpoch) / 1000).toFixed(1);

  http.post(`${BASE_URL}/api-admin/v1/queue/mode`,
    JSON.stringify({ mode: 'NORMAL' }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Loopers-Ldap': ADMIN_HEADER,
      },
    }
  );

  console.log('\n========================================');
  console.log(`  전원 입장 완료 시간: ${totalSec}초`);
  console.log('  역산 기대값: ~50초 (500명 / 125명/배치 × 10초 + 진입분산)');
  console.log('========================================');
  console.log('[분석] grep "[ADMITTED]" result.log | tail -1 → 마지막 입장 시점');
  console.log('[분석] admission_wait_seconds p50/p99 → 개인 대기 시간 분포');
}
