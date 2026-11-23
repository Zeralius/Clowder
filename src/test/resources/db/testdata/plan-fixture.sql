-- One depot and one vehicle, for tests that build plans directly instead of importing an instance.
--
-- Idempotent: @Sql re-runs this before every test method, and tests that cannot roll back would
-- otherwise trip the unique constraints on depot.code and vehicle.registration.
--
-- Children first, all the way down: plan_stop and plan_run_unassigned cascade from their parents,
-- but naming them keeps the order obvious to the next person to add a table.
delete from plan_stop_state;
delete from plan_execution_state;
delete from driver_event;
delete from plan_stop;
delete from plan;
delete from plan_run_unassigned;
delete from plan_run;
delete from consignment;
delete from driver;
delete from vehicle;
delete from depot;

insert into depot (code, name, latitude, longitude, timezone, distance_model)
values ('PLN', 'Plan Test Depot', 51.656000, 7.090000, 'UTC', 'HAVERSINE');

insert into vehicle (depot_id, registration, capacity_grams, capacity_millilitres,
                     shift_start, shift_minutes, avg_speed_kph, active)
values ((select id from depot where code = 'PLN'), 'PLAN-01',
        1000000, 1000000, '08:00', 600, 50.0, true);
