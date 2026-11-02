# Demo test cases

Status: Bundle B cases map to [issue #6](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/6). Service behavior is exercised with controlled identity/session doubles and a real temporary SQLite database by `ant -f app/build.xml test-bundle-b`. The actual Swing walkthrough with the integrated A/C providers remains pending [issue #9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9).

| Control Group | Test Scenario | Expected Outcome |
| --- | --- | --- |
| Authentication | Open a B operation without an authenticated actor, then repeat with a Disabled (Role 1) account. | Every operation is denied and protected data is not returned. Current service checks use injected identity/session providers; verify the real A/C session behavior after integration. |
| Authorization | Attempt product management as Client and purchase as Staff; read history as Client and Manager. | Client cannot manage products; Staff cannot purchase or read history; a Client sees only the current account's purchases; a Manager can view all purchase and legacy rows. Admin and Disabled have no B privileges. |
| Data Validation | Submit names with spaces, signed stock, stock above 1,000,000, prices with excess precision/grouping/exponent notation, quantity above 1,000,000, and invalid history searches. | Each value is rejected unchanged, no partial mutation occurs, and invalid values produce timestamped audit rows. |
| Error Handling | Force the audit writer to fail during product creation and purchase confirmation. | The UI receives a generic failure; the transaction leaves no product/purchase snapshot and purchase stock remains unchanged. An unrecoverable provider/storage failure returns to the unprivileged Login panel. |
| Logging | Cause a denied catalog read, rejected input, successful product mutation, and confirmed purchase. Inspect the SQLite `logs` table. | Each event is timestamped; successful purchase audit and stock/history writes commit together. Authentication logs and Admin log-view access are verified with the integrated A/C providers. |

Automated service-level evidence and remaining manual integration checks are recorded in [TODO](TODO.md).
