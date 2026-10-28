# Part 2 — DREAD Vulnerability Ranking

Scoring Weight = count of checkmarks in that CWE's column in Table 3. Damage Potential = Common Consequences row count. Reproducibility = Observed-Examples CVE count. Exploitability: Low=1, Medium=5, High=9 (CWE-918 has no "Likelihood Of Exploit" section on its page, so the assignment's default of 10 is used). Affected Users = Damage Potential. Discoverability = Detection Methods count. Probability = Reproducibility + Exploitability + Discoverability. Impact = Damage Potential + Affected Users. Overall Risk = Scoring Weight x Probability x Impact.

| Rank | Vulnerability | Weight | Damage | Repro | Exploit | Aff. Users | Discov | Probability | Impact | Overall Risk |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | CWE-89: SQL Injection | 15 | 5 | 15 | 9 | 5 | 9 | 33 | 10 | 4950 |
| 2 | CWE-862: Missing Authorization | 13 | 4 | 25 | 9 | 4 | 9 | 43 | 8 | 4472 |
| 3 | CWE-20: Improper Input Validation | 11 | 3 | 43 | 9 | 3 | 10 | 62 | 6 | 4092 |
| 4 | CWE-863: Incorrect Authorization | 11 | 5 | 13 | 9 | 5 | 9 | 31 | 10 | 3410 |
| 5 | CWE-79: Cross-site Scripting | 10 | 3 | 20 | 9 | 3 | 2 | 31 | 6 | 1860 |
| 6 | CWE-22: Path Traversal | 5 | 4 | 23 | 9 | 4 | 9 | 41 | 8 | 1640 |
| 7 | CWE-94: Code Injection | 3 | 4 | 22 | 5 | 4 | 1 | 28 | 8 | 672 |
| 8 | CWE-269: Improper Privilege Management | 12 | 1 | 15 | 5 | 1 | 1 | 21 | 2 | 504 |
| 9 | CWE-918: SSRF | 4 | 3 | 10 | 10 | 3 | 1 | 21 | 6 | 504 |
| 10 | CWE-798: Use of Hard-coded Credentials | 3 | 2 | 20 | 9 | 2 | 10 | 39 | 4 | 468 |
| 11 | CWE-502: Deserialization of Untrusted Data | 4 | 3 | 12 | 5 | 3 | 1 | 18 | 6 | 432 |
| 12 | CWE-287: Improper Authentication | 4 | 1 | 33 | 9 | 1 | 8 | 50 | 2 | 400 |
| 13 | CWE-78: OS Command Injection | 4 | 1 | 19 | 9 | 1 | 9 | 37 | 2 | 296 |
| 14 | CWE-306: Missing Authentication for Critical Function | 4 | 1 | 17 | 9 | 1 | 8 | 34 | 2 | 272 |
| 15 | CWE-400: Uncontrolled Resource Consumption | 2 | 2 | 17 | 9 | 2 | 3 | 29 | 4 | 232 |
| 16 | CWE-200: Exposure of Sensitive Information | 3 | 1 | 19 | 9 | 1 | 6 | 34 | 2 | 204 |
| 17 | CWE-77: Command Injection | 4 | 1 | 9 | 9 | 1 | 1 | 19 | 2 | 152 |
| 18 | CWE-352: CSRF | 3 | 1 | 10 | 5 | 1 | 9 | 24 | 2 | 144 |
| 19 | CWE-434: Unrestricted Upload of Dangerous File Type | 2 | 1 | 11 | 5 | 1 | 5 | 21 | 2 | 84 |

Note: CWE-269 and CWE-918 tie at risk 504 (rank 8/9); tie broken by CWE number ascending — adjust if your rubric expects a different tie-break.
