-- Idempotent: @Sql re-runs this before every test method, and tests that cannot roll back
-- would otherwise trip the unique constraint on depot.code.
-- Children first: consignment, driver and vehicle all reference depot.
delete from consignment;
delete from driver;
delete from vehicle;
delete from depot;

insert into depot (code, name, latitude, longitude, timezone) values
                                                                  ('MRL', 'Marl Hub',     51.656000, 7.090000, 'Europe/Berlin'),
                                                                  ('DTM', 'Dortmund Hub', 51.513600, 7.465300, 'Europe/Berlin');