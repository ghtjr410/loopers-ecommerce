# Design Principles

## 핵심 원칙
- SRP (Single Responsibility Principle) 무조건 준수
- OCP (Open/Closed Principle) 준수
- 현재 요구사항에 집중하되, 구조는 유연하게

## 입력값 검증 전략

### 2단계 검증 (관심사 분리)
| 단계 | 계층 | 검증 대상 | 예시       |
|------|------|----------|----------|
| 1단계 | Request DTO | 입력값 형식 | 필수값, 타입  |
| 2단계 | Domain Entity | 도메인 불변식 | 비즈니스 규칙  |

### Request DTO 검증 (입력값 형식)
- Bean Validation 사용 허용: `@NotNull`, `@NotBlank`, `@NotEmpty`
- 목적: "값이 존재하는가? 기본 형식에 맞는가?"
- Controller에서 `@Valid` 사용

### Domain Entity 검증 (도메인 불변식)
- 도메인 객체가 자기 불변식을 스스로 검증
- 목적: "비즈니스 규칙에 맞는가?"
- 정적 팩토리 메서드(`create`)에서 검증
- cross-field 검증(필드 간 교차 검증)은 도메인에서 처리

## 중복 체크 전략
- 비즈니스 중복 체크는 Service에서 exists 쿼리로 명시적 수행
- DB unique constraint는 스키마 레벨에서 반드시 설정 (최종 방어선)
- DataIntegrityViolationException은 글로벌 예외 핸들러에서 공통 처리
- Service에서 try-catch로 DataIntegrityViolationException을 잡지 않는다

## 계층 간 데이터 전달
- Controller → Service: 원시값 파라미터로 전달 (dto.toEntity 방식 사용하지 않음)
- Service → Controller: Result/Info 객체 사용
- DTO가 Domain Entity를 직접 알지 않는다
- 암호화 등 외부 의존성이 필요한 변환은 Service에서 수행