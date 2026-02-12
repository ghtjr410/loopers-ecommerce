# Application Service 통합 테스트 예제

## 원칙
- 실제 DB 사용 (TestContainers)
- Service → Domain → Repository → DB 전체 흐름 검증
- 외부 연동만 Fake 구현체 사용
- 내부 구현 모르는 블랙박스 테스트
- 리팩터링 내성 확보

---

## 기본 설정

### 테스트 Base 클래스

```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestSupport {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
}
```

### application-test.yml

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

---

## 기본 통합 테스트 구조

```java
class OrderCommandServiceIT extends IntegrationTestSupport {

    @Autowired
    private OrderCommandService orderService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Nested
    class 주문_생성 {

        @Test
        void 성공() {
            // given
            var member = memberRepository.save(MemberFixture.adult());
            var product = productRepository.save(ProductFixture.normal());
            
            var command = CreateOrderCommand.of(
                member.getId(),
                List.of(OrderItemCommand.of(product.getId(), 2))
            );
            
            // when
            var result = orderService.createOrder(command);
            
            // then
            assertThat(result.orderId()).isNotNull();
            assertThat(result.status()).isEqualTo(OrderStatus.CREATED);
            
            // DB 확인
            var savedOrder = orderRepository.findById(result.orderId()).orElseThrow();
            assertThat(savedOrder.getMemberId()).isEqualTo(member.getId());
            assertThat(savedOrder.getOrderItems()).hasSize(1);
        }
        
        @Test
        void 재고부족_실패() {
            // given
            var member = memberRepository.save(MemberFixture.adult());
            var product = productRepository.save(ProductFixture.withStock(5));
            
            var command = CreateOrderCommand.of(
                member.getId(),
                List.of(OrderItemCommand.of(product.getId(), 10)) // 재고보다 많이 주문
            );
            
            // when & then
            assertThatThrownBy(() -> orderService.createOrder(command))
                .isInstanceOf(InsufficientStockException.class);
        }
        
        @Test
        void 존재하지않는_회원() {
            // given
            var product = productRepository.save(ProductFixture.normal());
            
            var command = CreateOrderCommand.of(
                999L,  // 존재하지 않는 회원 ID
                List.of(OrderItemCommand.of(product.getId(), 1))
            );
            
            // when & then
            assertThatThrownBy(() -> orderService.createOrder(command))
                .isInstanceOf(MemberNotFoundException.class);
        }
        
        @Test
        void 최소주문금액_미달() {
            // given
            var member = memberRepository.save(MemberFixture.adult());
            var product = productRepository.save(ProductFixture.withPrice(1_000));
            
            var command = CreateOrderCommand.of(
                member.getId(),
                List.of(OrderItemCommand.of(product.getId(), 1)) // 1,000원 (최소 10,000원)
            );
            
            // when & then
            assertThatThrownBy(() -> orderService.createOrder(command))
                .isInstanceOf(OrderMinimumAmountException.class);
        }
    }

    @Nested
    class 주문_취소 {

        @Test
        void 성공() {
            // given
            var order = createAndSaveOrder(OrderStatus.CREATED);
            var command = CancelOrderCommand.of(order.getId(), "고객 변심");
            
            // when
            var result = orderService.cancelOrder(command);
            
            // then
            assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
            
            // 재고 복구 확인
            var product = productRepository.findById(order.getFirstProductId()).orElseThrow();
            assertThat(product.getStock()).isEqualTo(10); // 원래 재고로 복구
        }
        
        @Test
        void 배송중_실패() {
            // given
            var order = createAndSaveOrder(OrderStatus.SHIPPING);
            var command = CancelOrderCommand.of(order.getId(), "고객 변심");
            
            // when & then
            assertThatThrownBy(() -> orderService.cancelOrder(command))
                .isInstanceOf(OrderCannotCancelException.class)
                .hasMessageContaining("배송 중");
        }
    }
    
    private Order createAndSaveOrder(OrderStatus status) {
        var member = memberRepository.save(MemberFixture.adult());
        var product = productRepository.save(ProductFixture.withStock(10));
        
        var order = Order.create(member, OrderItems.of(
            List.of(OrderItem.of(product, 2))
        ));
        
        if (status == OrderStatus.SHIPPING) {
            order.pay(PaymentInfo.card(order.getTotalPrice()));
            order.startShipping(TrackingNumber.of("1234567890"));
        }
        
        return orderRepository.save(order);
    }
}
```

---

## 트랜잭션 경계 테스트

```java
@Nested
class 트랜잭션_롤백 {

    @Test
    void 결제실패시_주문상태_롤백() {
        // given
        var order = createAndSaveOrder(OrderStatus.CREATED);
        
        // FakePaymentClient가 실패 응답 반환하도록 설정
        fakePaymentClient.willFail();
        
        var command = PayOrderCommand.of(order.getId(), "card");
        
        // when & then
        assertThatThrownBy(() -> orderService.payOrder(command))
            .isInstanceOf(PaymentFailedException.class);
        
        // 주문 상태는 CREATED 유지
        var savedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
    }
    
    @Test
    void 재고차감중_예외시_전체_롤백() {
        // given
        var member = memberRepository.save(MemberFixture.adult());
        var product1 = productRepository.save(ProductFixture.withStock(10));
        var product2 = productRepository.save(ProductFixture.withStock(1)); // 재고 부족할 상품
        
        var command = CreateOrderCommand.of(
            member.getId(),
            List.of(
                OrderItemCommand.of(product1.getId(), 5),  // 성공
                OrderItemCommand.of(product2.getId(), 10)  // 실패 (재고 1개뿐)
            )
        );
        
        // when & then
        assertThatThrownBy(() -> orderService.createOrder(command))
            .isInstanceOf(InsufficientStockException.class);
        
        // product1 재고도 롤백되어야 함
        var savedProduct1 = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(savedProduct1.getStock()).isEqualTo(10); // 원래대로
    }
}
```

