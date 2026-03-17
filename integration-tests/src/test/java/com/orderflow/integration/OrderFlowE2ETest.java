package com.orderflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.inventoryservice.InventoryServiceApplication;
import com.orderflow.notificationservice.NotificationServiceApplication;
import com.orderflow.orderservice.OrderServiceApplication;
import com.orderflow.paymentservice.PaymentServiceApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "runE2E", matches = "true")
class OrderFlowE2ETest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("orderflow")
            .withUsername("orderflow")
            .withPassword("orderflow");

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2"))
            .withExposedPorts(6379);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static ConfigurableApplicationContext orderContext;
    private static ConfigurableApplicationContext inventoryContext;
    private static ConfigurableApplicationContext paymentContext;
    private static ConfigurableApplicationContext notificationContext;

    @BeforeAll
    static void setUp() throws Exception {
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
        initSchemasAndSeedInventory();

        String kafkaBootstrap = KAFKA.getBootstrapServers();
        String redisHost = REDIS.getHost();
        Integer redisPort = REDIS.getMappedPort(6379);

        orderContext = new SpringApplicationBuilder(OrderServiceApplication.class)
                .properties(toProperties(commonProperties("order_core", 18080, kafkaBootstrap, redisHost, redisPort)))
                .run();

        inventoryContext = new SpringApplicationBuilder(InventoryServiceApplication.class)
                .properties(toProperties(commonProperties("inventory", 18081, kafkaBootstrap, redisHost, redisPort)))
                .run();

        paymentContext = new SpringApplicationBuilder(PaymentServiceApplication.class)
                .properties(toProperties(commonProperties("payment", 18082, kafkaBootstrap, redisHost, redisPort)))
                .run();

        notificationContext = new SpringApplicationBuilder(NotificationServiceApplication.class)
                .properties(toProperties(commonProperties("notification", 18083, kafkaBootstrap, redisHost, redisPort)))
                .run();
    }

    @AfterAll
    static void tearDown() {
        closeQuietly(notificationContext);
        closeQuietly(paymentContext);
        closeQuietly(inventoryContext);
        closeQuietly(orderContext);
        REDIS.stop();
        KAFKA.stop();
        POSTGRES.stop();
    }

    @Test
    void shouldCompleteOrderAndPersistNotification() throws Exception {
        UUID orderId = createOrder("CARD");
        waitForStatus(orderId, "COMPLETED", Duration.ofSeconds(45));

        JsonNode payments = getJson("http://localhost:18082/internal/payments/" + orderId);
        assertTrue(payments.isArray());
        assertFalse(payments.isEmpty());
        assertEquals("SUCCEEDED", payments.get(0).get("status").asText());

        JsonNode notifications = getJson("http://localhost:18083/internal/notifications/" + orderId);
        assertTrue(notifications.isArray());
        assertFalse(notifications.isEmpty());
        assertEquals("SENT", notifications.get(0).get("status").asText());
    }

    @Test
    void shouldFailOrderWhenPaymentDeclined() throws Exception {
        UUID orderId = createOrder("DECLINED");
        waitForStatus(orderId, "FAILED_PAYMENT", Duration.ofSeconds(45));

        JsonNode payments = getJson("http://localhost:18082/internal/payments/" + orderId);
        assertTrue(payments.isArray());
        assertFalse(payments.isEmpty());
        assertEquals("FAILED", payments.get(0).get("status").asText());

        JsonNode notifications = getJson("http://localhost:18083/internal/notifications/" + orderId);
        assertTrue(notifications.isArray());
        assertFalse(notifications.isEmpty());
    }

    private static Map<String, Object> commonProperties(String schema,
                                                        int port,
                                                        String kafkaBootstrap,
                                                        String redisHost,
                                                        int redisPort) {
        return Map.ofEntries(
                Map.entry("server.port", port),
                Map.entry("spring.datasource.url", schemaJdbcUrl(schema)),
                Map.entry("spring.datasource.username", POSTGRES.getUsername()),
                Map.entry("spring.datasource.password", POSTGRES.getPassword()),
                Map.entry("ORDER_DB_URL", schemaJdbcUrl(schema)),
                Map.entry("ORDER_DB_USER", POSTGRES.getUsername()),
                Map.entry("ORDER_DB_PASSWORD", POSTGRES.getPassword()),
                Map.entry("INVENTORY_DB_URL", schemaJdbcUrl(schema)),
                Map.entry("INVENTORY_DB_USER", POSTGRES.getUsername()),
                Map.entry("INVENTORY_DB_PASSWORD", POSTGRES.getPassword()),
                Map.entry("PAYMENT_DB_URL", schemaJdbcUrl(schema)),
                Map.entry("PAYMENT_DB_USER", POSTGRES.getUsername()),
                Map.entry("PAYMENT_DB_PASSWORD", POSTGRES.getPassword()),
                Map.entry("NOTIFICATION_DB_URL", schemaJdbcUrl(schema)),
                Map.entry("NOTIFICATION_DB_USER", POSTGRES.getUsername()),
                Map.entry("NOTIFICATION_DB_PASSWORD", POSTGRES.getPassword()),
                Map.entry("spring.jpa.hibernate.ddl-auto", "update"),
                Map.entry("spring.kafka.bootstrap-servers", kafkaBootstrap),
                Map.entry("spring.kafka.consumer.auto-offset-reset", "earliest"),
                Map.entry("spring.data.redis.host", redisHost),
                Map.entry("spring.data.redis.port", redisPort),
                Map.entry("management.endpoints.web.exposure.include", "health,info")
        );
    }

    private static Properties toProperties(Map<String, Object> source) {
        Properties properties = new Properties();
        source.forEach((key, value) -> properties.put(key, String.valueOf(value)));
        return properties;
    }

    private static String schemaJdbcUrl(String schema) {
        String jdbcUrl = POSTGRES.getJdbcUrl();
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + "currentSchema=" + schema;
    }

    private static void initSchemasAndSeedInventory() throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS order_core");
            statement.execute("CREATE SCHEMA IF NOT EXISTS inventory");
            statement.execute("CREATE SCHEMA IF NOT EXISTS payment");
            statement.execute("CREATE SCHEMA IF NOT EXISTS notification");
            statement.execute("CREATE TABLE IF NOT EXISTS inventory.inventory_stock (sku_id VARCHAR(100) PRIMARY KEY, available_qty INTEGER NOT NULL, reserved_qty INTEGER NOT NULL, updated_at TIMESTAMP NOT NULL)");
            statement.execute("INSERT INTO inventory.inventory_stock (sku_id, available_qty, reserved_qty, updated_at) VALUES ('sku-burger-001', 100, 0, NOW()) ON CONFLICT (sku_id) DO NOTHING");
            statement.execute("INSERT INTO inventory.inventory_stock (sku_id, available_qty, reserved_qty, updated_at) VALUES ('sku-drink-001', 100, 0, NOW()) ON CONFLICT (sku_id) DO NOTHING");
        }
    }

    private static UUID createOrder(String paymentMethod) throws Exception {
        String payload = """
                {
                  \"customerId\": \"%s\",
                  \"paymentMethod\": \"%s\",
                  \"items\": [
                    { \"skuId\": \"sku-burger-001\", \"quantity\": 1, \"unitPrice\": 8.50 },
                    { \"skuId\": \"sku-drink-001\", \"quantity\": 1, \"unitPrice\": 2.00 }
                  ]
                }
                """.formatted(UUID.randomUUID(), paymentMethod);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:18080/api/orders"))
                .header("Content-Type", "application/json")
                .header("X-Correlation-Id", UUID.randomUUID().toString())
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(202, response.statusCode(), response.body());
        return UUID.fromString(OBJECT_MAPPER.readTree(response.body()).get("orderId").asText());
    }

    private static void waitForStatus(UUID orderId, String expectedStatus, Duration timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            JsonNode status = getJson("http://localhost:18080/api/orders/" + orderId + "/status");
            if (expectedStatus.equals(status.get("status").asText())) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Timed out waiting for order status=" + expectedStatus + " for orderId=" + orderId);
    }

    private static JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return OBJECT_MAPPER.readTree(response.body());
    }

    private static void closeQuietly(ConfigurableApplicationContext context) {
        if (context != null) {
            context.close();
        }
    }
}



