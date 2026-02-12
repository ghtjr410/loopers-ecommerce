# VO (Value Object) 테스트 예제

## 원칙
- 생성 시 검증 로직 테스트
- 불변성 보장 확인
- 동등성(equals/hashCode) 검증은 record 사용 시 생략
- 경계값, null, empty, blank 검증

---

## Money (금액)

```java
class MoneyTest {

    @Nested
    class 생성 {

        @Test
        void 정상_생성() {
            var money = Money.of(10_000);
            
            assertThat(money.getValue()).isEqualTo(10_000);
        }
        
        @Test
        void _0원_허용() {
            var money = Money.of(0);
            
            assertThat(money.getValue()).isEqualTo(0);
        }
        
        @Test
        void 음수는_예외() {
            assertThatThrownBy(() -> Money.of(-1))
                .isInstanceOf(InvalidMoneyException.class)
                .hasMessageContaining("음수");
        }
        
        @Test
        void null은_예외() {
            assertThatThrownBy(() -> Money.of(null))
                .isInstanceOf(InvalidMoneyException.class);
        }
    }

    @Nested
    class 덧셈 {

        @Test
        void 정상_덧셈() {
            var money1 = Money.of(10_000);
            var money2 = Money.of(5_000);
            
            var result = money1.add(money2);
            
            assertThat(result.getValue()).isEqualTo(15_000);
        }
        
        @Test
        void 불변성_보장() {
            var original = Money.of(10_000);
            var other = Money.of(5_000);
            
            original.add(other);
            
            // 원본은 변경되지 않음
            assertThat(original.getValue()).isEqualTo(10_000);
        }
        
        @Test
        void null이면_예외() {
            var money = Money.of(10_000);

            assertThatThrownBy(() -> money.add(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class 뺄셈 {

        @Test
        void 정상_뺄셈() {
            var money1 = Money.of(10_000);
            var money2 = Money.of(3_000);
            
            var result = money1.subtract(money2);
            
            assertThat(result.getValue()).isEqualTo(7_000);
        }
        
        @Test
        void 결과가_음수면_예외() {
            var money1 = Money.of(3_000);
            var money2 = Money.of(5_000);

            assertThatThrownBy(() -> money1.subtract(money2))
                .isInstanceOf(InsufficientMoneyException.class);
        }
    }

    @Nested
    class 곱셈 {

        @Test
        void 정상_곱셈() {
            var money = Money.of(10_000);
            
            var result = money.multiply(3);
            
            assertThat(result.getValue()).isEqualTo(30_000);
        }
        
        @Test
        void _0배는_0원() {
            var money = Money.of(10_000);
            
            var result = money.multiply(0);
            
            assertThat(result).isEqualTo(Money.ZERO);
        }
        
        @Test
        void 음수배는_예외() {
            var money = Money.of(10_000);

            assertThatThrownBy(() -> money.multiply(-1))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class 비교 {

        @Test
        void 크면_true() {
            var money1 = Money.of(10_000);
            var money2 = Money.of(5_000);
            
            assertThat(money1.isGreaterThan(money2)).isTrue();
        }
        
        @Test
        void 같거나_크면_true() {
            var money1 = Money.of(10_000);
            var money2 = Money.of(10_000);
            
            assertThat(money1.isGreaterThanOrEqual(money2)).isTrue();
        }
        
        @Test
        void 작으면_true() {
            var money1 = Money.of(5_000);
            var money2 = Money.of(10_000);
            
            assertThat(money1.isLessThan(money2)).isTrue();
        }
    }
}
```

---

## Email

```java
class EmailTest {

    @Nested
    class 생성 {

        @Test
        void 정상_이메일() {
            var email = Email.of("test@example.com");
            
            assertThat(email.getValue()).isEqualTo("test@example.com");
        }
        
        @ParameterizedTest
        @ValueSource(strings = {
            "user@domain.com",
            "user.name@domain.com",
            "user+tag@domain.co.kr",
            "user@sub.domain.com"
        })
        void 유효한_형식(String value) {
            assertThatCode(() -> Email.of(value))
                .doesNotThrowAnyException();
        }
        
        @ParameterizedTest
        @ValueSource(strings = {
            "invalid",
            "@domain.com",
            "user@",
            "user@.com",
            "user@domain",
            ""
        })
        void 유효하지_않은_형식(String value) {
            assertThatThrownBy(() -> Email.of(value))
                .isInstanceOf(InvalidEmailException.class);
        }
        
        @Test
        void null이면_예외() {
            assertThatThrownBy(() -> Email.of(null))
                .isInstanceOf(InvalidEmailException.class);
        }

        @Test
        void 공백이면_예외() {
            assertThatThrownBy(() -> Email.of("   "))
                .isInstanceOf(InvalidEmailException.class);
        }
    }

    @Nested
    class 도메인_추출 {

        @Test
        void 도메인_반환() {
            var email = Email.of("user@example.com");
            
            assertThat(email.getDomain()).isEqualTo("example.com");
        }
    }

    @Nested
    class 로컬_파트_추출 {

        @Test
        void 로컬파트_반환() {
            var email = Email.of("user@example.com");
            
            assertThat(email.getLocalPart()).isEqualTo("user");
        }
    }
}
```

