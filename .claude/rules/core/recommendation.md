# Recommendation

## 코드 품질
- 재사용 가능한 객체 설계
- 기존 코드 패턴 분석 후 일관성 유지
- Java 21 모던 문법 적극 활용 (Records, Pattern Matching, Text Blocks, Sealed Class)

## 테스트
- 실제 API를 호출해 확인하는 E2E 테스트 코드 작성
- 도메인 검증은 단위 테스트로 작성 (HTTP 컨텍스트 불필요)
- Testcontainers 활용하여 실제 DB 환경에서 통합 테스트

## 문서화
- 개발 완료된 API는 `http/*.http` 파일에 분류해 작성
- 성능 최적화에 대한 대안 및 제안 제시

## 우선순위
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지