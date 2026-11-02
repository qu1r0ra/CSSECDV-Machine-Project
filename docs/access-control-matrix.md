# Access control matrix

Status: Bundle B permission mapping from [issue #6](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/6). The service role checks and test-double behavior are implemented; live Swing visibility and actual A/C provider integration remain to be verified under [issue #9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9). This is the B feature matrix, not the whole application's completed access matrix.

| Feature / Resource | Unregistered | Disabled (1) | Client (2) | Staff (3) | Manager (4) | Admin (5) |
| --- | --- | --- | --- | --- | --- | --- |
| Read product catalog | No | No | Yes | Yes | Yes | No |
| Add product (fresh current-password verification) | No | No | No | Yes | Yes | No |
| Edit product (fresh current-password verification) | No | No | No | Yes | Yes | No |
| Delete product (fresh current-password verification) | No | No | No | Yes | Yes | No |
| Confirm a purchase | No | No | Yes | No | No | No |
| Read own purchase history | No | No | Yes | No | No | No |
| Read all purchase history, including uncertain-owner legacy rows | No | No | No | No | Yes | No |

Automatic audit append is performed only as part of eligible B operations or rejected/denied attempts; it is not a user-readable feature in this B module. The system-wide Admin log viewer, user management, authentication, registration, and other Bundle C resources are outside this matrix and remain pending their owners' integration and verification.
