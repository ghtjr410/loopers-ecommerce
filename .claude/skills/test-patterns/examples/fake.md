# Fake 구현체 예제

## 원칙
- 외부 시스템(PG, 배송, 알림 등)만 Fake로 대체
- `@Profile("test")`로 테스트 환경에서만 활성화
- 실제 동작과 유사하게 구현 (단순 Mock과 다름)
- 테스트에서 시나리오 제어 가능하게 설계

---

## Mock vs Fake vs Dummy

| 종류 | 특징 | 용도 |
|------|------|------|
| **Mock** | 호출 검증용, 동작 없음 | 지양 |
| **Fake** | 실제와 유사한 동작 구현 | 외부 API |
| **Dummy** | 아무 동작 안 함 | 부수효과 무시 |

---

## 결제 클라이언트 Fake

### 인터페이스

```java
public interface PaymentClient {
    PaymentResult pay(PaymentRequest request);
    PaymentResult cancel(PaymentCancelRequest request);
    PaymentStatus getStatus(String transactionId);
}
```

### 실제 구현

```java
@Profile("!test")
@Component
@RequiredArgsConstructor
public class TossPaymentClient implements PaymentClient {
    
    private final RestTemplate restTemplate;
    private final PaymentProperties properties;
    
    @Override
    public PaymentResult pay(PaymentRequest request) {
        var response = restTemplate.postForEntity(
            properties.getPayUrl(),
            request,
            TossPaymentResponse.class
        );
        return PaymentResult.from(response.getBody());
    }
    
    // ...
}
```

### Fake 구현

```java
@Profile("test")
@Component
public class FakePaymentClient implements PaymentClient {
    
    private PaymentScenario scenario = PaymentScenario.SUCCESS;
    private String failReason = null;
    private final Map<String, PaymentStatus> transactions = new ConcurrentHashMap<>();
    
    // 테스트에서 시나리오 제어
    public void willSucceed() {
        this.scenario = PaymentScenario.SUCCESS;
        this.failReason = null;
    }
    
    public void willFail(String reason) {
        this.scenario = PaymentScenario.FAIL;
        this.failReason = reason;
    }
    
    public void willTimeout() {
        this.scenario = PaymentScenario.TIMEOUT;
    }
    
    public void reset() {
        this.scenario = PaymentScenario.SUCCESS;
        this.failReason = null;
        this.transactions.clear();
    }
    
    @Override
    public PaymentResult pay(PaymentRequest request) {
        return switch (scenario) {
            case SUCCESS -> {
                var txId = UUID.randomUUID().toString();
                transactions.put(txId, PaymentStatus.PAID);
                yield PaymentResult.success(txId, request.amount());
            }
            case FAIL -> PaymentResult.fail(failReason);
            case TIMEOUT -> throw new PaymentTimeoutException("결제 응답 시간 초과");
        };
    }
    
    @Override
    public PaymentResult cancel(PaymentCancelRequest request) {
        var status = transactions.get(request.transactionId());
        if (status == null) {
            return PaymentResult.fail("존재하지 않는 거래");
        }
        if (status == PaymentStatus.CANCELLED) {
            return PaymentResult.fail("이미 취소된 거래");
        }
        
        transactions.put(request.transactionId(), PaymentStatus.CANCELLED);
        return PaymentResult.success(request.transactionId(), request.amount());
    }
    
    @Override
    public PaymentStatus getStatus(String transactionId) {
        return transactions.getOrDefault(transactionId, PaymentStatus.NOT_FOUND);
    }
    
    private enum PaymentScenario {
        SUCCESS, FAIL, TIMEOUT
    }
}
```

### 테스트에서 사용

