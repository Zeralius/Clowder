-- A plan is for one vehicle out of one depot, and execution needs the depot to know where the
-- vehicle starts from before it has completed anything. Reading it back through plan_run would make
-- every plan read a two-table affair, and reaching into another module to get it would be worse, so
-- the column lives on the row it describes.
--
-- Backfilled rather than defaulted: plan_run already knows the answer for every existing row, and a
-- migration that fails on existing data is not a migration.
alter table plan add column depot_id bigint references depot(id);

update plan p set depot_id = r.depot_id from plan_run r where r.id = p.plan_run_id;

alter table plan alter column depot_id set not null;
