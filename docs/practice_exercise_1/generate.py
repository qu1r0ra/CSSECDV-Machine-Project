import json
import csv
import os

OUT_DIR = os.path.dirname(os.path.abspath(__file__))

# ---------------------------------------------------------------------------
# Table 2: Threats (verbatim from the Threat Model Document, section 6)
# severity computed from Table 3 check-count per threat row, per the
# assignment's rule: LOW 0-4, MEDIUM 5-9, HIGH 10-14, CRITICAL 15-19
# ---------------------------------------------------------------------------
THREATS = [
    ("T-001", "Spoofing", "Customer Account Spoofing",
     "An attacker could impersonate a legitimate customer to place fraudulent orders or access their order history.",
     ["Frontend", "Web Server Backend", "Database"], 5),
    ("T-002", "Spoofing", "Staff Account Spoofing",
     "An attacker could impersonate staff to manipulate order statuses, access sales reports, or modify menu items and application configurations.",
     ["Frontend", "Web Server Backend", "Database"], 5),
    ("T-003", "Spoofing", "Web Application Server Spoofing",
     "An attacker might set up a fraudulent web server to phish user credentials or intercept sensitive data.",
     ["Frontend", "Web Server Backend"], 5),
    ("T-004", "Tampering", "Order Data Tampering",
     "An attacker could modify order details (e.g., items, quantities, prices, status) in transit or at rest.",
     ["Web Server Backend", "Database"], 7),
    ("T-005", "Tampering", "Menu Data Tampering",
     "An attacker could alter menu item data (e.g., prices or descriptions) before it is stored in the database.",
     ["Web Server Backend", "Database"], 6),
    ("T-006", "Tampering", "Sales Report Tampering",
     "Unauthorized individuals could alter sales data in the MongoDB Database, leading to falsified reports.",
     ["Web Server Backend", "Database"], 5),
    ("T-007", "Tampering", "Application Code/Configuration Tampering",
     "Malicious modification of the application's source code, server configurations, or environment variables could introduce backdoors or disable security features.",
     ["Web Server Backend"], 6),
    ("T-008", "Repudiation", "Fraudulent Order Repudiation",
     "A customer could deny placing an order if insufficient logging and non-repudiation controls are in place.",
     ["Web Server Backend", "Database"], 5),
    ("T-009", "Repudiation", "Staff Action Repudiation",
     "Staff members (Barista, Manager, Administrator) could deny performing actions if audit logs are insufficient, not immutable, or can be tampered with.",
     ["Web Server Backend", "Database"], 5),
    ("T-010", "Information Disclosure", "Customer PII Disclosure",
     "Unauthorized access to customer names, contact details, delivery addresses, and order history.",
     ["Web Server Backend", "Database"], 10),
    ("T-011", "Information Disclosure", "Staff Credential Disclosure",
     "Exposure of usernames, passwords, or session tokens for staff members due to insecure storage or unencrypted transmission.",
     ["Web Server Backend", "Database"], 10),
    ("T-012", "Information Disclosure", "Application Code and Configuration Disclosure",
     "Exposure of source code, server configurations, environment variables, or API keys.",
     ["Web Server Backend"], 6),
    ("T-013", "Information Disclosure", "Sales Data Disclosure",
     "Unauthorized access to sales reports or underlying aggregated sales data.",
     ["Web Server Backend", "Database"], 7),
    ("T-014", "Denial of Service", "Web Application Server DoS",
     "An attacker could flood the Web Application Server with excessive requests, making it unavailable.",
     ["Web Server Backend"], 7),
    ("T-015", "Denial of Service", "MongoDB Database DoS",
     "Malicious or poorly optimized queries could overload the database, making it unresponsive.",
     ["Database", "Web Server Backend"], 2),
    ("T-016", "Elevation of Privilege", "Customer to Staff Privilege Escalation",
     "A customer could exploit a vulnerability to gain unauthorized access to Barista, Manager, or Administrator functionalities.",
     ["Web Server Backend"], 10),
    ("T-017", "Elevation of Privilege", "Barista/Manager to Administrator Privilege Escalation",
     "A Barista or Manager could exploit a vulnerability to gain Administrator privileges.",
     ["Web Server Backend"], 10),
    ("T-018", "Elevation of Privilege", "Horizontal Privilege Escalation",
     "Gaining unauthorized access to another user's account within the same privilege level.",
     ["Web Server Backend"], 6),
]

