# Entity 테스트 예제

## 원칙
- Mock 금지, 실제 객체만 사용
- 비즈니스 정책/상태 전이 검증에 집중
- 생성 규칙, 불변식 위반 검증

---

## 기본 구조

```java
class OrderTest {

    @Nested
    class 생성 {

        @Test
        void 성공() {
            // given
            var member = MemberFixture.adult();
            var orderItems = OrderItemsFixture.withTotalPrice(50_000);
            
            // when
            var order = Order.create(member, orderItems);
            
            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getMemberId()).isEqualTo(member.getId());
        }
        
        @Test
        void 최소주문금액_미만이면_예외() {
            // given
            var member = MemberFixture.adult();
            var orderItems = OrderItemsFixture.withTotalPrice(9_000); // 최소 10,000원
            
            // when & then
            assertThatThrownBy(() -> Order.create(member, orderItems))
                .isInstanceOf(OrderMinimumAmountException.class)
                .hasMessageContaining("최소 주문 금액");
        }
        
        @Test
        void 미성년자는_주류_주문_불가() {
            // given
            var minor = MemberFixture.minor();
            var orderItems = OrderItemsFixture.withAlcohol();
            
            // when & then
            assertThatThrownBy(() -> Order.create(minor, orderItems))
                .isInstanceOf(AgeRestrictionException.class);
        }
    }

    @Nested
    class 취소 {

        @Test
        void 성공() {
            // given
            var order = OrderFixture.created();
            
            // when
            order.cancel("고객 변심");
            
            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getCancelReason()).isEqualTo("고객 변심");
        }
        
        @Test
        void 이미_배송중이면_취소_불가() {
            // given
            var order = OrderFixture.shipping();
            
            // when & then
            assertThatThrownBy(() -> order.cancel("고객 변심"))
                .isInstanceOf(OrderCannotCancelException.class)
                .hasMessageContaining("배송 중");
        }
        
        @Test
        void 이미_취소된_주문은_재취소_불가() {
            // given
            var order = OrderFixture.cancelled();
            
            // when & then
            assertThatThrownBy(() -> order.cancel("중복 취소"))
                .isInstanceOf(OrderAlreadyCancelledException.class);
        }
    }

    @Nested
    class 취소_가능_여부_판단 {

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"CREATED", "PAID"})
        void 취소_가능한_상태(OrderStatus status) {
            // given
            var order = OrderFixture.withStatus(status);
            
            // when & then
            assertThat(order.canCancel()).isTrue();
        }
        
        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"SHIPPING", "DELIVERED", "CANCELLED"})
        void 취소_불가능한_상태(OrderStatus status) {
            // given
            var order = OrderFixture.withStatus(status);
            
            // when & then
            assertThat(order.canCancel()).isFalse();
        }
    }
}
```

---

## 경계값 테스트

```java
@Nested
class 배송비_계산 {

    @Test
    void _5만원_미만은_배송비_3000원() {
        // given
        var order = OrderFixture.withTotalPrice(49_999);
        
        // when
        var fee = order.calculateDeliveryFee();
        
        // then
        assertThat(fee).isEqualTo(Money.of(3_000));
    }
    
    @Test
    void _5만원_이상은_무료() {
        // given
        var order = OrderFixture.withTotalPrice(50_000);
        
        // when
        var fee = order.calculateDeliveryFee();
        
        // then
        assertThat(fee).isEqualTo(Money.ZERO);
    }
    
    @Test
    void 경계값_49999원() {
        var order = OrderFixture.withTotalPrice(49_999);
        assertThat(order.calculateDeliveryFee()).isEqualTo(Money.of(3_000));
    }

    @Test
    void 경계값_50000원() {
        var order = OrderFixture.withTotalPrice(50_000);
        assertThat(order.calculateDeliveryFee()).isEqualTo(Money.ZERO);
    }
}
```

---

## 상태 전이 테스트

```java
@Nested
class 상태_전이 {

    @Test
    void 주문생성_결제완료_배송시작_배송완료_정상흐름() {
        // given
        var order = OrderFixture.created();
        
        // when & then - 상태 전이 순서대로 검증
        order.pay(PaymentInfo.of("card", 50_000));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        
        order.startShipping(TrackingNumber.of("1234567890"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        
        order.completeDelivery();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }
    
    @Test
    void 결제전_배송시작_불가() {
        // given
        var order = OrderFixture.created(); // CREATED 상태
        
        // when & then
        assertThatThrownBy(() -> order.startShipping(TrackingNumber.of("123")))
            .isInstanceOf(InvalidOrderStateException.class)
            .hasMessageContaining("결제 완료 후");
    }
}
```

---

## Fixture 예시

```java
public class OrderFixture {
    
    public static Order created() {
        return Order.create(
            MemberFixture.adult(),
            OrderItemsFixture.default()
        );
    }
    
    public static Order paid() {
        var order = created();
        order.pay(PaymentInfo.of("card", 50_000));
        return order;
    }
    
    public static Order shipping() {
        var order = paid();
        order.startShipping(TrackingNumber.of("1234567890"));
        return order;
    }
    
    public static Order cancelled() {
        var order = created();
        order.cancel("테스트 취소");
        return order;
    }
    
    public static Order withStatus(OrderStatus status) {
        return switch (status) {
            case CREATED -> created();
            case PAID -> paid();
            case SHIPPING -> shipping();
            case DELIVERED -> { 
                var order = shipping();
                order.completeDelivery();
                yield order;
            }
            case CANCELLED -> cancelled();
        };
    }
    
    public static Order withTotalPrice(int price) {
        return Order.create(
            MemberFixture.adult(),
            OrderItemsFixture.withTotalPrice(price)
        );
    }
}
```

---

## 안티패턴

```java
// ❌ Mock 사용
@Test
void bad_mock_사용() {
    var member = mock(Member.class);
    when(member.isAdult()).thenReturn(true);  // 금지!
    
    var order = Order.create(member, items);
}

// ❌ 구현 세부사항 검증
@Test
void bad_내부_필드_직접_검증() {
    var order = Order.create(member, items);
    
    // 내부 구현에 의존
    assertThat(order).extracting("internalState").isEqualTo("INIT");
}

// ❌ 단순 getter 테스트
@Test
void bad_getter_테스트() {
    var order = Order.create(member, items);
    assertThat(order.getMemberId()).isNotNull(); // 의미 없음
}
```