```java
@SpringBootTest
class PaymentServiceIT {
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private FakePaymentClient fakePaymentClient;
    
    @BeforeEach
    void setUp() {
        fakePaymentClient.reset();
    }
    
    @Test
    void 결제_성공() {
        fakePaymentClient.willSucceed();

        var result = paymentService.pay(command);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void 결제_실패_잔액부족() {
        fakePaymentClient.willFail("잔액 부족");

        assertThatThrownBy(() -> paymentService.pay(command))
            .isInstanceOf(PaymentFailedException.class)
            .hasMessageContaining("잔액 부족");
    }

    @Test
    void 결제_타임아웃_재시도_안내() {
        fakePaymentClient.willTimeout();
        
        assertThatThrownBy(() -> paymentService.pay(command))
            .isInstanceOf(PaymentTimeoutException.class);
    }
}
```

---

## 알림 발송 Dummy

부수효과만 무시하면 되는 경우 Dummy 사용

```java
public interface NotificationSender {
    void send(Notification notification);
    void sendBulk(List<Notification> notifications);
}

@Profile("!test")
@Component
@RequiredArgsConstructor
public class SlackNotificationSender implements NotificationSender {
    
    private final SlackClient slackClient;
    
    @Override
    public void send(Notification notification) {
        slackClient.postMessage(notification.toSlackMessage());
    }
    
    @Override
    public void sendBulk(List<Notification> notifications) {
        notifications.forEach(this::send);
    }
}

@Profile("test")
@Component
public class DummyNotificationSender implements NotificationSender {
    
    // 발송 기록 (필요시 검증용)
    private final List<Notification> sentNotifications = new ArrayList<>();
    
    @Override
    public void send(Notification notification) {
        sentNotifications.add(notification);  // 기록만 하고 실제 발송 안 함
    }
    
    @Override
    public void sendBulk(List<Notification> notifications) {
        sentNotifications.addAll(notifications);
    }
    
    // 테스트 검증용
    public List<Notification> getSentNotifications() {
        return List.copyOf(sentNotifications);
    }
    
    public void reset() {
        sentNotifications.clear();
    }
}
```

### 테스트에서 발송 여부 검증

```java
@Test
void 주문완료시_알림_발송() {
    // given
    dummyNotificationSender.reset();
    var order = createOrder();
    
    // when
    orderService.complete(order.getId());
    
    // then
    var notifications = dummyNotificationSender.getSentNotifications();
    assertThat(notifications).hasSize(1);
    assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.ORDER_COMPLETED);
}
```

---

## 외부 API Fake (배송 조회)

```java
public interface DeliveryTracker {
    DeliveryStatus track(String trackingNumber);
}

@Profile("!test")
@Component
public class CJDeliveryTracker implements DeliveryTracker {
    
    @Override
    public DeliveryStatus track(String trackingNumber) {
        // 실제 CJ 대한통운 API 호출
    }
}

@Profile("test")
@Component
public class FakeDeliveryTracker implements DeliveryTracker {
    
    private final Map<String, DeliveryStatus> trackingData = new ConcurrentHashMap<>();
    
    // 테스트 데이터 설정
    public void setStatus(String trackingNumber, DeliveryStatus status) {
        trackingData.put(trackingNumber, status);
    }
    
    public void setDelivered(String trackingNumber) {
        trackingData.put(trackingNumber, DeliveryStatus.DELIVERED);
    }
    
    public void setInTransit(String trackingNumber) {
        trackingData.put(trackingNumber, DeliveryStatus.IN_TRANSIT);
    }
    
    @Override
    public DeliveryStatus track(String trackingNumber) {
        return trackingData.getOrDefault(trackingNumber, DeliveryStatus.NOT_FOUND);
    }
    
    public void reset() {
        trackingData.clear();
    }
}
```

---

## 시간 의존성 처리