def severity_from_count(n):
    if n <= 4:
        return "Low"
    if n <= 9:
        return "Medium"
    if n <= 14:
        return "High"
    return "Critical"

THREATS_BY_COMPONENT = {"Frontend": [], "Web Server Backend": [], "Database": []}
for tid, cat, title, desc, components, count in THREATS:
    sev = severity_from_count(count)
    for c in components:
        THREATS_BY_COMPONENT[c].append({
            "id": tid, "category": cat, "title": title, "desc": desc,
            "severity": sev, "count": count,
        })

# ---------------------------------------------------------------------------
# Part 2: DREAD data pulled from each CWE's mitre.org page (section 7, Table 3)
# damage = Common Consequences row count; repro = Observed Examples CVE count;
# exploit = Likelihood Of Exploit (Low=1, Medium=5, High=9, missing=10 default);
# discov = Detection Methods unique-method count
# ---------------------------------------------------------------------------
CWE_DATA = {
    "CWE-20":  {"name": "Improper Input Validation", "damage": 3, "repro": 43, "exploit": 9, "discov": 10},
    "CWE-22":  {"name": "Path Traversal", "damage": 4, "repro": 23, "exploit": 9, "discov": 9},
    "CWE-77":  {"name": "Command Injection", "damage": 1, "repro": 9, "exploit": 9, "discov": 1},
    "CWE-78":  {"name": "OS Command Injection", "damage": 1, "repro": 19, "exploit": 9, "discov": 9},
    "CWE-79":  {"name": "Cross-site Scripting", "damage": 3, "repro": 20, "exploit": 9, "discov": 2},
    "CWE-89":  {"name": "SQL Injection", "damage": 5, "repro": 15, "exploit": 9, "discov": 9},
    "CWE-94":  {"name": "Code Injection", "damage": 4, "repro": 22, "exploit": 5, "discov": 1},
    "CWE-200": {"name": "Exposure of Sensitive Information", "damage": 1, "repro": 19, "exploit": 9, "discov": 6},
    "CWE-269": {"name": "Improper Privilege Management", "damage": 1, "repro": 15, "exploit": 5, "discov": 1},
    "CWE-287": {"name": "Improper Authentication", "damage": 1, "repro": 33, "exploit": 9, "discov": 8},
    "CWE-306": {"name": "Missing Authentication for Critical Function", "damage": 1, "repro": 17, "exploit": 9, "discov": 8},
    "CWE-352": {"name": "CSRF", "damage": 1, "repro": 10, "exploit": 5, "discov": 9},
    "CWE-400": {"name": "Uncontrolled Resource Consumption", "damage": 2, "repro": 17, "exploit": 9, "discov": 3},
    "CWE-434": {"name": "Unrestricted Upload of Dangerous File Type", "damage": 1, "repro": 11, "exploit": 5, "discov": 5},
    "CWE-502": {"name": "Deserialization of Untrusted Data", "damage": 3, "repro": 12, "exploit": 5, "discov": 1},
    "CWE-798": {"name": "Use of Hard-coded Credentials", "damage": 2, "repro": 20, "exploit": 9, "discov": 10},
    "CWE-862": {"name": "Missing Authorization", "damage": 4, "repro": 25, "exploit": 9, "discov": 9},
    "CWE-863": {"name": "Incorrect Authorization", "damage": 5, "repro": 13, "exploit": 9, "discov": 9},
    "CWE-918": {"name": "SSRF", "damage": 3, "repro": 10, "exploit": 10, "discov": 1},  # exploit: Likelihood Of Exploit section absent -> default 10
}

