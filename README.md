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

## Solomon instances

`POST /api/import/solomon` takes a raw Solomon VRPTW instance file as `text/plain` and creates the
depot, vehicles and consignments through the ordinary service layer, so imported data obeys the
same invariants as anything created by hand.

Solomon coordinates are abstract plane units, not degrees, and are stored raw: x as longitude, y as
latitude. `LatLon` enforces ±90 / ±180, and the standard instances sit in roughly 0–100 on both
axes, so an instance whose y exceeds 90 is rejected with an explanatory error rather than silently
clamped. Euclidean distance is translation-invariant, so if that ever trips, subtracting a constant
from every coordinate preserves every published result exactly. Distances for these instances must
be computed as Euclidean, never haversine; projecting them onto real geography would destroy
benchmark comparability.

The format carries no date, so imports default to `2026-01-05` with the depot in UTC, and travel
time equals distance (speed is nominal and unused).
