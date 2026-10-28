# 1. Use role=1 as the single account-disable state

Status: Proposed — raised by Bundle C (centralized authorization/admin management) for A and B to confirm before building on it.

## Context

The starter schema has two overlapping ways to represent a disabled account:

- `users.role`, where role code `1` is documented as "Disabled" (checklist Table 3, 2.1.8: lockout "transitions a user to Role 1 (Disabled)").
- `users.locked`, a separate `INTEGER DEFAULT 0` column with its own "LOCK/UNLOCK" admin control and table column, unused by any checklist item.

Keeping both means two sources of truth can disagree (e.g. `locked=1` but `role=3`), and re-enabling a disabled account requires deciding what role to restore — information the schema doesn't keep once `role` is overwritten.

## Decision

Bundle C's admin tooling (`MgmtUser`) and centralized `Authorization` treat `role == 1` as the only disabled state:

- `Authorization.check()` denies any user whose role is `1`, regardless of which roles a resource allows.
- The automated lockout bundle A implements for checklist 2.1.8 should set `role = 1` on the 5th consecutive failure, not touch a separate `locked` flag.
- Admin re-enabling (`MgmtUser`'s LOCK/UNLOCK action) restores a disabled account to role `2` (Client) — the least-privileged non-disabled role — rather than attempting to recall a prior role. An Admin who needs the account at a higher role afterward uses EDIT ROLE.
- The legacy `locked` column is left in the schema (no migration) but is not read or written by this new logic.

## Consequences

- Matches the checklist's literal wording ("Role 1 (Disabled)") so the behavior is unambiguous against the grading rubric.
- Locking out a Staff/Manager/Admin account loses their specific role; this is an accepted tradeoff for having one unambiguous disabled state. The Admin can reassign the correct role immediately after re-enabling.
- Bundle A's login/lockout implementation must set `role = 1` (via `SQLite.updateUserRole`) on lockout, not a `locked` flag, so this and the admin panel agree on one mechanism.
- If this tradeoff is wrong for the team's design (e.g. roles must be preserved through a lockout), revisit by adding a `previous_role` column — flag it before bundle A starts the lockout feature.
