-- Who may do what, and whose plan is whose.
--
-- One role per user, not a join table. A dispatcher who is also a driver is not a case this
-- business has, and a many-to-many invented before anything needs it is a migration for its own
-- sake. If it ever turns up, the column becomes a table and this comment becomes wrong.
--
-- driver_id links a user to the driver record they are. It is null for dispatchers and admins, and
-- it is what makes "a driver may only report against their own plans" a question with an answer.
create table app_user (
    id            bigint generated always as identity primary key,
    username      varchar(64) not null unique,
    password_hash varchar(100) not null,
    role          varchar(16) not null,
    driver_id     bigint references driver(id),
    active        boolean not null default true,
    version       bigint not null default 0,
    created_at    timestamptz not null default now(),
    constraint app_user_role_check check (role in ('ADMIN', 'DISPATCHER', 'DRIVER')),
    -- A driver account that is not linked to a driver could never post anything, and a dispatcher
    -- linked to one would be a role confusion rather than a convenience.
    constraint app_user_driver_link_check check (
        (role = 'DRIVER' and driver_id is not null) or (role <> 'DRIVER' and driver_id is null))
);

create index idx_app_user_driver on app_user (driver_id);

-- Which driver is taking this plan out. Nullable: the solver assigns vehicles, not people, so a
-- plan exists before anyone is rostered onto it and a dispatcher assigns the driver separately.
alter table plan add column driver_id bigint references driver(id);

create index idx_plan_driver on plan (driver_id, service_date);
