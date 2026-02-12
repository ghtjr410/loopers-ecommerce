# Enum 테스트 예제

## 원칙
- `values()` 존재 여부 테스트 ❌ (언어 스펙)
- 비즈니스 로직이 있는 메서드만 테스트 ✅
- 상태 전이, 그룹화, 매핑 로직 테스트 ✅

---

## 테스트 대상 vs 제외 대상

```java
public enum OrderStatus {
    CREATED("주문생성"),
    PAID("결제완료"),
    SHIPPING("배송중"),
    DELIVERED("배송완료"),
    CANCELLED("취소됨");
    
    private final String description;
    
    // ❌ 테스트 제외: 단순 getter
    public String getDescription() {
        return description;
    }
    
    // ✅ 테스트 대상: 비즈니스 로직
    public boolean canCancel() {
        return this == CREATED || this == PAID;
    }
    
    // ✅ 테스트 대상: 상태 전이 검증
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case CREATED -> next == PAID || next == CANCELLED;
            case PAID -> next == SHIPPING || next == CANCELLED;
            case SHIPPING -> next == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
    
    // ✅ 테스트 대상: 그룹화
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
    
    // ✅ 테스트 대상: 외부 코드 매핑
    public static OrderStatus fromExternalCode(String code) {
        return switch (code) {
            case "ORD_NEW" -> CREATED;
            case "ORD_PAID" -> PAID;
            case "ORD_SHIP" -> SHIPPING;
            case "ORD_DONE" -> DELIVERED;
            case "ORD_CANCEL" -> CANCELLED;
            default -> throw new IllegalArgumentException("Unknown code: " + code);
        };
    }
}
```

---

## 상태 전이 테스트

```java
class OrderStatusTest {

    @Nested
    class 상태_전이_가능_여부 {

        @Test
        void CREATED에서_PAID_가능() {
            assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.PAID)).isTrue();
        }
        
        @Test
        void CREATED에서_CANCELLED_가능() {
            assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        }
        
        @Test
        void CREATED에서_SHIPPING_불가() {
            assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.SHIPPING)).isFalse();
        }
        
        @Test
        void DELIVERED에서_어디로도_전이_불가() {
            for (OrderStatus next : OrderStatus.values()) {
                assertThat(OrderStatus.DELIVERED.canTransitionTo(next)).isFalse();
            }
        }
        
        // 또는 ParameterizedTest 활용
        @ParameterizedTest
        @CsvSource({
            "CREATED, PAID, true",
            "CREATED, CANCELLED, true",
            "CREATED, SHIPPING, false",
            "PAID, SHIPPING, true",
            "PAID, CANCELLED, true",
            "SHIPPING, DELIVERED, true",
            "SHIPPING, CANCELLED, false",
            "DELIVERED, CREATED, false",
            "CANCELLED, CREATED, false"
        })
        void 상태전이_매트릭스(
                OrderStatus from, 
                OrderStatus to, 
                boolean expected) {
            assertThat(from.canTransitionTo(to)).isEqualTo(expected);
        }
    }
}
```

---

## 비즈니스 정책 테스트

```java
class OrderStatusTest {

    @Nested
    class 취소_가능_여부 {

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"CREATED", "PAID"})
        void 취소_가능한_상태(OrderStatus status) {
            assertThat(status.canCancel()).isTrue();
        }
        
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"SHIPPING", "DELIVERED", "CANCELLED"})
        void 취소_불가능한_상태(OrderStatus status) {
            assertThat(status.canCancel()).isFalse();
        }
    }

    @Nested
    class 종료_상태_여부 {

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED"})
        void 종료_상태(OrderStatus status) {
            assertThat(status.isTerminal()).isTrue();
        }
        
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"CREATED", "PAID", "SHIPPING"})
        void 진행중_상태(OrderStatus status) {
            assertThat(status.isTerminal()).isFalse();
        }
    }
}
```

---

## 매핑/변환 테스트