---

## 동시성 테스트

```java
@Nested
class 동시성 {

    @Test
    void 동시_주문시_재고_정합성() throws InterruptedException {
        // given
        var member1 = memberRepository.save(MemberFixture.adult());
        var member2 = memberRepository.save(MemberFixture.adult());
        var product = productRepository.save(ProductFixture.withStock(10));
        
        var executor = Executors.newFixedThreadPool(2);
        var latch = new CountDownLatch(2);
        var successCount = new AtomicInteger(0);
        var failCount = new AtomicInteger(0);
        
        // when - 동시에 10개씩 주문 (총 20개, 재고는 10개)
        executor.submit(() -> {
            try {
                orderService.createOrder(CreateOrderCommand.of(
                    member1.getId(),
                    List.of(OrderItemCommand.of(product.getId(), 10))
                ));
                successCount.incrementAndGet();
            } catch (InsufficientStockException e) {
                failCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
        
        executor.submit(() -> {
            try {
                orderService.createOrder(CreateOrderCommand.of(
                    member2.getId(),
                    List.of(OrderItemCommand.of(product.getId(), 10))
                ));
                successCount.incrementAndGet();
            } catch (InsufficientStockException e) {
                failCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
        
        latch.await(10, TimeUnit.SECONDS);
        
        // then - 하나만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
        
        // 재고는 0이어야 함
        var savedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(savedProduct.getStock()).isEqualTo(0);
    }
}
```

---

## 외부 연동 테스트 (Fake 활용)

```java
class PaymentServiceIT extends IntegrationTestSupport {

    @Autowired
    private OrderCommandService orderService;
    
    @Autowired
    private FakePaymentClient fakePaymentClient;  // Fake 구현체 주입
    
    @BeforeEach
    void setUp() {
        fakePaymentClient.reset();  // 상태 초기화
    }

    @Test
    void 결제성공시_주문상태_PAID로_변경() {
        // given
        var order = createAndSaveOrder(OrderStatus.CREATED);
        fakePaymentClient.willSucceed();
        
        var command = PayOrderCommand.of(order.getId(), "card");
        
        // when
        var result = orderService.payOrder(command);
        
        // then
        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
    }
    
    @Test
    void 결제실패시_예외_및_상태유지() {
        // given
        var order = createAndSaveOrder(OrderStatus.CREATED);
        fakePaymentClient.willFail("잔액 부족");
        
        var command = PayOrderCommand.of(order.getId(), "card");
        
        // when & then
        assertThatThrownBy(() -> orderService.payOrder(command))
            .isInstanceOf(PaymentFailedException.class)
            .hasMessageContaining("잔액 부족");
        
        var savedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
    }
    
    @Test
    void 결제타임아웃시_재시도_안내() {
        // given
        var order = createAndSaveOrder(OrderStatus.CREATED);
        fakePaymentClient.willTimeout();
        
        var command = PayOrderCommand.of(order.getId(), "card");
        
        // when & then
        assertThatThrownBy(() -> orderService.payOrder(command))
            .isInstanceOf(PaymentTimeoutException.class);
    }
}
```

---

## 데이터 셋업 패턴

### @Sql 활용

```java
@Nested
class 대량_데이터_조회 {

    @Test
    @Sql("/test-data/orders-100.sql")
    void 주문목록_페이징_조회() {
        // given
        var query = OrderListQuery.of(memberId, 0, 10);
        
        // when
        var result = orderQueryService.getOrders(query);
        
        // then
        assertThat(result.getContent()).hasSize(10);
        assertThat(result.getTotalElements()).isEqualTo(100);
    }
}
```

### TestDataBuilder 활용

```java
public class TestDataBuilder {
    
    @Autowired
    private MemberRepository memberRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private OrderRepository orderRepository;
    
    public Member createMember() {
        return memberRepository.save(MemberFixture.adult());
    }
    
    public Product createProduct(int stock, int price) {
        return productRepository.save(
            ProductFixture.of(stock, price)
        );
    }
    
    public Order createOrder(Member member, Product product, int quantity) {
        var order = Order.create(member, OrderItems.of(
            List.of(OrderItem.of(product, quantity))
        ));
        return orderRepository.save(order);
    }
}
```

---

## 안티패턴

```java
// ❌ 도메인 로직 세부 검증 - Track 1에서 해야 함
@Test
void bad_도메인_로직_검증() {
    var result = orderService.createOrder(command);
    
    // 할인 계산 로직 검증 - Domain 테스트에서 해야 함
    assertThat(result.getDiscountAmount()).isEqualTo(Money.of(5_000));
}

// ❌ Mock 사용
@Test
void bad_mock_사용() {
    when(memberRepository.findById(any())).thenReturn(Optional.of(member));  // 금지!
}

// ❌ 내부 구현 의존
@Test
void bad_내부구현_의존() {
    orderService.createOrder(command);
    
    // 내부에서 어떤 메서드가 호출되는지 검증 - 금지!
    verify(stockService).decrease(any(), any());
}

// ❌ Repository 기본 CRUD 테스트
@Test
void bad_crud_테스트() {
    var member = memberRepository.save(MemberFixture.adult());
    var found = memberRepository.findById(member.getId());
    
    assertThat(found).isPresent();  // Spring Data JPA 스펙 테스트
}
```