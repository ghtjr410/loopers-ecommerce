# Never Do

- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현 금지
- null-safety 하지 않은 코드 작성 금지 (Optional 활용)
- System.out.println 코드 남기지 않기
- 테스트를 임의로 삭제하거나 @Disabled 처리 금지
- 요청하지 않은 기능을 선제적으로 구현하지 않기
- 과도한 미래 예측 기반 설계 금지 (YAGNI 준수)
- Service 간 직접 호출 금지 (순환 참조 방지)