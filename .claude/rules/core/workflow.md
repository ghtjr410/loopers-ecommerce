# Workflow

## 대원칙 - 증강 코딩
- 방향성 및 주요 의사 결정은 개발자에게 제안만 하고, 최종 승인된 사항을 기반으로 작업 수행
- AI가 임의판단하지 않고, 제안 후 개발자 승인을 받은 뒤 수행
- 요청하지 않은 기능 구현, 테스트 삭제, 반복적 동작 시 즉시 중단하고 보고

## 구현 워크플로우 (하향식)

PRD + AC가 명세 역할을 하므로, **AC ↔ 테스트 1:1 매핑 검증**이 핵심

### 구현 순서 (하향식)

```
Controller → Facade → Service → Repository → Entity
```

1. **Controller + DTO** - API 인터페이스 정의
2. **Facade + Info** - 오케스트레이션
3. **Service** - 비즈니스 로직
4. **Repository** - 인터페이스 + 구현체
5. **Entity** - 도메인 모델

### 테스트 작성

- 구현과 테스트를 **동시에** 작성
- 모든 테스트는 3A 원칙: Arrange - Act - Assert
- AC와 테스트 1:1 매핑 필수

| 계층 | 테스트 유형 |
|------|-----------|
| Entity | 단위 테스트 |
| Service | 통합 테스트 |
| Controller | E2E 테스트 |

### Refactor Phase
- 불필요한 코드 제거, 객체지향적 구조 개선
- unused import 제거
- 모든 테스트 케이스가 통과해야 함