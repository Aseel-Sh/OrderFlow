# Testcontainers End-to-End Tests

This project includes an integration suite that uses Testcontainers to spin up:
- PostgreSQL
- Kafka (KRaft)
- Redis

The suite boots all 4 Spring Boot services in-process on test ports and verifies the full order lifecycle:
1. Create order via `order-service` HTTP API.
2. Inventory reserves stock.
3. Payment succeeds or fails.
4. Notification is persisted.
5. Final order state is eventually consistent and queryable.

Run:

```powershell
.\mvnw.cmd -pl integration-tests -am test "-Dsurefire.failIfNoSpecifiedTests=false" "-DrunE2E=true"
```

Notes:
- Docker Desktop (or another Docker engine) must be running for the containers to start.
- The integration test class uses `@Testcontainers(disabledWithoutDocker = true)` and `@EnabledIfSystemProperty(named = "runE2E", matches = "true")`.
- This means regular `mvn test` runs keep the suite skipped unless you explicitly pass `-DrunE2E=true`.

