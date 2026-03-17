# OrderFlow - Event-Driven Order Processing Platform

OrderFlow is a project that simulates a realistic order-processing workflow with asynchronous event choreography.

It demonstrates how an order progresses from creation through inventory, payment, and notification, while handling reliability concerns like retries, idempotency, dead-letter queues, and transactional outbox.

## Tech stack

- Java 17
- Spring Boot 3.3.x
- Spring Data JPA
- Spring Kafka + Spring Retry
- Spring Cache + Redis
- Spring Boot Actuator + Micrometer + Prometheus
- PostgreSQL 16
- Kafka (KRaft mode)
- Kafka UI
- Grafana
- MailHog
- Docker Compose
- Testcontainers (integration tests)
- Maven multi-module monorepo

## Architecture and service responsibilities

### `order-service` (`8080`)

- Public API for creating and querying orders.
- Owns order state transitions.
- Publishes `order.created.v1`, `payment.requested.v1`, and `order.events.v1` through transactional outbox.
- Consumes `inventory.events.v1` and `payment.events.v1`.
- Uses Redis cache for order status lookup.

### `inventory-service` (`8081`)

- Consumes `order.created.v1`.
- Validates/reserves stock and emits `inventory.events.v1` via outbox.
- Exposes internal stock endpoint.
- Exposes DLQ inspect/replay admin endpoints for inventory consumer DLQ.

### `payment-service` (`8082`)

- Consumes `payment.requested.v1`.
- Simulates payment decision (`DECLINED` payment method triggers failure).
- Emits `payment.events.v1` via outbox.
- Exposes internal payment history endpoint.
- Exposes DLQ inspect/replay admin endpoints for payment consumer DLQ.

### `notification-service` (`8083`)

- Consumes `order.events.v1`.
- Persists notification record and emits `notification.events.v1` via outbox.
- Exposes internal notification history endpoint.
- Exposes DLQ inspect/replay admin endpoints for notification consumer DLQ.

## Event-driven workflow

1. Client `POST /api/orders` to `order-service`.
2. `order-service` persists `PENDING_INVENTORY` order + outbox row for `order.created.v1`.
3. Inventory consumes `order.created.v1`, reserves/rejects stock, writes outbox row for `inventory.events.v1`.
4. Order consumes inventory result:
   - `RESERVED` -> updates to `PENDING_PAYMENT`, writes outbox row for `payment.requested.v1`.
   - `REJECTED` -> updates to `FAILED_INVENTORY`, writes outbox row for `order.events.v1`.
5. Payment consumes `payment.requested.v1`, saves payment result, writes outbox row for `payment.events.v1`.
6. Order consumes payment result:
   - `SUCCEEDED` -> `COMPLETED`, writes `order.events.v1`.
   - `FAILED` -> `FAILED_PAYMENT`, writes `order.events.v1`.
7. Notification consumes `order.events.v1`, stores notification, writes `notification.events.v1`.

All producer services use outbox relay schedulers to publish pending events.

## Kafka topics

### Core topics

- `order.created.v1`
- `inventory.events.v1`
- `payment.requested.v1`
- `payment.events.v1`
- `order.events.v1`
- `notification.events.v1`

### Retry and dead-letter topics

Consumers use `@RetryableTopic` with:
- attempts: `3`
- exponential backoff: `1s`, multiplier `2.0`
- DLT suffix: `.dlq`

Examples:
- `order.created.v1.dlq`
- `payment.requested.v1.dlq`
- `order.events.v1.dlq`

## Reliability patterns implemented

### Transactional Outbox

Implemented in all producer services:
- `order-service`
- `inventory-service`
- `payment-service`
- `notification-service`

Pattern summary:
- Business state update and outbox insert happen in one local DB transaction.
- Scheduled relay publishes pending outbox rows to Kafka.
- Prevents state-write/event-publish inconsistency.

### Idempotent consumers

- Each service stores consumed `eventId` in `processed_events`.
- Duplicate deliveries are ignored.

