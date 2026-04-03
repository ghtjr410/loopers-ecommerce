#!/bin/bash
# k6 실행 중 2초마다 서버 상태 확인
# Actuator가 8081 포트에서 서빙됨 (monitoring.yml)

ACTUATOR="${ACTUATOR_URL:-http://localhost:8081}"

echo "모니터링 시작 (Actuator: $ACTUATOR)"
echo "Ctrl+C로 종료"
echo ""

while true; do
  echo "=== $(date '+%H:%M:%S') ==="

  # Hikari 활성 커넥션
  echo -n "  Hikari active: "
  curl -s "$ACTUATOR/actuator/metrics/hikaricp.connections.active" 2>/dev/null | jq -r '.measurements[0].value // "N/A"'

  # Hikari 대기 중
  echo -n "  Hikari pending: "
  curl -s "$ACTUATOR/actuator/metrics/hikaricp.connections.pending" 2>/dev/null | jq -r '.measurements[0].value // "N/A"'

  # Hikari 전체 커넥션
  echo -n "  Hikari total: "
  curl -s "$ACTUATOR/actuator/metrics/hikaricp.connections" 2>/dev/null | jq -r '.measurements[0].value // "N/A"'

  # Tomcat 활성 스레드
  echo -n "  Tomcat busy: "
  curl -s "$ACTUATOR/actuator/metrics/tomcat.threads.busy" 2>/dev/null | jq -r '.measurements[0].value // "N/A"'

  # Tomcat 현재 스레드 수
  echo -n "  Tomcat current: "
  curl -s "$ACTUATOR/actuator/metrics/tomcat.threads.current" 2>/dev/null | jq -r '.measurements[0].value // "N/A"'

  # JVM 힙 사용량 (MB)
  echo -n "  JVM heap (MB): "
  curl -s "$ACTUATOR/actuator/metrics/jvm.memory.used?tag=area:heap" 2>/dev/null | jq -r '(.measurements[0].value / 1048576 | floor) // "N/A"'

  echo ""
  sleep 2
done
