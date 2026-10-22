# Secure Web Development Case Project Checklist

De La Salle University · College of Computer Studies · Computer Technology Department · Rev. 02-2026.

Cleaned conversion of **Machine Project Checklist - Security Svcs.pdf** (2 pages). Requirements and identifiers are retained; repeated page furniture and blank group/date/section fields are omitted. Source wording such as “Website” and “site-wide” is retained. The [specification](specifications.md) describes the Java desktop application.

Source SHA-256: `dd6f365e0408951165c05b59d8496b5ffa55ccce95092aa3c8c80a7fea2ac9a9`.

Each scored item receives **Complete (2)**, **Incomplete (1)**, or **Missing (0)**. Total: **58 points** across 29 scored items. [TODO](TODO.md) tracks readiness and evidence; this file preserves the assignment criteria.

## 1.0 Pre-demo requirements: account setup

Accounts must be created before the actual demo.

### 1.1 Accounts (at least 1 per type of user)

| ID | Requirement |
| --- | --- |
| 1.1.1 | Website Administrator Account: A functional account assigned Role 5 for system management. |
| 1.1.2 | Internal Staff/Manager Account: Functional accounts assigned Roles 3 or 4 for business logic testing. |
| 1.1.3 | Standard Customer Account: A functional account assigned Role 2 to demonstrate default privileges. |

## 2.0 Demo requirements

### 2.1 Authentication

| ID | Requirement |
| --- | --- |
| 2.1.1 | The application shall require authentication for all pages and resources, except those specifically intended to be public such as Login and Register. |
| 2.1.2 | All authentication controls shall fail securely, defaulting to a “deny” state in the event of an error. |
| 2.1.3 | The application shall store only cryptographically strong, one-way salted hashes of passwords within the database. |
| 2.1.4 | Authentication failure responses shall be generic to prevent account enumeration. |
| 2.1.5 | The registration logic shall enforce a strict password policy (uppercase, lowercase, digits, and symbols). |
| 2.1.6 | The application shall enforce a mandatory minimum and maximum password length during account creation. |
| 2.1.7 | Password entry shall be obscured on the user's screen using dots or asterisks in all UI components. |
| 2.1.8 | The application shall enforce an automated account lockout (Role 1) after exactly five consecutive failed login attempts. |
| 2.1.9 | The application shall report the timestamp and status of the last account use upon a successful login. |
| 2.1.10 | The application shall re-authenticate users immediately prior to performing critical operations like a password change. |

### 2.2 Authorization/access control

| ID | Requirement |
| --- | --- |
| 2.2.1 | The system shall use a single site-wide component to verify access authorization for every request. |
| 2.2.2 | Application logic and UI visibility must strictly comply with the established role descriptions. |
| 2.2.3 | Authorization mechanisms shall fail securely, ensuring unauthorized users gain no accidental privileges during system errors. |
| 2.2.4 | The system shall ensure users are strictly directed to their corresponding Home panels based on their assigned Role Code. |

### 2.3 Data validation

| ID | Requirement |
| --- | --- |
| 2.3.1 | All validation failures shall result in total input rejection; the use of sanitization or “cleaning” is strictly prohibited. |
| 2.3.2 | The application shall validate that all numerical data (e.g., Role codes 1–5) falls within established ranges. |
| 2.3.3 | Every input field shall be validated against a maximum character length to prevent buffer or database overflows. |
| 2.3.4 | Parameterized Queries (PreparedStatements) for all database interactions to block SQL Injection attacks. |

### 2.4 Error handling

| ID | Requirement |
| --- | --- |
| 2.4.1 | Configure the system to prevent the display of stack traces or detailed debugging information to the end-user. |
| 2.4.2 | Replace technical SQLite or Java exceptions with user-friendly, non-descriptive error messages. |
| 2.4.3 | Wrap all controller and database methods in logic that prevents application crashes during unexpected events. |
| 2.4.4 | In the event of an unrecoverable error, the system shall redirect the user to a safe, unprivileged panel. |

### 2.5 Logging

| ID | Requirement |
| --- | --- |
| 2.5.1 | Ensure that the functionality to view and manage security logs is accessible exclusively to the Administrator (Role 5). |
| 2.5.2 | Record a timestamped log of all input validation failures as potential security threats. |
| 2.5.3 | Record a timestamped log of all successful and failed authentication attempts. |
| 2.5.4 | Log every instance where a user attempts to access a resource that exceeds their assigned role privileges. |

**TOTAL: /58**
