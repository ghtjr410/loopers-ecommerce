---
paths:
  - "**/application/**/*.java"
  - "**/service/**/*.java"
---
# Service Layer Rules

## 메서드 흐름
1. 비즈니스 중복 체크 등 IO 수행
2. 암호화 등 외부 의존성 처리
3. Repository 저장/조회

## 의존성 규칙
- Service 간 직접 호출 금지 (순환 참조 방지)
- 여러 Service 조합이 필요하면 Facade 패턴 사용
- Repository, PasswordEncoder 등 인프라 의존성 주입 가능

## 반환
- Result/Info 객체(record)로 반환
- Entity를 Controller에 직접 노출하지 않음