-- M4: plans as versioned artifacts.
--
-- Two columns named "version" would mean two different things, so neither is called that:
-- version_no is the number a dispatcher reads (v1, v2) and lock_version is JPA's optimistic
-- locking counter. They are unrelated and both belong on plan.
--
-- total_distance is in the distance matrix's minor units, not metres (decision D7).

create table plan_run (
    id             bigint generated always as identity primary key,
    depot_id       bigint not null references depot(id),
    service_date   date not null,
    status         varchar(24) not null,
    solver_version varchar(64) not null,
    seed           bigint not null,
    budget_ms      int not null check (budget_ms > 0),
    elapsed_ms     bigint,
    iterations     int,
    failure_reason varchar(512),
    started_at     timestamptz not null default now(),
    finished_at    timestamptz
);

create index idx_plan_run_lookup on plan_run (depot_id, service_date, started_at desc);

-- What the solver could not route. A run that silently dropped work would be worse than one that failed.
create table plan_run_unassigned (
    plan_run_id    bigint not null references plan_run(id) on delete cascade,
    consignment_id bigint not null references consignment(id),
    reference      varchar(32) not null,
    primary key (plan_run_id, consignment_id)
);

create table plan (
    id                     bigint generated always as identity primary key,
    plan_run_id            bigint not null references plan_run(id),
    vehicle_id             bigint not null references vehicle(id),
    service_date           date not null,
    version_no             int not null check (version_no > 0),
    status                 varchar(24) not null,
    total_distance         bigint not null check (total_distance >= 0),
    total_duration_minutes int not null check (total_duration_minutes >= 0),
    published_at           timestamptz,
    lock_version           bigint not null default 0,
    created_at             timestamptz not null default now(),
    unique (vehicle_id, service_date, version_no)
);

-- The milestone in three lines: at most one published plan per vehicle per day, enforced by
-- Postgres rather than by a select-then-insert that two dispatchers can both win.
create unique index uq_plan_published
    on plan (vehicle_id, service_date)
    where status = 'PUBLISHED';

create index idx_plan_lookup on plan (vehicle_id, service_date, status);

-- A snapshot, not a view. If a consignment's window or address is edited next week, this plan must
-- still show what was planned, which is why the reference and address are copied rather than joined.
create table plan_stop (
    id                    bigint generated always as identity primary key,
    plan_id               bigint not null references plan(id) on delete cascade,
    sequence_no           int not null check (sequence_no >= 0),
    consignment_id        bigint not null references consignment(id),
    reference             varchar(32) not null,
    address_line          varchar(256) not null,
    planned_arrival       timestamptz not null,
    planned_service_start timestamptz not null,
    planned_departure     timestamptz not null,
    wait_minutes          int not null check (wait_minutes >= 0),
    cumulative_distance   bigint not null check (cumulative_distance >= 0),
    load_after            bigint not null check (load_after >= 0),
    unique (plan_id, sequence_no)
);

create index idx_plan_stop_consignment on plan_stop (consignment_id);
