# Project Conventions (기존 코드 패턴)

## API Response
- 성공: ApiResponse.success(data)
- 실패: ApiResponse.fail(errorType)

## Exception
- throw new CoreException(ErrorType.NOT_FOUND)
- throw new CoreException(ErrorType.BAD_REQUEST, "Custom message")

## ErrorType 종류
- INTERNAL_ERROR (500), BAD_REQUEST (400), NOT_FOUND (404), CONFLICT (409)

## Feature 생성 순서
1. Controller (interfaces/api/) — *ApiSpec 인터페이스 + *Controller 구현
2. Facade (application/) — orchestration + *Info records
3. Service (domain/) — 비즈니스 로직
4. Repository (domain/) — 인터페이스 정의
5. Repository Impl (infrastructure/) — JPA 구현

## 주의사항
- open-in-view disabled (Controller에서 lazy loading 금지)
- Timezone: Asia/Seoul
- Soft Delete: deletedAt 필드 사용, hard delete 금지
- Hibernate DDL은 production에서 none (스키마 변경은 migration으로만)
- QueryDSL Q-class 경로: build/generated/sources/annotationProcessor