create table driver (
    id            bigint generated always as identity primary key,
    depot_id      bigint not null references depot(id),
    employee_no   varchar(32) not null unique,
    full_name     varchar(128) not null,
    licence_class varchar(16) not null,
    active        boolean not null default true,
    version       bigint not null default 0,
    created_at    timestamptz not null default now()
);

create index idx_driver_depot_active on driver (depot_id, active);
