create table depot (
        id bigint generated always as identity primary key,
        code varchar(16) not null unique,
        name varchar(128) not null,
        latitude numeric(9,6) not null,
        longitude numeric(9,6) not null,
        timezone varchar(64) not null,
        created_at timestamptz not null default now()
    );