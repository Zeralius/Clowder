-- Idempotent: @Sql re-runs this before every test method, and tests that cannot roll back
-- would otherwise trip the unique constraint on depot.code.
-- Children first: plans reference vehicle and consignment, which reference depot.
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

insert into depot (code, name, latitude, longitude, timezone, distance_model) values
                                                                  ('MRL', 'Marl Hub',     51.656000, 7.090000, 'Europe/Berlin', 'HAVERSINE'),
                                                                  ('DTM', 'Dortmund Hub', 51.513600, 7.465300, 'Europe/Berlin', 'HAVERSINE');