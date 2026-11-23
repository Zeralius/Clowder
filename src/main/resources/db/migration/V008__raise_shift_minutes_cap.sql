-- Decision D6: the Solomon type-2 instances have a planning horizon of roughly 3390 minutes, so the
-- 2880 cap from V002 made more than half the benchmark set impossible to import through the API. The
-- solver never cared; the check constraint did.
--
-- 10080 is one week. It is still a sanity bound rather than no bound at all: a shift measured in
-- weeks is a data entry mistake, and a column with no ceiling is how a typo becomes a route that
-- takes a year.
alter table vehicle drop constraint if exists vehicle_shift_minutes_check;

alter table vehicle
    add constraint vehicle_shift_minutes_check
    check (shift_minutes > 0 and shift_minutes <= 10080);
