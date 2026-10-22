# CSSECDV Machine Project Specifications

Cleaned conversion of the course-provided **Machine Project Specifications - Security Svcs.pdf** (8 pages). Paragraphs and tables retain the assignment content; page breaks and decorative UI screenshots are omitted, and obvious typographical errors are corrected. Implementation observations belong in the [development guide](development.md); current evidence belongs in [TODO](TODO.md).

Source SHA-256: `b43cf7ac422a500a557992285bc830e6dd2f7082e725a02a14028ef72b283700`.

## Introduction

As a software security expert, you and your team have been hired by SECURITY Svcs, a startup software security firm which would be specializing in developing and selling security solutions to various clients. They have recently been in contact with your Instructor, who has endorsed your team to take on this task. With your knowledge and understanding on security and how to implement secure applications, modify the program by implementing good security practices that you have observed are needed by the application. For this milestone, only focus on the ones which have been discussed during the class; you may, however, add in more security functionalities as you see fit.

The system of SECURITY Svcs is implemented using the Java programming language using the NetBeans IDE. As the team in charge of implementing their security features, you have the option to use other IDEs; however, you may have to import the SQLite library again for your chosen IDE. After implementing your solution, try to run the program again in NetBeans IDE since SECURITY Svcs would be using that IDE to continue their application. To help you understand their application, a snippet of the file structure is as follows.

### Table 1. Application file structure

| Component | File | Description |
| --- | --- | --- |
| Controller | Main.java | Driver class that initializes the application. |
| Controller | SQLite.java | SQLite driver to be used to access the database in database.db. |
| Model | User | Class reference to the users table in the database. |
| View | AdminHome.java | JPanel — contains functionalities for the Admin role. |
| View | ClientHome.java | JPanel — contains functionalities for the Client role. |
| View | Frame.java | JFrame — contains the view panels of the entire application and has a direct reference to Main.java to call SQLite.java methods. Contains HomePnl, a JPanel where authorization would be implemented. |
| View | Login.java | JPanel — contains the first view of the application, where the user logs in to the system to be authenticated. |
| View | ManagerHome.java | JPanel — contains functionalities for the Manager role. |
| View | Register.java | JPanel — contains the view where new users may register to obtain role code 2, designating client privilege. |
| View | StaffHome.java | JPanel — contains functionalities for the Staff role. |

For the current iteration, the application follows the Model-View-Controller framework and has three pages: the Login Page, Registration Page, and Home Page. The functionality for moving from one page to another has already been implemented.

The Login Page contains fields for USERNAME and PASSWORD input. It also contains REGISTER and LOGIN buttons. The application implements a database service using SQLite Database, which allows your team to use the application without installing MySQL Server or Workbench. The database is the database.db file in the software's home directory.

As of the current iteration, the application has no implementation for verifying user authenticity. The default users for testing are defined in Main.java, with their corresponding credentials and role codes.

The Registration Page contains USERNAME, PASSWORD, and CONFIRM PASSWORD fields. It contains a BACK button to redirect the user to the Login Page and a REGISTER button to insert credentials and create an account. Once a user completes the registration fields, the user receives role code 2, designating a registered user with client privilege.

The Home Page contains the main functionality of the application. A user accesses functionalities available to their role through this view. The application currently does not restrict access based on role; a user should only be able to access their home page according to their role.

Users are stored in the database with the following fields, and user types have the following role codes.

### Table 2. Database table attributes

| Field name | Data type |
| --- | --- |
| Id | Integer Auto Increment |
| Username | String |
| Password | String |
| Role | Integer |

### Table 3. Role codes

| Role | Code |
| --- | --- |
| Administrator | 5 |
| Manager | 4 |
| Staff | 3 |
| Client | 2 |
| Disabled | 1 |

## Security requirements

### Authentication

For the Authentication portion, verify user identity and protect credentials through the following mandatory controls:

- **Secure Credential Storage:** Ensure passwords are never stored in plain text by implementing cryptographically strong, one-way salted hashes within the SQLite database.
- **Generic Feedback:** Configure the login interface to provide a single, generic “Invalid username and/or password” message for all failed attempts to prevent account enumeration.
- **Brute Force Mitigation:** Implement an automated account lockout policy that transitions a user to Role 1 (Disabled) after five consecutive failed login attempts.
- **Visual Privacy:** Utilize masked input fields, such as dots or asterisks, for all password entries to protect users from shoulder-surfing and visual theft.
- **Contextual Awareness:** Report the timestamp and status of the last login attempt to the user immediately upon successful entry into their dashboard.

### Authorization

For the Authorization portion, enable or disable functions and permissions for each user type based on the following descriptions:

- **Unregistered:** A user who does not have an account and needs to register first to gain access to the system's functions.
- **Disabled:** A user who has incorrectly logged in multiple times, making the account inaccessible. The user must communicate with the Admin in person to re-enable the account.
- **Client:** The default permission of a registered user. This user can only purchase products and view their own purchase history.
- **Staff:** Manages the organization's products. This user can add, edit, and delete products.
- **Manager:** Can add, edit, and delete products, and view the purchase history of all users.
- **Admin:** Manages system users and logs. This user is not the super admin.