# Table 3 checkmarks, transcribed from the rendered page image (page 8)
TABLE3 = {
    "T-001": ["CWE-79", "CWE-89", "CWE-287", "CWE-306", "CWE-862"],
    "T-002": ["CWE-79", "CWE-89", "CWE-287", "CWE-306", "CWE-862"],
    "T-003": ["CWE-22", "CWE-77", "CWE-78", "CWE-79", "CWE-94"],
    "T-004": ["CWE-20", "CWE-79", "CWE-89", "CWE-269", "CWE-352", "CWE-862", "CWE-863"],
    "T-005": ["CWE-20", "CWE-89", "CWE-269", "CWE-502", "CWE-862", "CWE-863"],
    "T-006": ["CWE-20", "CWE-89", "CWE-269", "CWE-862", "CWE-863"],
    "T-007": ["CWE-22", "CWE-77", "CWE-78", "CWE-94", "CWE-434", "CWE-502"],
    "T-008": ["CWE-20", "CWE-89", "CWE-269", "CWE-862", "CWE-863"],
    "T-009": ["CWE-20", "CWE-89", "CWE-269", "CWE-862", "CWE-863"],
    "T-010": ["CWE-20", "CWE-22", "CWE-79", "CWE-89", "CWE-200", "CWE-269", "CWE-798", "CWE-862", "CWE-863", "CWE-918"],
    "T-011": ["CWE-20", "CWE-22", "CWE-79", "CWE-89", "CWE-200", "CWE-269", "CWE-798", "CWE-862", "CWE-863", "CWE-918"],
    "T-012": ["CWE-22", "CWE-77", "CWE-78", "CWE-269", "CWE-798", "CWE-918"],
    "T-013": ["CWE-79", "CWE-89", "CWE-200", "CWE-269", "CWE-862", "CWE-863", "CWE-918"],
    "T-014": ["CWE-20", "CWE-77", "CWE-78", "CWE-89", "CWE-94", "CWE-400", "CWE-434"],
    "T-015": ["CWE-89", "CWE-400"],
    "T-016": ["CWE-20", "CWE-79", "CWE-89", "CWE-269", "CWE-287", "CWE-306", "CWE-352", "CWE-502", "CWE-862", "CWE-863"],
    "T-017": ["CWE-20", "CWE-79", "CWE-89", "CWE-269", "CWE-287", "CWE-306", "CWE-352", "CWE-502", "CWE-862", "CWE-863"],
    "T-018": ["CWE-20", "CWE-79", "CWE-89", "CWE-269", "CWE-862", "CWE-863"],
}

# sanity check: every CWE referenced in TABLE3 must exist in CWE_DATA
all_cwes_used = set()
for row in TABLE3.values():
    all_cwes_used.update(row)
assert all_cwes_used == set(CWE_DATA.keys()), all_cwes_used.symmetric_difference(CWE_DATA.keys())

weights = {cwe: 0 for cwe in CWE_DATA}
for row in TABLE3.values():
    for cwe in row:
        weights[cwe] += 1

rows = []
for cwe, d in CWE_DATA.items():
    weight = weights[cwe]
    damage = d["damage"]
    repro = d["repro"]
    exploit = d["exploit"]
    affected = damage
    discov = d["discov"]
    probability = repro + exploit + discov
    impact = damage + affected
    risk = weight * probability * impact
    rows.append({
        "cwe": cwe, "name": d["name"], "weight": weight, "damage": damage,
        "repro": repro, "exploit": exploit, "affected": affected, "discov": discov,
        "probability": probability, "impact": impact, "risk": risk,
    })

rows.sort(key=lambda r: (-r["risk"], r["cwe"]))
for i, r in enumerate(rows, start=1):
    r["rank"] = i

