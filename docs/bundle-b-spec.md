# Bundle B: products, purchases and history

Status: draft specification for review. Owner: Christian Joseph Bunyi. The user confirmed immutable purchase history and the catalog re-authentication policy on November 2, 2026. Implementation has not begun; implementation tasks follow the settled spec.

## Problem Statement

The supplied application does not safely complete product management, purchasing or purchase-history access. Product controls are exposed without effective role enforcement, purchase handlers are incomplete, and history totals use current product prices. Editing or deleting a product can change the apparent past transaction or break history rendering.

Christian owns Bundle B and needs a precise, verifiable description of its behavior and its dependencies on Liam's authentication work and Roan's shared security work. Allocation alone does not define these integration requirements.

## Solution

Staff and Managers manage products; Clients confirm purchases and view only their own history; Managers view all purchase history. Every operation enforces the prescribed roles in application logic and the UI. Catalog mutations require fresh password verification. Ordinary purchases use the authenticated session plus explicit product/quantity/price/total confirmation.

Purchase records preserve purchase-time facts. A purchase commits its stock change, history record and success audit record together, or changes nothing. Invalid input, stale data, denied access and unexpected failures produce safe outcomes without exposing technical details.

### Requirement authority

The [assignment specification and rubric](https://github.com/qu1r0ra/CSSECDV-Machine-Project/blob/main/docs/specifications.md) and [grading checklist](https://github.com/qu1r0ra/CSSECDV-Machine-Project/blob/main/docs/checklist.md) are authoritative. Course practices guide unspecified choices and do not override required functionality. The [course audit](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/4#issuecomment-6569339054) records the lecture evidence. Numeric limits, exact-money storage, transactional auditing and the specific catalog re-authentication scope below are project choices, not invented rubric requirements.

| B operation | Allowed roles | Additional condition |
| --- | --- | --- |
| Read catalog for an authorized operation | Client, Staff, Manager | Grants no purchase or edit privilege by itself |
| Add, edit or delete product | Staff, Manager | Fresh current-password verification for each mutation |
| Purchase | Client | Confirm product, quantity, unit price and total |
| Read own purchase history | Client | Query is scoped to the authenticated buyer |
| Read all purchase history | Manager | Includes all buyers; grants no purchase privilege |

Unregistered, Disabled and Admin have no B access. Staff cannot read purchase history. Admin is not a superadmin. Missing or stale identities and unsupported role values are denied.

## User Stories

1. As a Staff member, I want to add a valid product so that Clients can purchase available stock.
2. As a Manager, I want the same catalog-management capabilities as Staff so that I can manage the business catalog.
3. As a Staff member or Manager, I want to update a selected product so that its name, price and stock reflect current availability.
4. As a Staff member or Manager, I want to delete a selected product so that it is no longer offered for purchase.
5. As a catalog operator, I want to verify my current password before each add/edit/delete so that temporary access to my session does not authorize catalog changes.
6. As a catalog operator, I want cancelling or failing that verification to leave the catalog unchanged so that an unapproved mutation cannot slip through.
7. As a Client, I want to see the catalog without management controls so that I can choose an available product.
8. As a Client, I want to confirm the product, quantity, unit price and total so that I understand the purchase before committing it.
9. As a Client, I want to cancel the confirmation without changing stock or history so that reviewing a purchase has no side effects.
10. As a Client, I want a changed price or stale product to require refresh/reconfirmation so that I do not unknowingly accept new terms.
11. As a Client, I want a successful purchase to reduce stock and create exactly one purchase record so that the catalog and history agree.
12. As a Client competing for the last available unit, I want only a valid in-stock purchase to succeed so that stock cannot become negative.
13. As a Client, I want only my own purchase history returned so that another buyer's information remains private.
14. As a Manager, I want to view all buyers' purchase history so that I can review completed transactions.
15. As a buyer or Manager, I want purchase-time product details and totals to remain accurate after catalog edits/deletion so that the history remains trustworthy.
16. As a user, I want invalid or oversized input rejected entirely so that malformed requests cannot be converted into unintended valid actions.
17. As a user, I want exact decimal prices and totals so that binary rounding does not change displayed or stored amounts.
18. As a user, I want stale selection, unavailable stock and duplicate in-flight submission handled safely so that I do not affect the wrong product or buy twice accidentally.
19. As a user whose account is disabled or session ends, I want protected operations to stop immediately so that an old screen cannot preserve access.
20. As a user, I want understandable, generic failure messages and a safe fallback after unrecoverable errors so that the application remains secure and usable.
21. As an Administrator, I want B's required security and mutation events recorded through the protected audit mechanism so that activity can be investigated without granting me B privileges.
22. As a maintainer, I want legacy records preserved without fabricated prices or unsafe buyer associations so that an upgrade does not create false history or disclose data.
23. As a Bundle B owner, I want behavior-level verification and demo evidence so that I can double-check my implementation and polish it against the rubric.
24. As a teammate, I want explicit shared contracts and integration ownership so that B can connect to A/C without competing security implementations or overlapping rewrites.

## Implementation Decisions

### B module and interface

Expose B's behavior through one product/purchase module: catalog listing, add/edit/delete, confirmed purchase, own history and Manager history. Swing handlers collect input and display outcomes; the module resolves the trusted actor and performs centralized authorization, validation and transaction handling. Keep low-level product/history database operations internal to this path so a public unguarded write cannot bypass the checks.

B callers do not supply authoritative buyer identity, role, timestamp, final charged price or allowed-role lists. Select products by stable ID rather than editable name or table-row position. Use an expected version or equivalent state check so a stale edit, deletion or purchase cannot silently act on changed state.

### Interfaces consumed from A/C

The following are required behavior contracts, not claims that particular Java declarations already exist. Concrete declarations must be reconciled with the actual shared implementation before callers are written. Historical interface names in [Issue #4](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/4) remain unverified.

| Provider | Interface B consumes | Invariants and failure behavior |
| --- | --- | --- |
| A: session | Trusted stable user identity and session generation | No password/hash in the exposed identity. Logout invalidates the session. B cannot choose the current actor. |
| C: authorization | Operation plus trusted identity and resource context | Central role policy checks current account state and ownership. Missing, disabled, deleted, stale or unauthorized identities fail closed. |
| A: re-authentication | Current credentials checked for one pending catalog mutation | Mask entry; reject failure/cancellation; verify immediately before execution; clear temporary password buffers. No reusable UI boolean granting privilege. |
| C: validation | Shared reject-by-default rules accepting B field constraints | UI checks supplement operation-level checks. Invalid requests are rejected wholly, without cleaning, trimming, truncating or rounding them into acceptance. |
| C: audit | Trusted actor, operation, target, outcome and application timestamp | Protected Admin-only access; no credentials, hashes or raw invalid input. Success events can join the mutation's transaction. |
| C: errors/navigation | Safe outcomes and unprivileged fallback | Ordinary rejection/cancellation leaves data unchanged. Unrecoverable errors invalidate protected state and route to safe fallback, without raw SQL/Java details. |

Re-authentication is bound to the current session, pending operation, target and input. Changing any of these invalidates the pending approval. Recheck authorization and relevant product state before writing. A owns credential verification and authentication failure accounting; B does not add a credential store or counter. Integration must preserve the checklist's exact five-consecutive-failed-login behavior. Re-authentication failures reject the operation and are logged through A; any shared-policy mismatch must be resolved before claiming integrated compliance.

### Field and money rules

These reversible defaults fill unspecified handout values. Enforce the same limits at the B operation entry, even when called without the UI.

| Field | Accepted value | Maximum raw length |
| --- | --- | --- |
| Product name | 1-64 ASCII letters/digits; retain the starter's unique-name rule | 64 |
| Stock | Integer 0-1,000,000 | 7 |
| Unit price | Decimal 0.01-1,000,000.00; at most two fractional digits | 10 |
| Purchase quantity | Integer 1-1,000,000 and no greater than available stock | 7 |

Numeric input uses unsigned decimal digits, with a decimal point permitted only for price. Reject signs, separators, exponents, surrounding whitespace, non-finite numbers, excess precision and overflow. A supplies password length/policy rules for its verification interface. Validate stable IDs and all business constraints as well as field syntax.

Store monetary values as 64-bit integer minor units; parse decimal input exactly and use checked arithmetic for totals. Do not calculate money with binary floats or silently round invalid precision. Generate unambiguous UTC purchase timestamps inside the application; audit events use the same clock convention.

### Catalog and purchase state

Each catalog mutation requires current role authorization, fresh credential verification and valid unchanged target/input. Cancellation, duplicate name, no selection, unavailable product, stale state or verification failure produces no catalog change.

A Client purchase starts with a confirmation based on the selected product's current name, quantity, unit price and total. At execution, revalidate the account, role, product state and available stock. In one database transaction, conditionally decrement stock, insert one immutable purchase snapshot and append the success audit record. Any failure rolls all three effects back. Two competing requests for the last unit cannot both succeed.

A changed price or other relevant product state requires fresh confirmation. Prevent a second submission of the same in-flight confirmation; a deliberate later purchase uses a new confirmation. A failed request or cancelled confirmation must never appear successful.

### History and schema compatibility

History stores purchase-time stable buyer/product identity, buyer/product display names, unit price, quantity and timestamp. Read totals from those immutable facts. Do not join to the live catalog to reconstruct the price or require the product still to exist. Catalog deletion must not cascade into history deletion.

Scope own-history queries by trusted buyer ID before rows reach the UI. Manager history is a separately authorized operation. Client requests cannot substitute another buyer, and fetching every buyer's history for UI filtering is insufficient.

Preserve the original course fixture and existing working data during schema upgrades; no silent reset. Legacy history has no historical unit price: retain that fact as unavailable rather than using today's price or inventing totals. Only associate a legacy row with a Client when ownership can be established safely; otherwise expose it solely through authorized Manager history. Upgrades must be repeatable without losing or duplicating data. Prepared demo purchases come through the completed purchase flow and have complete snapshots.

### Logging and failures

Use PreparedStatements for every B database interaction. Record required validation failures, authorization denials and authentication outcomes through the shared mechanisms. Also record product mutation and purchase outcomes as the course-informed audit coverage; avoid duplicate events where A/C already records them.

Mutation success and its success audit record commit together. Audit failure prevents the mutation from being reported or committed as successful. Record failure events outside the rolled-back transaction when storage remains available; unavailable logging must not recursively generate unbounded failures. Generic user feedback must not reveal SQL, stack traces, credentials or internal diagnostics. Expected validation/stock conflicts leave the user able to correct the request; unrecoverable errors invoke the safe unprivileged fallback.

### Integration ownership and evidence

B owns product/history models, operations, views, schema requirements and their checks. C integrates shared database connection/error/audit changes and role navigation; A supplies authenticated-session and re-authentication hooks. B supplies the corresponding wiring rather than rewriting shared modules independently. Agree on shared edit ownership before parallel implementation.

Each B feature supplies its security-documentation entry with a clear vulnerability/impact distinction and exact implemented control, its ACL row and its demo scenarios. B owns its own verification and polish. Compliant demo accounts come from A; C integrates final packaging. External A/C deliverables are dependencies, not implementation tasks assigned by this spec.

## Testing Decisions

**Proposed seam for confirmation:** exercise the public B product/purchase module used by Swing callers, with a real temporary SQLite database. This is the highest practical automated seam that can verify role checks, persistence and transaction outcomes together. The starter exposes low-level database calls directly from views; no existing automated behavioral test suite was found. Introducing this one B module is necessary to test complete operations independently of Swing dialogs.

Prefer observable outcomes: returned history, stock, purchase records, allowed/denied operations and audit events. Avoid tests of private helpers or SQL strings. Use controlled A/C doubles only to induce session changes, verification failures and storage failures; those checks do not establish real shared-module compliance. Repeat the important scenarios with the actual A/C integration before acceptance.

| Observable behavior | Required verification |
| --- | --- |
| Role and ownership rules | Exercise every B operation for all prescribed roles; attempt cross-buyer reads and direct calls bypassing UI visibility. |
| Session and re-authentication | Wrong password, cancellation, changed target/input, logout, disabled/deleted account and changed role prevent mutation. Password entry is masked in the manual UI pass. |
| Input and exact money | Check valid endpoints and immediately invalid values for every bound, malformed/oversized fields, duplicate names, excess precision and exact totals. |
| Stale data and confirmation | A changed price requires reconfirmation; stale edit/delete and missing selections cannot affect another product. |
| Atomicity and competing purchases | Competing requests for the last unit yield one success; failed history/audit writes leave no stock/history/success-event partial state. |
| Stable history | Renaming, repricing or deleting the product leaves completed purchase facts and totals unchanged. |
| Legacy data | Upgrades preserve rows, do not fabricate prices and do not expose uncertain buyer associations to a Client; repeated upgrade is safe. |
| User-visible failures | Manual Swing pass verifies confirmations, cancellation, role visibility, refreshed stock/history, generic messages and unrecoverable safe fallback. |
| Delivery and rubric evidence | Ant build; standalone launch from a separate distribution directory using deliberately prepared secure data; B documentation, ACL and live-demo cases match executed behavior. |

Checklist coverage: protected access and secure failure (2.1.1-2.1.2), masked re-authentication and critical operations (2.1.7, 2.1.10), centralized authorization and role/UI behavior (2.2.1-2.2.4), validation and SQL (2.3.1-2.3.4), errors/fallback (2.4.1-2.4.4), and B's contributions to authentication/validation/denial logs (2.5.2-2.5.4). B verifies that it cannot obtain Admin-only log access (2.5.1); C implements that feature. This mapping does not claim global completion of shared criteria.

## Out of Scope

- A's authentication/credential implementation, C's authorization/Admin/log-management implementation, and assigning new implementation tasks to either teammate.
- B access for Admin, Staff history access, or purchasing by Staff/Manager; these would conflict with the prescribed role boundaries.
- Payments, shipping, refunds, order fulfillment, multi-currency support, tax calculations or editable completed purchase records.
- Browser cookies, HTTPS infrastructure or a web rewrite of the Swing starter.
- Guessing missing historic prices, silently resetting existing data or claiming security compliance from starter/template files.
- Implementation task breakdown, code changes or application test execution before the specification and testing seam are settled.

## Further Notes

The general project setup is complete; the remaining planning work is to confirm the proposed test seam, publish this spec to the project tracker, then derive B-only implementation tasks and acceptance criteria. No task count or execution map is approved by this spec draft.

The two confirmed product decisions do not require an ADR under the current criteria: immutable purchase facts are expected behavior, and the re-authentication policy is reversible. The rationale belongs here without a separate ADR.
