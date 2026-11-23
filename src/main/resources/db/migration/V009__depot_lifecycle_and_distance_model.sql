-- Three columns, each because something now needs it.
--
-- active: depots can close. Until now there was no lifecycle, and inventing one before anything
-- needed it would have been a migration for its own sake. Deactivating a depot is now a real
-- operation, so the column earns its place.
--
-- version: depots are updated now, so they need the same optimistic-locking counter every other
-- mutable entity in this schema has. Two dispatchers editing one depot was previously impossible
-- because editing was impossible.
--
-- distance_model: which geometry this depot's coordinates are in. Solomon instances are abstract
-- plane units and must be measured with Euclidean distance or the benchmark comparison is destroyed;
-- a real depot is degrees on a sphere and wants haversine. The planner had this hard-coded, so the
-- choice was per build rather than per depot.
alter table depot add column active boolean not null default true;

alter table depot add column version bigint not null default 0;

alter table depot add column distance_model varchar(16);

-- Every row that exists today came in through the Solomon importer, so it is plane units.
update depot set distance_model = 'EUCLIDEAN' where distance_model is null;

alter table depot alter column distance_model set not null;

alter table depot
    add constraint depot_distance_model_check
    check (distance_model in ('EUCLIDEAN', 'HAVERSINE'));

create index idx_depot_active on depot (active);
