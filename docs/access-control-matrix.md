# Access control matrix

This matrix maps each application feature and protected resource to the six assignment user states. It reflects [the role descriptions](specifications.md#authorization) and current authorization in `AccessControl`, `UserAdminService`, `BundleBService`, and `SqliteSecurityAudit`. “Yes” means the signed-in role may perform the operation; a role cannot gain permission by calling a service directly.

| Feature / Resource | Unregistered | Disabled (1) | Client (2) | Staff (3) | Manager (4) | Admin (5) |
| --- | --- | --- | --- | --- | --- | --- |
| Open public login form / submit a login attempt | Yes | Yes | Yes | Yes | Yes | Yes |
| Sign in successfully with an enabled account and correct credential | No | No | Yes | Yes | Yes | Yes |
| Open public registration after logging out; new account receives Client (2) | Yes | Yes | Yes | Yes | Yes | Yes |
| Log out of the current session | No | No | Yes | Yes | Yes | Yes |
| Enter Admin home panel | No | No | No | No | No | Yes |
| Enter Manager home panel | No | No | No | No | Yes | No |
| Enter Staff home panel | No | No | No | Yes | No | No |
| Enter Client home panel | No | No | Yes | No | No | No |
| View the last login attempt timestamp and status after successful login | No | No | Yes | Yes | Yes | Yes |
| Read product catalog | No | No | Yes | Yes | Yes | No |
| Add a product | No | No | No | Yes | Yes | No |
| Edit a product | No | No | No | Yes | Yes | No |
| Delete a product | No | No | No | Yes | Yes | No |
| Reverify current password before product add/edit/delete | No | No | No | Yes | Yes | No |
| Confirm a product purchase | No | No | Yes | No | No | No |
| Read own purchase history | No | No | Yes | No | No | No |
| Read all users' purchase history | No | No | No | No | Yes | No |
| List user accounts and their non-secret role/status fields | No | No | No | No | No | Yes |
| Change another account's role, including re-enable a Role 1 account | No | No | No | No | No | Yes |
| Lock or unlock another account | No | No | No | No | No | Yes |
| Delete another account | No | No | No | No | No | Yes |
| Change another account's password | No | No | No | No | No | Yes |
| Reverify Admin password for sensitive role, deletion, or password changes | No | No | No | No | No | Yes |
| Review, filter, or show all security audit records | No | No | No | No | No | Yes |
| Permanently delete security audit records | No | No | No | No | No | No |
| Generate security audit events for auth, validation, denials, account and business changes | Yes | Yes | Yes | Yes | Yes | Yes |

Login and registration are public routes. Existing users return to the public route through logout before starting another registration. A Disabled account may reach the public login/register screen but cannot create an authenticated session or access a protected home; only an Admin can restore its role. The Admin is not a super-admin and has no product, purchase, or purchase-history privileges.

Audit append is automatic and does not grant permission to read logs. Admin audit review is read-only: the Admin may search or show all records, but no role has an application operation for permanent audit deletion. Critical account and product mutations enforce current-password verification in their service before changing state.

Coverage evidence: the A and B suites exercise direct service authorization and role rules; the AssertJ Swing acceptance suite checks each valid role's home navigation, the Client's hidden product mutation controls, a denied direct product mutation, and the Admin's audit review. The aggregate command and result are in [TODO.md](TODO.md).