Implement the corresponding functionalities while following best practices. Input fields, buttons, and action listeners are already mostly present; mostly the controller and database functionalities, and possibly filters, need to be configured.

### Data validation

Data validation is the first line of defense against injection attacks (SQLi) and Cross-Site Scripting (XSS). Ensure the integrity of all incoming data by enforcing a strict “Reject-by-Default” policy:

- **Input Rejection:** Reject any input that fails validation entirely. “Sanitizing” or “cleaning” suspicious data is strictly prohibited to prevent bypasses.
- **Constraint Enforcement:** Validate all inputs against strictly defined requirements for data type, numerical range (for example, role codes 1–5), and maximum character length.
- **Whitelisting:** Implement regex-based filters to ensure usernames and other text fields contain only authorized alphanumeric characters.
- **Password Policy Enforcement:** Enforce a strict policy requiring a combination of uppercase letters, lowercase letters, numerical digits, and special symbols.

### Error handling

Proper error handling ensures an attacker cannot learn about the system's internal structure through error messages. Manage application failures without leaking internal system intelligence:

- **Debug Suppression:** Configure all try-catch blocks to prevent displaying stack traces or detailed debugging information to the end user.
- **Generic Alerting:** Replace technical SQLite or Java exceptions with user-friendly, non-descriptive error messages that maintain the security of the application's architecture.
- **Failsafe:** Ensure the application remains stable and redirects users to a safe, unprivileged state in the event of an unhandled exception.

### Logging

Logging provides the audit trail needed to identify how a breach occurred or detect a brute-force attack in progress. Maintain a comprehensive audit trail to ensure accountability and forensic readiness:

- **Event Auditing:** Record timestamped logs of all successful and failed authentication attempts, and all input validation failures.
- **Access Control Violation Logs:** Log every instance where a user attempts to access a resource or panel (Admin, Manager, etc.) that exceeds their assigned role privileges.
- **Restricted Review:** Ensure viewing and managing security logs is accessible exclusively to the Administrator (Role 5).

## Deliverables and project demo requirements

### Security documentation

Document details of the security modifications made to the application using this format.

#### Table 4. Security documentation format

| Security Requirement / Issue | Added / Modified File/s | Vulnerability | Impact | Security Control |
| --- | --- | --- | --- | --- |
| Checklist item | Java classes and other files added or modified | Vulnerability addressed to reduce risk | Scenario if the vulnerability is exploited | Detailed description of the logic, algorithms, or methods used |

Working document: [security-documentation.md](security-documentation.md).

### Access control matrix

Provide a table mapping every system feature to the specific roles (2–5) authorized to use it. The prescribed table also includes Unregistered and Disabled (1).

#### Table 5. Access control matrix format

| Feature / Resource | Unregistered | Disabled (1) | Client (2) | Staff (3) | Manager (4) | Admin (5) |
| --- | --- | --- | --- | --- | --- | --- |
| Feature | Yes / No | Yes / No | Yes / No | Yes / No | Yes / No | Yes / No |

Working document: [access-control-matrix.md](access-control-matrix.md).

### Test cases

Provide a summary of test cases to be used during the live project demo.

#### Table 6. Test cases format

| Control Group | Test Scenario | Expected Outcome |
| --- | --- | --- |
| Authentication / Authorization / Data Validation / Error Handling / Logging | Description of the scenario, including needed input | Description of how the application responds to the scenario |

Working document: [demo-test-cases.md](demo-test-cases.md).

### Source code

Submit the complete source code folder and a distribution folder containing the built program with the executable JAR, all dependencies, and the database file. The executable must be able to run on its own.

## Rubric for grading

### Table 7. Rubric for grading

The score bands below reproduce the published values, including their overlapping boundaries and gaps.

| Criteria | Exceptional (15–13.5) | Satisfactory (13–11.5) | Needs Improvement (11.5–7.5) | Incomplete (7–0) |
| --- | --- | --- | --- | --- |
| Security Documentation | All security modifications are documented with comprehensive technical details. The distinction between Vulnerability and Impact is clear, and the “Security Control” column specifies exact algorithms or methods used. | Most modifications are documented. Technical descriptions are present but may lack specific details. | Documentation is missing multiple entries or contains vague “Security Control” descriptions. | Documentation is missing or does not follow the required format. |
| Access Control Matrix | The ACL accurately reflects the “Least Privilege” principle. Every system feature is mapped, including strict “No” values for Unregistered and Disabled users. | Most features are mapped. Role 1 (Disabled) is correctly blocked, but some staff/manager permission overlaps are unclear. | Several features are missing from the matrix, or permissions do not align with the provided role descriptions. | No ACL Matrix provided or it cannot distinguish between different user roles. |

## Companion checklist

The [course checklist](checklist.md) supplies additional explicit criteria, including password length bounds, re-authentication, and PreparedStatements. Read both handouts when implementing or reviewing a requirement.
