#!/bin/bash
# k6 부하 테스트용 데이터 세팅 스크립트
# 실행 전 commerce-api가 정상 기동되어 있어야 함

BASE_URL="http://localhost:8080"
ADMIN_HEADER="X-Loopers-Ldap: admin-ldap"

echo "=== 1. 브랜드 생성 ==="
BRAND_RESPONSE=$(curl -s -X POST "$BASE_URL/api-admin/v1/brands" \
  -H "Content-Type: application/json" \
  -H "$ADMIN_HEADER" \
  -d '{"name": "LoadTest Brand", "description": "부하 테스트용 브랜드"}')
echo "$BRAND_RESPONSE"

BRAND_ID=$(echo "$BRAND_RESPONSE" | jq -r '.data.id')
if [ "$BRAND_ID" = "null" ] || [ -z "$BRAND_ID" ]; then
  echo "❌ 브랜드 생성 실패. 응답: $BRAND_RESPONSE"
  exit 1
fi
echo "✅ 브랜드 ID: $BRAND_ID"

echo ""
echo "=== 2. 상품 생성 (HOT 3개 + NORMAL 7개) ==="

# HOT 상품 (재고 적음)
for i in 1 2 3; do
  STOCK=$((60 - i * 10))  # 50, 30, 20
  curl -s -X POST "$BASE_URL/api-admin/v1/products" \
    -H "Content-Type: application/json" \
    -H "$ADMIN_HEADER" \
    -d "{\"brandId\": $BRAND_ID, \"name\": \"HOT Product $i\", \"price\": $((10000 * i)), \"stockQuantity\": $STOCK, \"description\": \"인기 상품 $i\"}" | jq -r '"  상품 \(.data.id): HOT Product '$i' (재고 '$STOCK')"'
done

# NORMAL 상품 (재고 충분 — k6 테스트 대상)
for i in $(seq 4 10); do
  curl -s -X POST "$BASE_URL/api-admin/v1/products" \
    -H "Content-Type: application/json" \
    -H "$ADMIN_HEADER" \
    -d "{\"brandId\": $BRAND_ID, \"name\": \"NORMAL Product $i\", \"price\": $((5000 * i)), \"stockQuantity\": 99999, \"description\": \"일반 상품 $i\"}" | jq -r '"  상품 \(.data.id): NORMAL Product '$i' (재고 99999)"'
done

echo ""
echo "=== 3. 테스트 유저 1,000명 생성 ==="

SUCCESS=0
FAIL=0
for i in $(seq 1 1000); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/v1/users" \
    -H "Content-Type: application/json" \
    -d "{\"loginId\": \"loaduser$i\", \"password\": \"Test1234!\", \"name\": \"테스트$i\", \"birthDate\": \"1990-01-01\", \"email\": \"loaduser$i@test.com\"}")
  if [ "$STATUS" = "200" ] || [ "$STATUS" = "201" ]; then
    SUCCESS=$((SUCCESS + 1))
  else
    FAIL=$((FAIL + 1))
  fi
  # 진행률 출력 (100명마다)
  if [ $((i % 100)) -eq 0 ]; then
    echo "  $i/1000 완료 (성공: $SUCCESS, 실패: $FAIL)"
  fi
done

echo ""
echo "=== 완료 ==="
echo "브랜드: $BRAND_ID"
echo "상품: HOT 3개 (재고 50/30/20), NORMAL 7개 (재고 99,999)"
echo "유저: 성공 $SUCCESS / 실패 $FAIL / 총 1,000"
echo ""
echo "k6 실행: k6 run k6/baseline-orders.js"
