# Domain Service 테스트 예제

## 원칙
- Mock 금지, 실제 객체만 사용
- 여러 도메인 객체를 조합하는 비즈니스 로직 테스트
- 순수 Java 로직 (Infrastructure 의존 없음)

---

## Domain Service란?

Entity나 VO에 넣기 어색한 도메인 로직을 담는 순수 객체

```java
// 할인 계산은 Order가 하기엔 책임이 큼
// → Domain Service로 분리
public class DiscountCalculator {
    
    public Money calculate(Order order, List<Coupon> coupons, MemberGrade grade) {
        var couponDiscount = calculateCouponDiscount(order, coupons);
        var gradeDiscount = calculateGradeDiscount(order, grade);
        
        return couponDiscount.add(gradeDiscount);
    }
}
```

---

## 기본 테스트 구조

```java
class DiscountCalculatorTest {

    private DiscountCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DiscountCalculator();
    }

    @Nested
    class 할인_금액_계산 {

        @Test
        void 쿠폰할인_적용() {
            // given
            var order = OrderFixture.withTotalPrice(100_000);
            var coupons = List.of(CouponFixture.percentOff(10)); // 10% 할인
            var grade = MemberGrade.NORMAL;
            
            // when
            var discount = calculator.calculate(order, coupons, grade);
            
            // then
            assertThat(discount).isEqualTo(Money.of(10_000));
        }
        
        @Test
        void 등급할인_적용() {
            // given
            var order = OrderFixture.withTotalPrice(100_000);
            var coupons = List.of();
            var grade = MemberGrade.VIP; // VIP 5% 할인
            
            // when
            var discount = calculator.calculate(order, coupons, grade);
            
            // then
            assertThat(discount).isEqualTo(Money.of(5_000));
        }
        
        @Test
        void 쿠폰과_등급할인_합산() {
            // given
            var order = OrderFixture.withTotalPrice(100_000);
            var coupons = List.of(CouponFixture.percentOff(10)); // 10%
            var grade = MemberGrade.VIP; // 5%
            
            // when
            var discount = calculator.calculate(order, coupons, grade);
            
            // then
            assertThat(discount).isEqualTo(Money.of(15_000)); // 10% + 5%
        }
        
        @Test
        void 최대할인금액_초과_불가() {
            // given
            var order = OrderFixture.withTotalPrice(100_000);
            var coupons = List.of(
                CouponFixture.percentOff(50),  // 50%
                CouponFixture.percentOff(30)   // 30%
            );
            var grade = MemberGrade.VIP; // 5%
            
            // when
            var discount = calculator.calculate(order, coupons, grade);
            
            // then - 최대 50% 제한
            assertThat(discount).isEqualTo(Money.of(50_000));
        }
    }
}
```

---

## 복잡한 비즈니스 규칙 테스트

```java
public class OrderEligibilityChecker {
    
    public EligibilityResult check(Member member, Order order) {
        if (member.isBlocked()) {
            return EligibilityResult.blocked("차단된 회원");
        }
        
        if (member.hasUnpaidOrder()) {
            return EligibilityResult.rejected("미결제 주문 존재");
        }
        
        if (order.containsAgeRestrictedItem() && member.isMinor()) {
            return EligibilityResult.rejected("미성년자 구매 불가 상품");
        }
        
        if (order.getTotalPrice().isGreaterThan(member.getDailyLimit())) {
            return EligibilityResult.rejected("일일 한도 초과");
        }
        
        return EligibilityResult.eligible();
    }
}

class OrderEligibilityCheckerTest {

    private OrderEligibilityChecker checker;

    @BeforeEach
    void setUp() {
        checker = new OrderEligibilityChecker();
    }

    @Nested
    class 주문_자격_검사 {

        @Test
        void 정상_회원_정상_주문() {
            // given
            var member = MemberFixture.normal();
            var order = OrderFixture.normal();
            
            // when
            var result = checker.check(member, order);
            
            // then
            assertThat(result.isEligible()).isTrue();
        }
        
        @Test
        void 차단된_회원() {
            // given
            var member = MemberFixture.blocked();
            var order = OrderFixture.normal();
            
            // when
            var result = checker.check(member, order);
            
            // then
            assertThat(result.isEligible()).isFalse();
            assertThat(result.getReason()).contains("차단");
        }
        
        @Test
        void 미결제_주문_존재() {
            // given
            var member = MemberFixture.withUnpaidOrder();
            var order = OrderFixture.normal();
            
            // when
            var result = checker.check(member, order);
            
            // then
            assertThat(result.isEligible()).isFalse();
            assertThat(result.getReason()).contains("미결제");
        }
        
        @Test
        void 미성년자_연령제한_상품() {
            // given
            var member = MemberFixture.minor();
            var order = OrderFixture.withAlcohol();
            
            // when
            var result = checker.check(member, order);
            
            // then
            assertThat(result.isEligible()).isFalse();
            assertThat(result.getReason()).contains("미성년자");
        }
        
        @Test
        void 일일_한도_초과() {
            // given
            var member = MemberFixture.withDailyLimit(100_000);
            var order = OrderFixture.withTotalPrice(150_000);
            
            // when
            var result = checker.check(member, order);
            
            // then
            assertThat(result.isEligible()).isFalse();
            assertThat(result.getReason()).contains("한도 초과");
        }
        
        @Test
        void 검사_우선순위_차단이_먼저() {
            // given - 차단 + 한도초과 동시 만족
            var member = MemberFixture.blocked().withDailyLimit(100_000);
            var order = OrderFixture.withTotalPrice(150_000);
            
            // when
            var result = checker.check(member, order);
            
            // then - 차단이 먼저 검사됨
            assertThat(result.getReason()).contains("차단");
        }
    }
}
```

