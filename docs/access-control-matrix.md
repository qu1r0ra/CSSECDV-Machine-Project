# Access control matrix

Status: Bundle C rows (navigation/admin/log features) recorded below, enforced by `Controller/Authorization.java` and `View/Frame.java`. Bundle A (login/register) and Bundle B (products/history/purchasing) rows are still pending from their owners.

Use the [required format](specifications.md#access-control-matrix) and [role descriptions](specifications.md#authorization). Add every system feature/resource and enter **Yes** or **No** for each user type. Check both application enforcement and UI visibility before recording completion in [TODO](TODO.md).

| Feature / Resource | Unregistered | Disabled (1) | Client (2) | Staff (3) | Manager (4) | Admin (5) |
| --- | --- | --- | --- | --- | --- | --- |
| View Admin Home / nav button | No | No | No | No | No | Yes |
| View Manager Home / nav button | No | No | No | No | Yes | No |
| View Staff Home / nav button | No | No | No | Yes | No | No |
| View Client Home / nav button | No | No | Yes | No | No | No |
| Manage users (edit role, delete, change password) | No | No | No | No | No | Yes |
| Disable / re-enable accounts | No | No | No | No | No | Yes |
| View security logs | No | No | No | No | No | Yes |
| Clear security logs | No | No | No | No | No | Yes |
