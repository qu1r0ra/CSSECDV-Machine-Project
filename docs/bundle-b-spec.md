# Bundle B specification and ticket review

The settled [Bundle B specification (Issue #6)](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/6) is authoritative for B behavior, shared contracts and testing. Its live body and `ready-for-agent` label were verified after publication. The testing seam was confirmed on November 2, 2026. GitHub owns the specification; this page links it and holds the proposed ticket breakdown for review.

Owner: Christian Joseph Bunyi. These are B-only implementation tickets for his tracking and polish. Liam's A work and Roan's C work remain external dependencies under the [team handoff (Issue #4)](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/4); this proposal creates no tasks for them.

Status: **breakdown awaiting approval; no implementation tickets published and no implementation started.** After approval, publish each body below as a sub-issue of #6, establish its blocking relationship, and replace this draft with ticket links. Add only the approved execution map to #6, then verify the map against the published blockers.

## Proposed execution map

External integration gate: A's trusted session/current-password verification and C's current-account authorization, validation, transactional audit and safe-error/navigation interfaces must be reconciled with the actual shared implementation. B may develop against controlled doubles; integrated acceptance requires the real providers. The confirmed behavior contracts are in #6; availability is not established by historical claims in #4.

```text
Batch 1: B1 [product model, shared B operations/schema integration, catalog views, catalog verification and evidence]
Batch 2: B2 [same B operations/schema integration plus purchase/history views, verification and evidence] (blocked by B1)
```

The two tickets share B's operation module and schema integration, so they run sequentially. Each includes its own UI, persistence, security controls, tests and rubric evidence. There is no separate test-only or documentation-only ticket.

## B1: Secure catalog browsing and product management

### Parent

[Bundle B specification #6](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/6)

### What to build

Clients can browse the catalog, and Staff/Managers can add, edit and delete products through the same protected B operations used by Swing. Every mutation requires fresh current-password verification. Deliver a working catalog slice with exact prices, preserved data, safe failures and verifiable security behavior.

### Acceptance criteria

- [ ] Reconcile the consumed A/C interfaces and shared edit ownership before integration. Use trusted stable identity/session generation and current account state; B does not store credentials, select its own actor or duplicate shared security policy.
- [ ] Both direct operation calls and Swing enforce the parent's role table. Client browsing grants no management privileges; Unregistered, Disabled, Admin, missing/stale identities and unsupported roles are denied B access.
- [ ] Staff and Managers can add, edit and delete the intended product by stable identity. Duplicate names, missing selections, deleted products and stale state leave the catalog unchanged and refresh or explain the conflict safely.
- [ ] Each mutation binds fresh masked current-password verification to the session, operation, target and input. Failure, cancellation, changed input/target, logout, account disabling/deletion or lost role prevents the write. Temporary password buffers are cleared; A owns authentication logging/failure accounting, including preservation of the exact five-failed-login rule.
- [ ] Enforce all parent field/range/raw-length rules at the operation boundary and supplement them in the UI. Reject the entire invalid request without trimming, truncating, cleaning or rounding. Valid boundary values and exact money round-trip correctly with checked arithmetic.
- [ ] Every B database interaction uses PreparedStatements. Product/schema compatibility changes preserve the original fixture and existing working records; repeated upgrades do not lose or duplicate data. Existing history remains preserved for the subsequent purchase/history slice.
- [ ] Each successful catalog mutation and its protected success audit record commit together. Validation, authorization and verification failures use shared timestamped logging without secrets/raw invalid input or duplicate events. Audit/storage failure leaves no committed partial mutation; failure logging cannot recurse indefinitely.
- [ ] Expected conflicts keep the application usable with generic feedback. Unrecoverable failures invalidate protected state and use the safe unprivileged fallback without exposing SQL/Java diagnostics.
- [ ] Automated checks exercise the public B operations against real temporary SQLite for the complete catalog role matrix, direct-call bypass attempts, validation boundaries/exact money, stale state, re-authentication/session changes and mutation/audit rollback. Controlled doubles induce failures; repeat the relevant checks with actual A/C providers before integrated acceptance.
- [ ] A manual Swing pass verifies browsing, management visibility, masked verification, cancellation, refreshed catalog state and safe messages/navigation. Record the executed scenarios and successful Ant build.
- [ ] Update the catalog's security documentation, ACL entries and demo cases to match implemented behavior, separating vulnerability from impact and naming exact controls. Record evidence and remaining dependencies without claiming unfinished shared criteria are complete.

### Blocked by

No earlier B ticket. Actual A/C provider integration and agreed shared edit ownership are external gates to integrated acceptance, as described in the execution map and parent spec.

## B2: Atomic purchases and authorized immutable history

### Parent

[Bundle B specification #6](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/6)

### What to build

Clients confirm purchases and view their own completed purchases; Managers view all buyers' history. Purchases update stock, preserve purchase-time facts and record success atomically. History remains accurate after catalog edits/deletion, and legacy data remains preserved with honest limits.

### Acceptance criteria

- [ ] Use B1's protected B operations, stable product identity, exact-money rules and shared A/C integration. Clients alone may purchase or read their own history; Managers alone may read all history. Other prescribed roles and invalid/stale sessions fail closed in both logic and UI.
- [ ] Show the current product, quantity, unit price and exact total before purchase. Cancellation changes nothing. Invalid/oversized quantities and unavailable stock are rejected wholly; changed price or relevant product state requires fresh confirmation. Repeated submission of the same in-flight confirmation cannot create a duplicate purchase.
- [ ] Revalidate current identity/account state, role, selected product state and stock at execution. In a single transaction, conditionally decrement stock, insert one immutable purchase snapshot and append its protected success audit event. Any failed history/audit write rolls back every effect; competing requests for the last unit yield one success and nonnegative stock.
- [ ] Each new record retains stable buyer/product identity, purchase-time display names, unit price, quantity and application-generated UTC time. Totals derive from those facts; subsequent buyer/product display changes or product deletion cannot rewrite completed facts or remove history through a cascade.
- [ ] Own-history queries constrain rows by the trusted buyer before reaching the UI. Clients cannot request another buyer or receive all rows for UI filtering. Manager history is separately authorized and grants no purchase privilege.
- [ ] Preserve existing working data and the original fixture. Display unknown legacy historical prices/totals as unavailable; never substitute current prices. Expose legacy rows with uncertain buyer ownership only to authorized Managers. Repeated compatibility upgrades retain rows without duplication or fabricated facts.
- [ ] All database access uses PreparedStatements and the parent's validation, timestamped logging, generic errors and safe-fallback contracts. Failure or cancellation cannot appear successful; ordinary conflicts allow correction and unrecoverable failures remove protected access.
- [ ] Automated checks use the public B seam and real temporary SQLite to verify role/ownership isolation, validation/exact totals, stale confirmation, duplicate submission, last-unit competition, history/audit rollback, immutable history after rename/reprice/delete and repeatable legacy compatibility. Exercise actual A/C integration before acceptance; doubles alone do not establish shared compliance.
- [ ] A manual Swing pass verifies Client purchase/own-history and Manager all-history, cancellation, reconfirmation, refreshed stock/history, denied roles and safe failure navigation. Record observed results and a successful Ant build.
- [ ] Complete the B purchase/history security documentation, ACL entries and demo cases. Prepare complete demo purchases through the implemented flow; with A's compliant demo accounts and C's packaging, verify standalone launch and B scenarios from a separate distribution directory. Record B evidence and any remaining team-wide gaps without claiming global compliance.

### Blocked by

B1: Secure catalog browsing and product management. It supplies the catalog behavior, stable identity/exact money and shared B operations/schema area used by this slice. The actual A/C integration and final account/packaging dependencies also remain external acceptance gates.