---

## 정책 분기 테스트

```java
public class ShippingFeePolicy {
    
    private static final Money FREE_SHIPPING_THRESHOLD = Money.of(50_000);
    private static final Money BASE_FEE = Money.of(3_000);
    private static final Money ISLAND_EXTRA_FEE = Money.of(3_000);
    
    public Money calculate(Order order, Address address) {
        if (order.getTotalPrice().isGreaterThanOrEqual(FREE_SHIPPING_THRESHOLD)) {
            return calculateFreeShipping(address);
        }
        return calculatePaidShipping(address);
    }
    
    private Money calculateFreeShipping(Address address) {
        // 무료배송이어도 도서산간은 추가비용
        if (address.isIsland()) {
            return ISLAND_EXTRA_FEE;
        }
        return Money.ZERO;
    }
    
    private Money calculatePaidShipping(Address address) {
        if (address.isIsland()) {
            return BASE_FEE.add(ISLAND_EXTRA_FEE);
        }
        return BASE_FEE;
    }
}

class ShippingFeePolicyTest {

    private ShippingFeePolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ShippingFeePolicy();
    }

    @Nested
    class 배송비_계산 {

        @Nested
        class 무료배송_기준_미만 {

            @Test
            void 일반지역_기본배송비() {
                var order = OrderFixture.withTotalPrice(30_000);
                var address = AddressFixture.normal();
                
                var fee = policy.calculate(order, address);
                
                assertThat(fee).isEqualTo(Money.of(3_000));
            }
            
            @Test
            void 도서산간_추가배송비() {
                var order = OrderFixture.withTotalPrice(30_000);
                var address = AddressFixture.island();
                
                var fee = policy.calculate(order, address);
                
                assertThat(fee).isEqualTo(Money.of(6_000)); // 3000 + 3000
            }
        }
        
        @Nested
        class 무료배송_기준_이상 {

            @Test
            void 일반지역_무료() {
                var order = OrderFixture.withTotalPrice(50_000);
                var address = AddressFixture.normal();
                
                var fee = policy.calculate(order, address);
                
                assertThat(fee).isEqualTo(Money.ZERO);
            }
            
            @Test
            void 도서산간_추가비용만() {
                var order = OrderFixture.withTotalPrice(50_000);
                var address = AddressFixture.island();
                
                var fee = policy.calculate(order, address);
                
                assertThat(fee).isEqualTo(Money.of(3_000)); // 도서산간 추가비만
            }
        }
        
        @Nested
        class 경계값 {

            @Test
            void _49999원_유료배송() {
                var order = OrderFixture.withTotalPrice(49_999);
                var address = AddressFixture.normal();
                
                var fee = policy.calculate(order, address);
                
                assertThat(fee).isEqualTo(Money.of(3_000));
            }
            
            @Test
            void _50000원_무료배송() {
                var order = OrderFixture.withTotalPrice(50_000);
                var address = AddressFixture.normal();
                
                var fee = policy.calculate(order, address);
                
                assertThat(fee).isEqualTo(Money.ZERO);
            }
        }
    }
}
```

---

## 시간 의존 로직 테스트

```java
public class PromotionValidator {
    
    private final Clock clock;
    
    public PromotionValidator(Clock clock) {
        this.clock = clock;
    }
    
    public boolean isActive(Promotion promotion) {
        var now = LocalDateTime.now(clock);
        return promotion.isActiveAt(now);
    }
}

class PromotionValidatorTest {

    @Test
    void 프로모션_기간_내() {
        // given
        var fixedClock = Clock.fixed(
            LocalDateTime.of(2024, 6, 15, 12, 0).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC
        );
        var validator = new PromotionValidator(fixedClock);
        
        var promotion = PromotionFixture.activeBetween(
            LocalDateTime.of(2024, 6, 1, 0, 0),
            LocalDateTime.of(2024, 6, 30, 23, 59)
        );
        
        // when & then
        assertThat(validator.isActive(promotion)).isTrue();
    }
    
    @Test
    void 프로모션_시작_전() {
        // given
        var fixedClock = Clock.fixed(
            LocalDateTime.of(2024, 5, 31, 23, 59).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC
        );
        var validator = new PromotionValidator(fixedClock);
        
        var promotion = PromotionFixture.activeBetween(
            LocalDateTime.of(2024, 6, 1, 0, 0),
            LocalDateTime.of(2024, 6, 30, 23, 59)
        );
        
        // when & then
        assertThat(validator.isActive(promotion)).isFalse();
    }
}
```

---

## 안티패턴

```java
// ❌ Repository 의존 - Domain Service가 아님
public class BadDomainService {
    private final OrderRepository orderRepository;  // Infrastructure 의존!
    
    public void process(Long orderId) {
        var order = orderRepository.findById(orderId);  // 금지!
    }
}

// ❌ Mock 사용
@Test
void bad_mock_사용() {
    var order = mock(Order.class);
    when(order.getTotalPrice()).thenReturn(Money.of(50_000));  // 금지!
}

// ❌ 외부 서비스 호출
public class BadDomainService {
    private final PaymentClient paymentClient;  // 외부 API 의존!
    
    public void process(Order order) {
        paymentClient.pay(order);  // 금지! Application Service에서 해야 함
    }
}
```