---

## PhoneNumber

```java
class PhoneNumberTest {

    @Nested
    class 생성 {

        @ParameterizedTest
        @ValueSource(strings = {
            "010-1234-5678",
            "01012345678",
            "010 1234 5678"
        })
        void 다양한_형식_허용(String value) {
            var phone = PhoneNumber.of(value);
            
            // 정규화된 형식으로 저장
            assertThat(phone.getValue()).isEqualTo("01012345678");
        }
        
        @ParameterizedTest
        @ValueSource(strings = {
            "02-123-4567",   // 지역번호
            "1588-1234",     // 대표번호
            "12345",         // 너무 짧음
            "abc-defg-hijk"  // 문자
        })
        void 휴대폰_형식_아니면_예외(String value) {
            assertThatThrownBy(() -> PhoneNumber.of(value))
                .isInstanceOf(InvalidPhoneNumberException.class);
        }
        
        @Test
        void null이면_예외() {
            assertThatThrownBy(() -> PhoneNumber.of(null))
                .isInstanceOf(InvalidPhoneNumberException.class);
        }
    }

    @Nested
    class 포맷팅 {

        @Test
        void 하이픈_포함_형식() {
            var phone = PhoneNumber.of("01012345678");
            
            assertThat(phone.formatWithHyphen()).isEqualTo("010-1234-5678");
        }
        
        @Test
        void 마스킹_형식() {
            var phone = PhoneNumber.of("01012345678");
            
            assertThat(phone.formatMasked()).isEqualTo("010-****-5678");
        }
    }
}
```

---

## DateRange (기간)

```java
class DateRangeTest {

    @Nested
    class 생성 {

        @Test
        void 정상_생성() {
            var start = LocalDate.of(2024, 1, 1);
            var end = LocalDate.of(2024, 12, 31);
            
            var range = DateRange.of(start, end);
            
            assertThat(range.getStart()).isEqualTo(start);
            assertThat(range.getEnd()).isEqualTo(end);
        }
        
        @Test
        void 시작일과_종료일_같아도_허용() {
            var date = LocalDate.of(2024, 1, 1);
            
            var range = DateRange.of(date, date);
            
            assertThat(range.getDays()).isEqualTo(1);
        }
        
        @Test
        void 시작일이_종료일보다_늦으면_예외() {
            var start = LocalDate.of(2024, 12, 31);
            var end = LocalDate.of(2024, 1, 1);
            
            assertThatThrownBy(() -> DateRange.of(start, end))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("시작일이 종료일보다 늦을 수 없습니다");
        }
    }

    @Nested
    class 포함_여부 {

        @Test
        void 범위_내_날짜() {
            var range = DateRange.of(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31)
            );
            
            assertThat(range.contains(LocalDate.of(2024, 6, 15))).isTrue();
        }
        
        @Test
        void 시작일_포함() {
            var range = DateRange.of(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31)
            );
            
            assertThat(range.contains(LocalDate.of(2024, 1, 1))).isTrue();
        }
        
        @Test
        void 종료일_포함() {
            var range = DateRange.of(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31)
            );
            
            assertThat(range.contains(LocalDate.of(2024, 12, 31))).isTrue();
        }
        
        @Test
        void 범위_밖_날짜() {
            var range = DateRange.of(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31)
            );
            
            assertThat(range.contains(LocalDate.of(2025, 1, 1))).isFalse();
        }
    }

    @Nested
    class 겹침_여부 {

        @Test
        void 부분_겹침() {
            var range1 = DateRange.of(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 6, 30)
            );
            var range2 = DateRange.of(
                LocalDate.of(2024, 4, 1),
                LocalDate.of(2024, 12, 31)
            );
            
            assertThat(range1.overlaps(range2)).isTrue();
        }
        
        @Test
        void 겹치지_않음() {
            var range1 = DateRange.of(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 3, 31)
            );
            var range2 = DateRange.of(
                LocalDate.of(2024, 4, 1),
                LocalDate.of(2024, 12, 31)
            );
            
            assertThat(range1.overlaps(range2)).isFalse();
        }
    }
}
```

---

## 안티패턴

```java
// ❌ equals/hashCode 테스트 (record 사용 시 불필요)
@Test
void bad_record_equals_테스트() {
    var money1 = Money.of(10_000);
    var money2 = Money.of(10_000);
    
    assertThat(money1).isEqualTo(money2);  // record는 자동 생성, 테스트 불필요
}

// ❌ toString 테스트
@Test
void bad_toString_테스트() {
    var email = Email.of("test@example.com");
    
    assertThat(email.toString()).contains("test@example.com");  // 의미 없음
}

// ❌ getter만 테스트
@Test
void bad_getter만_테스트() {
    var money = Money.of(10_000);
    
    assertThat(money.getValue()).isEqualTo(10_000);  // 생성 검증과 중복
}
```