# ---------------------------------------------------------------------------
# Write DREAD CSV
# ---------------------------------------------------------------------------
csv_path = os.path.join(OUT_DIR, "dread-matrix.csv")
with open(csv_path, "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["Rank", "Vulnerability", "Scoring Weight", "Damage Potential",
                "Reproducibility", "Exploitability", "Affected Users",
                "Discoverability", "Probability", "Impact", "Overall Risk"])
    for r in rows:
        w.writerow([r["rank"], f'{r["cwe"]}: {r["name"]}', r["weight"], r["damage"],
                    r["repro"], r["exploit"], r["affected"], r["discov"],
                    r["probability"], r["impact"], r["risk"]])

# ---------------------------------------------------------------------------
# Write DREAD markdown summary
# ---------------------------------------------------------------------------
md_path = os.path.join(OUT_DIR, "dread-matrix.md")
with open(md_path, "w") as f:
    f.write("# Part 2 — DREAD Vulnerability Ranking\n\n")
    f.write("Scoring Weight = count of checkmarks in that CWE's column in Table 3. ")
    f.write("Damage Potential = Common Consequences row count. Reproducibility = Observed-Examples CVE count. ")
    f.write("Exploitability: Low=1, Medium=5, High=9 (CWE-918 has no \"Likelihood Of Exploit\" section on its page, so the assignment's default of 10 is used). ")
    f.write("Affected Users = Damage Potential. Discoverability = Detection Methods count. ")
    f.write("Probability = Reproducibility + Exploitability + Discoverability. Impact = Damage Potential + Affected Users. ")
    f.write("Overall Risk = Scoring Weight x Probability x Impact.\n\n")
    f.write("| Rank | Vulnerability | Weight | Damage | Repro | Exploit | Aff. Users | Discov | Probability | Impact | Overall Risk |\n")
    f.write("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n")
    for r in rows:
        f.write(f'| {r["rank"]} | {r["cwe"]}: {r["name"]} | {r["weight"]} | {r["damage"]} | {r["repro"]} | {r["exploit"]} | {r["affected"]} | {r["discov"]} | {r["probability"]} | {r["impact"]} | {r["risk"]} |\n')
    f.write("\nNote: CWE-269 and CWE-918 tie at risk 504 (rank 8/9); tie broken by CWE number ascending — adjust if your rubric expects a different tie-break.\n")

print("Scoring weights:", weights)
for r in rows:
    print(r["rank"], r["cwe"], r["risk"])

# ---------------------------------------------------------------------------
# Part 1: OWASP Threat Dragon 2.x model file
# ---------------------------------------------------------------------------
TD_VERSION = "2.4.2"

def make_threat(number, t):
    return {
        "id": t["id"],
        "title": t["title"],
        "status": "Open",
        "severity": t["severity"],
        "type": t["category"],
        "description": t["desc"],
        "mitigation": "",
        "modelType": "STRIDE",
        "number": number,
    }

def node(node_id, shape, tm_type, name, x, y, w, h, threat_list, z):
    threats = [make_threat(i, t) for i, t in enumerate(threat_list)]
    return {
        "position": {"x": x, "y": y},
        "size": {"width": w, "height": h},
        "attrs": {"text": {"text": name}},
        "visible": True,
        "shape": shape,
        "zIndex": z,
        "id": node_id,
        "data": {
            "type": tm_type,
            "name": name,
            "description": "",
            "outOfScope": False,
            "reasonOutOfScope": "",
            "threats": threats,
            "hasOpenThreats": len(threats) > 0,
        },
    }

def actor(node_id, name, x, y, threat_list, z=2):
    return node(node_id, "actor", "tm.Actor", name, x, y, 120, 60, threat_list, z)

def process(node_id, name, x, y, threat_list, z=2):
    return node(node_id, "process", "tm.Process", name, x, y, 140, 70, threat_list, z)

def store(node_id, name, x, y, threat_list, z=2):
    return node(node_id, "store", "tm.Store", name, x, y, 160, 60, threat_list, z)

