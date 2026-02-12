# DTO Patterns

## Record 사용 원칙
- 모든 DTO는 Java record로 작성 (불변성 보장)
- Request/Response는 버전별 DTO 컨테이너 내부에 정의
- Info는 application 계층에 독립 record로 정의

## DTO 컨테이너 구조
```java
public class UserV1Dto {
    public record SignUpRequest(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
    ) {}

    public record UserResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.loginId(),
                info.maskedName(),
                info.birthDate(),
                info.email()
            );
        }
    }
}
```

## 데이터 흐름
```
Request → Controller (원시값 추출) → Service → Domain → Info → Response
```

## Request DTO 검증
- Bean Validation 사용 허용: `@NotNull`, `@NotBlank` 등
- 입력값 형식 검증 담당 (도메인 불변식 검증과 관심사 분리)
- Controller에서 `@Valid` 사용

## 금지 사항
- DTO가 Domain Entity를 직접 import 금지
- dto.toEntity() 패턴 금지 (Controller에서 원시값 전달)
- 비즈니스 규칙 검증을 DTO에서 수행 금지 (도메인 역할)
