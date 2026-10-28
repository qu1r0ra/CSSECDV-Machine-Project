# Part 1 blueprint (manual fallback if the import file doesn't load)

Try `threat-dragon-model.json` first: open OWASP Threat Dragon → **Open Existing Model** → select the file. If it imports, just review placement and export as PDF (File → Report) plus PNG/JPG screenshots of each diagram, and you're done — skip the rest of this file.

If the import fails or looks wrong, build these 4 diagrams by hand. Each one is quick: 2-4 shapes, a few arrows, paste threats from the lists below into each shape's Threats panel.

**Interpretation note:** Table 2's "Affected Components" are the three generic categories (Frontend, Web Server Backend, Database) — not tied to a specific actor or flow — so the same threat sets repeat identically across all 4 diagrams. That's consistent with the source document, not an error.

## Severity reference (Table 3 check-count per threat)
MEDIUM (5-9 checks) for T-001–T-009, T-012–T-015(LOW), T-018; HIGH (10) for T-010, T-011, T-016, T-017; LOW for T-015 (2 checks).
Exact: T-001=MED, T-002=MED, T-003=MED, T-004=MED, T-005=MED, T-006=MED, T-007=MED, T-008=MED, T-009=MED, T-010=HIGH, T-011=HIGH, T-012=MED, T-013=MED, T-014=MED, T-015=LOW, T-016=HIGH, T-017=HIGH, T-018=MED.

## Threats to attach to any "Frontend" shape (the customer/staff actor)
- **T-001** Spoofing — *Customer Account Spoofing* — "An attacker could impersonate a legitimate customer to place fraudulent orders or access their order history." — MEDIUM
- **T-002** Spoofing — *Staff Account Spoofing* — "An attacker could impersonate staff to manipulate order statuses, access sales reports, or modify menu items and application configurations." — MEDIUM
- **T-003** Spoofing — *Web Application Server Spoofing* — "An attacker might set up a fraudulent web server to phish user credentials or intercept sensitive data." — MEDIUM

## Threats to attach to the "Web Application Server" process (same list, all 4 diagrams)
All 18 threats T-001 through T-018 (every row of Table 2 lists Web Server Backend as affected). Copy directly from the Threat Model Document §6, Table 2 — Title / STRIDE Type / Description columns verbatim, severity per the reference above.

## Threats to attach to the "MongoDB Database" store (same list, all 4 diagrams)
- T-001, T-002 (Spoofing)
- T-004, T-005, T-006 (Tampering)
- T-008, T-009 (Repudiation)
- T-010, T-011, T-013 (Information Disclosure)
- T-015 (Denial of Service)

## Payment Gateway (diagram 1 only)
No Table 2 row lists it as an affected component — leave it with no threats attached (matches the source document).

## Diagram 1 — Customer Places an Order
Shapes: External Entity **Customer** · Process **Web Application Server** · Data Store **MongoDB Database** · External Entity **Payment Gateway (Third-Party)**
Trust boundaries: Client Boundary around Customer · Server Boundary around Web Application Server · Database Boundary around MongoDB Database · Payment Boundary around Payment Gateway (this is the one diagram that needs all 3 boundary *types*: Client/Server, Server/DB, Server/Payment)
Flows: Customer→Server "Submit order (validated)" · Server→Payment "Tokenized payment request" · Payment→Server "Transaction response" · Server→Database "Store order + payment status" · Server→Customer "Order confirmation & status"

## Diagram 2 — Barista Updates Order Status
Shapes: External Entity **Barista** · Process **Web Application Server** · Data Store **MongoDB Database**
Trust boundaries: Client Boundary, Server Boundary, Database Boundary
Flows: Barista→Server "Request incoming orders (authenticated)" · Server→Database "Query order data" · Database→Server "Pending order data" · Server→Barista "Order list" · Barista→Server "Updated order status" · Server→Database "Update order record"

## Diagram 3 — Manager Accesses Basic Sales Reports
Shapes: External Entity **Manager** · Process **Web Application Server** · Data Store **MongoDB Database**
Trust boundaries: Client Boundary, Server Boundary, Database Boundary
Flows: Manager→Server "Request sales reports (authenticated)" · Server→Database "Query aggregated sales data" · Database→Server "Sales data" · Server→Manager "Formatted sales report"

## Diagram 4 — Administrator Adds New Menu Item
Shapes: External Entity **Administrator** · Process **Web Application Server** · Data Store **MongoDB Database**
Trust boundaries: Client Boundary, Server Boundary, Database Boundary
Flows: Administrator→Server "New menu item data (authenticated)" · Server→Database "Create menu item record" · Server→Administrator "Confirmation"