def boundary(node_id, name, x, y, w, h, z=1):
    return {
        "position": {"x": x, "y": y},
        "size": {"width": w, "height": h},
        "attrs": {"text": {"text": name}},
        "visible": True,
        "shape": "trust-boundary-box",
        "zIndex": z,
        "id": node_id,
        "data": {
            "type": "tm.Boundary",
            "name": name,
            "description": "",
            "isTrustBoundary": True,
        },
    }

def flow(flow_id, src, dst, label, z=3):
    return {
        "shape": "flow",
        "id": flow_id,
        "zIndex": z,
        "source": {"cell": src},
        "target": {"cell": dst},
        "attrs": {"text": {"text": label}, "line": {"stroke": "#333333"}},
        "labels": [{"attrs": {"text": {"text": label}}}],
        "data": {
            "type": "tm.Flow",
            "name": label,
            "description": "",
            "outOfScope": False,
            "reasonOutOfScope": "",
            "isBidirectional": False,
            "threats": [],
            "hasOpenThreats": False,
        },
    }

FRONTEND_THREATS = THREATS_BY_COMPONENT["Frontend"]
SERVER_THREATS = THREATS_BY_COMPONENT["Web Server Backend"]
DB_THREATS = THREATS_BY_COMPONENT["Database"]

def build_diagram_1():
    cells = []
    cells.append(boundary("d1-b-client", "Client Boundary (Untrusted)", 20, 40, 200, 160))
    cells.append(boundary("d1-b-server", "Web Application Server Boundary", 300, 40, 220, 220))
    cells.append(boundary("d1-b-db", "Database Boundary", 300, 320, 220, 140))
    cells.append(boundary("d1-b-pay", "Payment Gateway Boundary (Third-Party, Partially Trusted)", 600, 40, 240, 160))
    cells.append(actor("d1-customer", "Customer", 40, 100, FRONTEND_THREATS))
    cells.append(process("d1-server", "Web Application Server", 330, 120, SERVER_THREATS))
    cells.append(store("d1-db", "MongoDB Database", 320, 390, DB_THREATS))
    cells.append(actor("d1-payment", "Payment Gateway (Third-Party)", 620, 100, []))
    cells.append(flow("d1-f1", "d1-customer", "d1-server", "1.1-1.2 Submit order (validated)"))
    cells.append(flow("d1-f2", "d1-server", "d1-payment", "1.3 Tokenized payment request"))
    cells.append(flow("d1-f3", "d1-payment", "d1-server", "1.4 Transaction response"))
    cells.append(flow("d1-f4", "d1-server", "d1-db", "1.5-1.6 Store order + payment status"))
    cells.append(flow("d1-f5", "d1-server", "d1-customer", "1.7-1.8 Order confirmation & status"))
    return {
        "id": 0, "title": "1. Customer Places an Order", "diagramType": "STRIDE",
        "placeholder": "Click the arrow icons to edit this diagram",
        "thumbnail": "./public/content/images/thumbnail.stride.jpg",
        "version": TD_VERSION, "cells": cells,
    }

def build_diagram_2():
    cells = []
    cells.append(boundary("d2-b-client", "Client Boundary (Untrusted)", 20, 40, 220, 160))
    cells.append(boundary("d2-b-server", "Web Application Server Boundary", 320, 40, 220, 220))
    cells.append(boundary("d2-b-db", "Database Boundary", 320, 320, 220, 140))
    cells.append(actor("d2-barista", "Barista", 50, 100, FRONTEND_THREATS))
    cells.append(process("d2-server", "Web Application Server", 350, 120, SERVER_THREATS))
    cells.append(store("d2-db", "MongoDB Database", 340, 390, DB_THREATS))
    cells.append(flow("d2-f1", "d2-barista", "d2-server", "2.1 Request incoming orders (authenticated)"))
    cells.append(flow("d2-f2", "d2-server", "d2-db", "2.2 Query order data"))
    cells.append(flow("d2-f3", "d2-db", "d2-server", "2.3 Pending order data"))
    cells.append(flow("d2-f4", "d2-server", "d2-barista", "2.4 Order list"))
    cells.append(flow("d2-f5", "d2-barista", "d2-server", "2.6 Updated order status"))
    cells.append(flow("d2-f6", "d2-server", "d2-db", "2.7 Update order record"))
    return {
        "id": 1, "title": "2. Barista Updates Order Status", "diagramType": "STRIDE",
        "placeholder": "Click the arrow icons to edit this diagram",
        "thumbnail": "./public/content/images/thumbnail.stride.jpg",
        "version": TD_VERSION, "cells": cells,
    }

