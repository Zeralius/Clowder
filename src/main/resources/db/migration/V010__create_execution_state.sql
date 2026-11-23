-- A materialised projection of the event log.
--
-- Everything in these two tables is derived. The log is still the only record of what happened, and
-- nothing here is written by hand: every row is the output of folding driver_event, recomputed when
-- an event arrives and rebuildable from scratch at any time. ProjectionRebuildIT is what makes that
-- claim checkable rather than aspirational.
--
-- Why materialise at all. Tracking used to fold the whole log on every read, which is nothing at a
-- depot-day of a few hundred rows and is a problem at a thousand vehicles with a day of history
-- each. A cache with a proven rebuild is a different thing from a second source of truth.
create table plan_execution_state (
    plan_id              bigint primary key references plan(id) on delete cascade,
    stops_completed      int not null check (stops_completed >= 0),
    stops_remaining      int not null check (stops_remaining >= 0),
    current_delay_minutes int not null,
    last_known_departure timestamptz,
    last_completed_stop  bigint references plan_stop(id),
    events_applied       int not null check (events_applied >= 0),
    updated_at           timestamptz not null default now()
);

create table plan_stop_state (
    plan_stop_id     bigint primary key references plan_stop(id) on delete cascade,
    plan_id          bigint not null references plan(id) on delete cascade,
    consignment_id   bigint not null references consignment(id),
    reference        varchar(32) not null,
    status           varchar(16) not null,
    actual_arrival   timestamptz,
    actual_departure timestamptz,
    failure_reason   varchar(64)
);

create index idx_plan_stop_state_plan on plan_stop_state (plan_id);
