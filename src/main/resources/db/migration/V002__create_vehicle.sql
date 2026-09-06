create table vehicle (
                         id                   bigint generated always as identity primary key,
                         depot_id             bigint not null references depot(id),
                         registration         varchar(16) not null unique,
                         capacity_grams       bigint not null check (capacity_grams > 0),
                         capacity_millilitres bigint not null check (capacity_millilitres > 0),
                         shift_start          time not null,
                         shift_minutes        int not null check (shift_minutes > 0 and shift_minutes <= 2880),
                         avg_speed_kph        numeric(5,2) not null check (avg_speed_kph > 0),
                         active               boolean not null default true,
                         version              bigint not null default 0,
                         created_at           timestamptz not null default now()
);

create index idx_vehicle_depot_active on vehicle(depot_id, active);