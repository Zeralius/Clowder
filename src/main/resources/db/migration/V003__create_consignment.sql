create table consignment (
    id                 bigint generated always as identity primary key,
    reference          varchar(32) not null unique,
    depot_id           bigint not null references depot(id),
    service_date       date not null,
    latitude           numeric(9,6) not null,
    longitude          numeric(9,6) not null,
    address_line       varchar(256) not null,
    weight_grams       bigint not null check (weight_grams >= 0),
    volume_millilitres bigint not null check (volume_millilitres >= 0),
    service_minutes    int not null check (service_minutes >= 0),
    window_start       timestamptz not null,
    window_end         timestamptz not null,
    status             varchar(24) not null,
    version            bigint not null default 0,
    created_at         timestamptz not null default now(),
    check (window_end > window_start)
);

create index idx_consignment_planning on consignment (depot_id, service_date, status);
