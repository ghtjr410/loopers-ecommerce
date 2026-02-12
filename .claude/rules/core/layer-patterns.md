# Layer Patterns

## 패키지 구조
```
com.loopers/
├── interfaces/api/{domain}/     # REST API
├── application/{domain}/        # Facade, Info
├── domain/{domain}/             # Entity, Service, Repository 인터페이스
└── infrastructure/{domain}/     # Repository 구현, 외부 어댑터
```

## 계층별 역할

### Controller (interfaces)
- HTTP 요청/응답 변환만 담당
- Request에서 원시값 추출하여 Facade에 전달
- try-catch 금지 (글로벌 핸들러가 처리)
- `@Valid`로 Request DTO 입력값 형식 검증

### Facade (application)
- Service 호출 오케스트레이션
- Domain Entity → Info 변환
- 트랜잭션 경계 아님 (Service에서 관리)

### Service (domain)
- 비즈니스 로직 담당
- 중복 체크는 exists 쿼리로 명시적 수행
- `@Transactional(readOnly = true)` 기본, 쓰기만 `@Transactional`

### Repository (domain → infrastructure)
- 인터페이스는 domain 패키지에 정의
- 구현체는 infrastructure 패키지에 배치

## 의존성 방향
```
interfaces → application → domain ← infrastructure
```
- Domain은 Infrastructure를 알지 않음
- 외부 의존성은 인터페이스로 추상화 (예: PasswordEncoder)
