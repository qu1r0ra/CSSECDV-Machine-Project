# Access control matrix

The matrix shows which roles can use each protected feature. `AccessControl`, `UserAdminService`, `BundleBService`, and `SqliteSecurityAudit` enforce these permissions in the application and service layer. A user cannot obtain a permission by calling a service directly.

| Feature / Resource | Unregistered | Disabled (1) | Client (2) | Staff (3) | Manager (4) | Admin (5) |
| --- | --- | --- | --- | --- | --- | --- |
| Sign in successfully with an enabled account and correct credential | No | No | Yes | Yes | Yes | Yes |
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

Public routes outside the protected-resource matrix: anyone can open the login form and submit an attempt, and anyone can open registration and create a Client account. Existing users return to the public route through logout before starting another registration. A Disabled account may reach the public login/register screen but cannot create an authenticated session or access a protected home; only an Admin can restore its role. The Admin is not a super-admin and has no product, purchase, or purchase-history privileges.

Audit append is automatic and does not grant permission to read logs. Admin audit review is read-only: the Admin may search or show all records, but no role has an application operation for permanent audit deletion. Critical account and product mutations enforce current-password verification in their service before changing state.

The A and B tests exercise role rules and direct service calls. The Swing acceptance suite checks role-specific home panels, Client product controls, a denied direct mutation, and Admin audit review.
