---
paths:
  - "**/domain/**/*.java"
---
# Domain Layer Rules

## Entity 설계
- 정적 팩토리 메서드로 생성 (public 생성자 금지)
- JPA용 protected 기본 생성자 필수
- 생성 시점에 모든 불변식 검증 수행
- 검증 실패 시 IllegalArgumentException 던지기
- 검증 정규식은 상수(private static final Pattern)로 선언

## 검증 범위
- null/blank 체크
- 형식 검증 (이메일, 로그인ID 패턴 등)
- 비즈니스 규칙 (cross-field 검증 포함)
- 미래 날짜 불가 등 논리적 제약

## 의존성
- 도메인 객체는 Spring Bean을 필드 주입받지 않는다
- 도메인 불변식에 필요한 외부 기능은 도메인 패키지에 인터페이스를 정의하고,
  메서드 파라미터로 전달받는다 (예: PasswordEncoder)