```java
class OrderStatusTest {

    @Nested
    class 외부_코드_변환 {

        @ParameterizedTest
        @CsvSource({
            "ORD_NEW, CREATED",
            "ORD_PAID, PAID",
            "ORD_SHIP, SHIPPING",
            "ORD_DONE, DELIVERED",
            "ORD_CANCEL, CANCELLED"
        })
        void 정상_변환(String code, OrderStatus expected) {
            assertThat(OrderStatus.fromExternalCode(code)).isEqualTo(expected);
        }
        
        @Test
        void 알수없는_코드는_예외() {
            assertThatThrownBy(() -> OrderStatus.fromExternalCode("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown code");
        }
        
        @Test
        void null은_예외() {
            assertThatThrownBy(() -> OrderStatus.fromExternalCode(null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
```

---

## 그룹화 테스트

```java
public enum PaymentMethod {
    CREDIT_CARD("신용카드"),
    DEBIT_CARD("체크카드"),
    BANK_TRANSFER("계좌이체"),
    VIRTUAL_ACCOUNT("가상계좌"),
    KAKAO_PAY("카카오페이"),
    NAVER_PAY("네이버페이"),
    TOSS_PAY("토스페이");
    
    private final String description;
    
    // ✅ 테스트 대상: 그룹화 로직
    public boolean isCard() {
        return this == CREDIT_CARD || this == DEBIT_CARD;
    }
    
    public boolean isSimplePay() {
        return this == KAKAO_PAY || this == NAVER_PAY || this == TOSS_PAY;
    }
    
    public boolean requiresCallback() {
        return this == VIRTUAL_ACCOUNT || isSimplePay();
    }
}

class PaymentMethodTest {

    @Nested
    class 카드_결제_여부 {

        @ParameterizedTest
        @EnumSource(value = PaymentMethod.class, names = {"CREDIT_CARD", "DEBIT_CARD"})
        void 카드_결제(PaymentMethod method) {
            assertThat(method.isCard()).isTrue();
        }
        
        @ParameterizedTest
        @EnumSource(value = PaymentMethod.class, names = {"CREDIT_CARD", "DEBIT_CARD"}, mode = EnumSource.Mode.EXCLUDE)
        void 카드_외_결제(PaymentMethod method) {
            assertThat(method.isCard()).isFalse();
        }
    }

    @Nested
    class 콜백_필요_여부 {

        @ParameterizedTest
        @EnumSource(value = PaymentMethod.class, names = {"VIRTUAL_ACCOUNT", "KAKAO_PAY", "NAVER_PAY", "TOSS_PAY"})
        void 콜백_필요(PaymentMethod method) {
            assertThat(method.requiresCallback()).isTrue();
        }
    }
}
```

---

## 새 값 추가 시 테스트 누락 방지

```java
class OrderStatusTest {

    /**
     * 새로운 상태가 추가되면 이 테스트가 실패하여
     * canCancel() 로직 검토를 강제함
     */
    @Test
    void 모든_상태_커버_확인() {
        var testedStatuses = Set.of(
            OrderStatus.CREATED,     // canCancel: true
            OrderStatus.PAID,        // canCancel: true
            OrderStatus.SHIPPING,    // canCancel: false
            OrderStatus.DELIVERED,   // canCancel: false
            OrderStatus.CANCELLED    // canCancel: false
        );
        
        var allStatuses = Set.of(OrderStatus.values());
        
        assertThat(testedStatuses)
            .as("새 상태 추가 시 canCancel 테스트 업데이트 필요")
            .containsExactlyInAnyOrderElementsOf(allStatuses);
    }
}
```

---

## 안티패턴

```java
// ❌ values() 존재 테스트 - 언어 스펙
@Test
void bad_values_테스트() {
    assertThat(OrderStatus.values()).hasSize(5);
}

// ❌ valueOf 테스트 - 언어 스펙  
@Test
void bad_valueOf_테스트() {
    assertThat(OrderStatus.valueOf("CREATED")).isEqualTo(OrderStatus.CREATED);
}

// ❌ name() 테스트 - 언어 스펙
@Test
void bad_name_테스트() {
    assertThat(OrderStatus.CREATED.name()).isEqualTo("CREATED");
}

// ❌ 단순 getter 테스트
@Test
void bad_description_테스트() {
    assertThat(OrderStatus.CREATED.getDescription()).isEqualTo("주문생성");
}

// ❌ ordinal 테스트 - 순서 의존성 만듦
@Test
void bad_ordinal_테스트() {
    assertThat(OrderStatus.CREATED.ordinal()).isEqualTo(0);
}
```