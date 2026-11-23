-- Revoking tokens that have already been issued.
--
-- The tokens stay signed and stateless, which is the model rather than an optimisation: a driver's
-- phone retries everything after a tunnel and a retry must not depend on server state still
-- existing. What stateless also meant, until now, is that deactivating an account only stopped the
-- next sign-in. A token already in someone's hands kept working for the rest of its eight hours,
-- which is the wrong answer for a driver who left on bad terms or a phone left in a cab.
--
-- One timestamp per account, not a list of revoked tokens. A token is accepted only if it was
-- issued at or after this instant, so writing now() into it invalidates every token that account
-- holds, in one update, with nothing to clean up afterwards. A deny list of individual token ids is
-- the other usual answer: it can revoke one token out of several, and it costs a row per revocation
-- plus a job to delete the rows once the tokens they name have expired anyway. The operation this
-- business has is "sign this account out everywhere" -- nobody knows which of a user's tokens a
-- thief is holding -- so the cheaper mechanism is also the one that answers the real question.
--
-- The price is one indexed read by username on every authenticated request. It is paid deliberately
-- and it is what makes revocation immediate; a cache in front of it would make the one property the
-- feature exists for eventually true.
alter table app_user add column tokens_valid_from timestamptz not null default now();

-- Existing rows take their own creation time rather than now(), so this migration does not sign
-- every account out on deploy. No token for an account can have been issued before the account
-- existed, so created_at is exactly the value that means "nothing has been revoked yet".
update app_user set tokens_valid_from = created_at;
