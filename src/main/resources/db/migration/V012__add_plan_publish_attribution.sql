-- Who published this plan.
--
-- The row has said it was published and when since M4, but not by whom. A published plan is the
-- document a driver works from, and publishing one supersedes the version somebody else published,
-- so "who did that?" is a question the artifact should answer itself rather than by guesswork from
-- timestamps and shift rosters.
--
-- The username, not a foreign key to app_user, for three reasons in order of weight. It is readable
-- by anyone who may read the plan, whereas resolving an id needs the admin-only user endpoint -- an
-- id would be an audit trail only an admin could follow. It keeps in the schema the boundary the
-- ArchUnit rules keep in the code: planning records a fact about the request it served rather than
-- taking a reference into security's data. And it is unambiguous here, because usernames are unique,
-- accounts are deactivated rather than deleted, and nothing renames one. If a rename endpoint is
-- ever added this column stops being an identity and becomes a snapshot of one, which is still the
-- right thing for an audit trail but is a different claim.
alter table plan add column published_by varchar(64);

-- No row may be PUBLISHED without a publisher from here on, which is the invariant worth having and
-- the same kind of guarantee as uq_plan_published: not a convention the application remembers.
--
-- NOT VALID deliberately. Rows published before this migration have nobody to name and backfilling
-- one would be a lie, so they are exempt from the initial scan while every row written afterwards is
-- checked.
--
-- It covers PUBLISHED only and not SUPERSEDED, which is not an oversight. NOT VALID exempts existing
-- rows from the scan but not from later updates, so a constraint that also covered SUPERSEDED would
-- make the first replan of a legacy vehicle-day fail at the moment it superseded the old row.
alter table plan
    add constraint plan_published_by_check
    check (status <> 'PUBLISHED' or published_by is not null) not valid;
