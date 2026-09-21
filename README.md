# Clowder

Delivery route planning for a small fleet: depots, vehicles, drivers and consignments, a feasibility
engine, and a VRPTW solver benchmarked against the Solomon instances.

Java 21 · Spring Boot 4 · PostgreSQL 17 · Flyway

## Running

```bash
docker compose up -d
./mvnw spring-boot:run
```

`./mvnw verify` runs the unit tests under Surefire and the Testcontainers-backed integration tests
under Failsafe; Docker must be running for the latter.

## Pagination

Listing endpoints use offset pagination (`?page=&size=`, default size 50). This is fine at the scale
the system targets — a depot-day is hundreds of rows, not millions — but `OFFSET` makes deep pages
progressively more expensive and can skip or repeat rows when data changes between requests. Cursor
pagination is the fix and is not implemented.