### DLQ inspection and replay

Admin endpoints are available in inventory/payment/notification services to:
- inspect latest DLQ records
- replay a selected partition+offset message back to the source topic

## Redis caching

Current cache usage:
- `order-service` caches `GET /api/orders/{orderId}/status` response by `orderId`.

## Data model summary

- `order_core`: `orders`, `order_items`, `outbox_events`, `processed_events`
- `inventory`: `inventory_stock`, `outbox_events`, `processed_events`
- `payment`: `payments`, `outbox_events`, `processed_events`
- `notification`: `notifications`, `outbox_events`, `processed_events`

## API endpoints

### Public

- `POST /api/orders`
- `GET /api/orders/{orderId}`
- `GET /api/orders/{orderId}/status`

### Internal data/debug

- `GET /internal/inventory/{skuId}`
- `GET /internal/payments/{orderId}`
- `GET /internal/notifications/{orderId}`

### DLQ admin

- `GET /internal/inventory/admin/dlq?limit=20`
- `POST /internal/inventory/admin/dlq/replay`
- `GET /internal/payments/admin/dlq?limit=20`
- `POST /internal/payments/admin/dlq/replay`
- `GET /internal/notifications/admin/dlq?limit=20`
- `POST /internal/notifications/admin/dlq/replay`

Replay body:

```json
{
  "partition": 0,
  "offset": 12
}
```

## Local development

### 1) Start infrastructure

```powershell
docker compose up -d
```

### 2) Build all modules

```powershell
.\mvnw.cmd -DskipTests install
```

### 3) Run services (4 terminals)

```powershell
.\mvnw.cmd -pl services/order-service spring-boot:run
.\mvnw.cmd -pl services/inventory-service spring-boot:run
.\mvnw.cmd -pl services/payment-service spring-boot:run
.\mvnw.cmd -pl services/notification-service spring-boot:run
```

### Helper scripts (PowerShell)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-services.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\stop-services.ps1
```

## Infrastructure UIs

- Kafka UI: `http://localhost:8085`
- MailHog: `http://localhost:8025`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (`admin` / `admin`)

Grafana dashboard JSON is provided at `docs/grafana/orderflow-overview-dashboard.json`.

## Integration tests (Testcontainers)

An end-to-end integration module exists at `integration-tests/`.

It boots PostgreSQL, Kafka, and Redis containers, then launches all four Spring Boot services in-process and validates:
- happy path completion
- payment-failure path
- payment/notification persistence side effects

Run:

```powershell
.\mvnw.cmd -pl integration-tests -am test "-Dsurefire.failIfNoSpecifiedTests=false" "-DrunE2E=true"
```

See `docs/testcontainers.md` for details.

By default, the E2E class is skipped unless `-DrunE2E=true` is provided.

## Example order request

```http
POST /api/orders
Content-Type: application/json
X-Correlation-Id: 7f4a8b01-95c5-4e4c-9a06-9c3b66e6d4af

{
  "customerId": "2e67ee2b-0ce5-4e4f-b586-50ff61eeb86f",
  "paymentMethod": "CARD",
  "items": [
    { "skuId": "sku-burger-001", "quantity": 2, "unitPrice": 8.50 },
    { "skuId": "sku-drink-001", "quantity": 1, "unitPrice": 2.00 }
  ]
}
```

## Current status

- [x] Event-driven flow across order/inventory/payment/notification services
- [x] Transactional outbox in all producer services
- [x] Idempotent consumers
- [x] Retry topics + dead-letter topics
- [x] DLQ inspection/replay admin endpoints
- [x] Redis caching for order status
- [x] Observability stack (Actuator, Prometheus, Grafana assets)
- [x] Testcontainers end-to-end integration tests

## Stretch improvements

- Secure admin/internal endpoints with JWT + role checks.
- Add correlation-id filters and structured JSON log layout.
- Add order timeline/audit endpoint from event history.
- Add replay safety controls (max batch replay, dry-run mode).
- Replace simple payment simulation with strategy-based providers.