```java
// Clock을 Bean으로 등록
@Configuration
public class ClockConfig {
    
    @Bean
    @Profile("!test")
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
    
    @Bean
    @Profile("test")
    public Clock testClock() {
        return Clock.fixed(
            LocalDateTime.of(2024, 1, 15, 12, 0).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC
        );
    }
}

// 또는 테스트에서 조작 가능한 Clock
@Profile("test")
@Component
public class TestClock extends Clock {
    
    private Instant instant = Instant.now();
    private ZoneId zone = ZoneId.systemDefault();
    
    @Override
    public ZoneId getZone() {
        return zone;
    }
    
    @Override
    public Clock withZone(ZoneId zone) {
        this.zone = zone;
        return this;
    }
    
    @Override
    public Instant instant() {
        return instant;
    }
    
    // 테스트에서 시간 조작
    public void setTime(LocalDateTime dateTime) {
        this.instant = dateTime.toInstant(ZoneOffset.UTC);
    }
    
    public void advanceTime(Duration duration) {
        this.instant = instant.plus(duration);
    }
}
```

### 테스트에서 사용

```java
@Test
void 프로모션_만료_확인() {
    // given
    var promotion = createPromotion(endDate: "2024-01-31");
    
    // 1월 15일 - 진행 중
    testClock.setTime(LocalDateTime.of(2024, 1, 15, 12, 0));
    assertThat(promotionService.isActive(promotion.getId())).isTrue();
    
    // 2월 1일 - 만료
    testClock.setTime(LocalDateTime.of(2024, 2, 1, 0, 0));
    assertThat(promotionService.isActive(promotion.getId())).isFalse();
}
```

---

## 파일 저장소 Fake

```java
public interface FileStorage {
    String upload(String path, byte[] content);
    byte[] download(String path);
    void delete(String path);
}

@Profile("!test")
@Component
public class S3FileStorage implements FileStorage {
    // 실제 S3 연동
}

@Profile("test")
@Component
public class InMemoryFileStorage implements FileStorage {
    
    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();
    
    @Override
    public String upload(String path, byte[] content) {
        storage.put(path, content);
        return "http://fake-storage/" + path;
    }
    
    @Override
    public byte[] download(String path) {
        var content = storage.get(path);
        if (content == null) {
            throw new FileNotFoundException(path);
        }
        return content;
    }
    
    @Override
    public void delete(String path) {
        storage.remove(path);
    }
    
    public void reset() {
        storage.clear();
    }
    
    public boolean exists(String path) {
        return storage.containsKey(path);
    }
}
```

---

## Fake 작성 가이드라인

### ✅ 좋은 Fake
- 실제 동작과 유사하게 동작
- 테스트에서 시나리오 제어 가능
- 상태 초기화 메서드 제공 (`reset()`)
- 검증용 메서드 제공 (필요시)

### ❌ 나쁜 Fake
```java
// 너무 단순 - 항상 성공만 반환
@Profile("test")
@Component
public class BadFakePaymentClient implements PaymentClient {
    @Override
    public PaymentResult pay(PaymentRequest request) {
        return PaymentResult.success("tx-123", request.amount());  // 항상 성공
    }
}

// 테스트 불가능 - 시나리오 제어 불가
// 실패 케이스, 타임아웃 케이스 테스트 불가
```

---

## 디렉토리 구조

```
src/
├── main/java/
│   └── com/example/
│       └── infrastructure/
│           └── external/
│               ├── payment/
│               │   ├── PaymentClient.java          # 인터페이스
│               │   └── TossPaymentClient.java      # 실제 구현
│               └── notification/
│                   ├── NotificationSender.java
│                   └── SlackNotificationSender.java
└── test/java/
    └── com/example/
        ├── fake/
        │   ├── FakePaymentClient.java              # Fake 구현
        │   ├── DummyNotificationSender.java        # Dummy 구현
        │   └── InMemoryFileStorage.java
        └── support/
            └── IntegrationTestSupport.java
```

또는 `src/main/java`에 `@Profile("test")`로 함께 배치

```
src/main/java/
└── com/example/
    └── infrastructure/
        └── external/
            └── payment/
                ├── PaymentClient.java
                ├── TossPaymentClient.java      # @Profile("!test")
                └── FakePaymentClient.java      # @Profile("test")
```