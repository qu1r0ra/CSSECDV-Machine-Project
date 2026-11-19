# Access control matrix

Status: Bundle B permissions plus the shared audit and account-management service permissions from [issue #16](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/16). These service checks use current account/session state. Interactive log search controls and the whole-application walkthrough remain under [issue #17](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/17); this is not yet a complete project-wide feature matrix.

| Feature / Resource | Unregistered | Disabled (1) | Client (2) | Staff (3) | Manager (4) | Admin (5) |
| --- | --- | --- | --- | --- | --- | --- |
| Read product catalog | No | No | Yes | Yes | Yes | No |
| Add product (fresh current-password verification) | No | No | No | Yes | Yes | No |
| Edit product (fresh current-password verification) | No | No | No | Yes | Yes | No |
| Delete product (fresh current-password verification) | No | No | No | Yes | Yes | No |
| Confirm a purchase | No | No | Yes | No | No | No |
| Read own purchase history | No | No | Yes | No | No | No |
| Read all purchase history, including uncertain-owner legacy rows | No | No | No | No | Yes | No |
| Review/search security logs | No | No | No | No | No | Yes |
| Manage user accounts | No | No | No | No | No | Yes |
| Register as Client | Yes | Yes | Yes | Yes | Yes | Yes |
| Sign-in attempt (authentication is available) | Yes | Yes | Yes | Yes | Yes | Yes |

Audit append is automatic and does not grant permission to read or manage logs. Failed sign-in and registration events are recorded for public authentication flows; protected log review and account management require a currently valid Role 5 actor, including on direct service calls. No role can permanently clear audit records. Other application features remain to be added before this matrix can be called exhaustive.