def build_diagram_3():
    cells = []
    cells.append(boundary("d3-b-client", "Client Boundary (Untrusted)", 20, 40, 220, 160))
    cells.append(boundary("d3-b-server", "Web Application Server Boundary", 320, 40, 220, 220))
    cells.append(boundary("d3-b-db", "Database Boundary", 320, 320, 220, 140))
    cells.append(actor("d3-manager", "Manager", 50, 100, FRONTEND_THREATS))
    cells.append(process("d3-server", "Web Application Server", 350, 120, SERVER_THREATS))
    cells.append(store("d3-db", "MongoDB Database", 340, 390, DB_THREATS))
    cells.append(flow("d3-f1", "d3-manager", "d3-server", "3.1 Request basic sales reports (authenticated)"))
    cells.append(flow("d3-f2", "d3-server", "d3-db", "3.2 Query aggregated sales data"))
    cells.append(flow("d3-f3", "d3-db", "d3-server", "3.3 Sales data"))
    cells.append(flow("d3-f4", "d3-server", "d3-manager", "3.4-3.5 Formatted sales report"))
    return {
        "id": 2, "title": "3. Manager Accesses Basic Sales Reports", "diagramType": "STRIDE",
        "placeholder": "Click the arrow icons to edit this diagram",
        "thumbnail": "./public/content/images/thumbnail.stride.jpg",
        "version": TD_VERSION, "cells": cells,
    }

def build_diagram_4():
    cells = []
    cells.append(boundary("d4-b-client", "Client Boundary (Untrusted)", 20, 40, 220, 160))
    cells.append(boundary("d4-b-server", "Web Application Server Boundary", 320, 40, 220, 220))
    cells.append(boundary("d4-b-db", "Database Boundary", 320, 320, 220, 140))
    cells.append(actor("d4-admin", "Administrator", 50, 100, FRONTEND_THREATS))
    cells.append(process("d4-server", "Web Application Server", 350, 120, SERVER_THREATS))
    cells.append(store("d4-db", "MongoDB Database", 340, 390, DB_THREATS))
    cells.append(flow("d4-f1", "d4-admin", "d4-server", "4.1-4.2 New menu item data (authenticated)"))
    cells.append(flow("d4-f2", "d4-server", "d4-db", "4.4 Create menu item record"))
    cells.append(flow("d4-f3", "d4-server", "d4-admin", "4.6-4.7 Confirmation"))
    return {
        "id": 3, "title": "4. Administrator Adds New Menu Item", "diagramType": "STRIDE",
        "placeholder": "Click the arrow icons to edit this diagram",
        "thumbnail": "./public/content/images/thumbnail.stride.jpg",
        "version": TD_VERSION, "cells": cells,
    }

model = {
    "version": TD_VERSION,
    "summary": {
        "title": "Brew & Bloom Coffee Co. Ordering Application",
        "owner": "",
        "description": "Threat model covering the 4 data flows in section 5.2 of the reference Threat Model Document, for Practical Exercise 1.",
        "id": 0,
    },
    "detail": {
        "contributors": [],
        "diagrams": [build_diagram_1(), build_diagram_2(), build_diagram_3(), build_diagram_4()],
        "diagramTop": 4,
        "reviewer": "",
        "threatTop": 0,
    },
}

model_path = os.path.join(OUT_DIR, "threat-dragon-model.json")
with open(model_path, "w") as f:
    json.dump(model, f, indent=2)

print("Wrote", model_path)
print("Wrote", csv_path)
print("Wrote", md_path)
