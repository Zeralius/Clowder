-- M5: the execution event log.
--
-- Append-only. There is no updated_at and there never will be: a correction is a new event that
-- compensates for an old one, the same rule as plans and for the same reason.
--
-- Two timestamps, always. occurred_at is the device clock and is used only for ordering, because
-- phone clocks are wrong and a driver who spent an hour underground uploads six events in a burst.
-- received_at is the server clock, and it is what lateness and connectivity are reasoned about.
create table driver_event (
    id              bigint generated always as identity primary key,
    idempotency_key varchar(64) not null unique,
    plan_id         bigint not null references plan(id),
    plan_stop_id    bigint references plan_stop(id),
    event_type      varchar(24) not null,
    occurred_at     timestamptz not null,
    received_at     timestamptz not null default now(),
    latitude        numeric(9,6),
    longitude       numeric(9,6),
    failure_reason  varchar(64),
    payload         jsonb
);

-- plan_stop_id is nullable because DEPARTED_DEPOT and RETURNED_DEPOT belong to the route, not to any
-- one stop.
create index idx_driver_event_plan on driver_event (plan_id, occurred_at);
create index idx_driver_event_stop on driver_event (plan_stop_id, occurred_at);

-- payload is a trap door, not a design: a signature reference, a proof-of-delivery photo id. The
-- moment anything filters on a field inside it, that field wants to be a column.
