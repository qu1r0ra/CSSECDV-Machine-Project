package Controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

/** Behavior checks at the confirmed public Bundle B operations seam. */
public final class BundleBServiceTest {
    private BundleBServiceTest() { }

    public static void main(String[] args) throws Exception {
        clientCanBrowseButCannotManageProducts();
        staffAddsExactPricedProductAfterBoundVerification();
        reauthenticationFailureAccountingRemainsProviderOwned();
        purchaseConfirmationPreservesImmutableFactsAndRejectsReplay();
        clientHistoryIsScopedAndManagersCanSeeUnknownLegacyFacts();
        competingQuotesCannotSellTheLastUnitTwice();
        invalidInputAndAuditFailureLeaveNoPartialChanges();
        failedPurchaseAuditRollsBackStockAndHistory();
        legacyPriceMigrationPreservesUnrepresentableValues();
        disabledAndAdminRolesHaveNoBundleBAccess();
        System.out.println("Bundle B behavior checks passed.");
    }

    private static void clientHistoryIsScopedAndManagersCanSeeUnknownLegacyFacts() throws Exception {
        Path database = Files.createTempFile("bundle-b-", ".db");
        try {
            BundleBService staff = new BundleBService(database, new FakeSecurity(3, 30, "staff"));
            staff.initialize();
            staff.addProduct("Marker", "2", "2.50", new char[] {'S', 'a', 'f', 'e', '1', '!'});
            BundleBService.CatalogProduct product = staff.listCatalog().getValue().get(0);
            BundleBService buyer = new BundleBService(database, new FakeSecurity(2, 20, "buyer"));
            buyer.initialize();
            buyer.confirmPurchase(buyer.quotePurchase(product.getId(), "1").getValue());
            BundleBService other = new BundleBService(database, new FakeSecurity(2, 21, "other"));
            other.initialize();
            other.confirmPurchase(other.quotePurchase(product.getId(), "1").getValue());
            check(buyer.historyForCurrentActor("").getValue().size() == 1, "client own-history scope");
            check(other.historyForCurrentActor("").getValue().size() == 1, "second client own-history scope");

            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO history(username, name, stock, timestamp) VALUES(?, ?, ?, ?)")) {
                insert.setString(1, "uncertain-owner");
                insert.setString(2, "LegacyWidget");
                insert.setInt(3, 2);
                insert.setString(4, "2020-01-01T00:00:00Z");
                insert.executeUpdate();
            }
            BundleBService manager = new BundleBService(database, new FakeSecurity(4, 40, "manager"));
            manager.initialize();
            List<BundleBService.PurchaseRecord> all = manager.historyForCurrentActor("").getValue();
            check(all.size() == 3, "manager history includes purchases and legacy row");
            BundleBService.PurchaseRecord legacy = manager.historyForCurrentActor("Legacy").getValue().get(0);
            check(legacy.isLegacy() && !legacy.hasPrice(), "legacy price is shown as unavailable");
            BundleBService uncertainClient = new BundleBService(database, new FakeSecurity(2, 99, "uncertain-owner"));
            uncertainClient.initialize();
            check(uncertainClient.historyForCurrentActor("").getValue().isEmpty(), "legacy ownership is not inferred for a client");
            check(manager.historyForCurrentActor(repeat('a', 65)).getStatus() == BundleBService.Status.INVALID,
                    "oversized search is rejected");
            check(manager.historyForCurrentActor("LegacyWidget!").getStatus() == BundleBService.Status.INVALID,
                    "disallowed search characters are rejected");
        } finally {
            Files.deleteIfExists(database);
        }
    }

    private static void competingQuotesCannotSellTheLastUnitTwice() throws Exception {
        Path database = Files.createTempFile("bundle-b-", ".db");
        try {
            BundleBService staff = new BundleBService(database, new FakeSecurity(3, 30, "staff"));
            staff.initialize();
            staff.addProduct("LastUnit", "1", "1.00", new char[] {'S', 'a', 'f', 'e', '1', '!'});
            int productId = staff.listCatalog().getValue().get(0).getId();
            BundleBService client = new BundleBService(database, new FakeSecurity(2, 20, "buyer"));
            client.initialize();
            BundleBService.PurchaseQuote first = client.quotePurchase(productId, "1").getValue();
            BundleBService.PurchaseQuote second = client.quotePurchase(productId, "1").getValue();
            check(client.confirmPurchase(first).isSuccess(), "first last-unit purchase succeeds");
            check(client.confirmPurchase(second).getStatus() == BundleBService.Status.CONFLICT,
                    "second stale quote is rejected");
            BundleBService.CatalogProduct remaining = client.listCatalog().getValue().get(0);
            check(remaining.getStock() == 0, "stock never becomes negative");
            check(client.historyForCurrentActor("").getValue().size() == 1, "only one purchase record is created");
        } finally {
            Files.deleteIfExists(database);
        }
    }

    private static void invalidInputAndAuditFailureLeaveNoPartialChanges() throws Exception {
        Path database = Files.createTempFile("bundle-b-", ".db");
        try {
            BundleBService staff = new BundleBService(database, new FakeSecurity(3, 30, "staff"));
            staff.initialize();
            check(staff.addProduct("Bad Name", "1", "1.00", new char[] {'S', 'a', 'f', 'e', '1', '!'}).getStatus()
                    == BundleBService.Status.INVALID, "spaces in product names are rejected");
            check(staff.addProduct("BadPrice", "1", "1.001", new char[] {'S', 'a', 'f', 'e', '1', '!'}).getStatus()
                    == BundleBService.Status.INVALID, "excess price precision is rejected");
            check(staff.addProduct("BadPrice", "1", "1e2", new char[] {'S', 'a', 'f', 'e', '1', '!'}).getStatus()
                    == BundleBService.Status.INVALID, "exponent prices are rejected");
            check(staff.addProduct("BadPrice", "1", "1,000.00", new char[] {'S', 'a', 'f', 'e', '1', '!'}).getStatus()
                    == BundleBService.Status.INVALID, "grouped prices are rejected");
            check(staff.addProduct("BadStock", "+1", "1.00", new char[] {'S', 'a', 'f', 'e', '1', '!'}).getStatus()
                    == BundleBService.Status.INVALID, "signed stock is rejected");
            check(staff.addProduct("BadStock", "1000001", "1.00", new char[] {'S', 'a', 'f', 'e', '1', '!'}).getStatus()
                    == BundleBService.Status.INVALID, "stock above the specified maximum is rejected");
            check(countRows(database, "SELECT COUNT(*) FROM logs WHERE event = 'PRODUCT_ADD' AND timestamp IS NOT NULL") >= 5,
                    "validation failures append timestamped audit events");
            staff.setAuditWriterForTest(new BundleBService.AuditWriter() {
                public void append(Connection connection, BundleBService.AuditEvent event) throws Exception {
                    throw new Exception("storage failure");
                }
            });
            check(staff.addProduct("Rollback", "1", "1.00", new char[] {'S', 'a', 'f', 'e', '1', '!'}).getStatus()
                    == BundleBService.Status.FAILURE, "audit failure is generic");
            check(staff.listCatalog().getValue().isEmpty(), "failed audit rolls back product creation");
            check(countRows(database, "SELECT COUNT(*) FROM logs WHERE event = 'PRODUCT_ADD' AND \"desc\" LIKE '%outcome=FAILURE%'") == 1,
                    "failure outcome is recorded after rollback when audit storage remains available");
        } finally {
            Files.deleteIfExists(database);
        }
    }

    private static void failedPurchaseAuditRollsBackStockAndHistory() throws Exception {
        Path database = Files.createTempFile("bundle-b-", ".db");
        try {
            BundleBService staff = new BundleBService(database, new FakeSecurity(3, 30, "staff"));
            staff.initialize();
            staff.addProduct("Atomic", "2", "3.75", new char[] {'S', 'a', 'f', 'e', '1', '!'});
            int productId = staff.listCatalog().getValue().get(0).getId();
            BundleBService buyer = new BundleBService(database, new FakeSecurity(2, 20, "buyer"));
            buyer.initialize();
            check(buyer.quotePurchase(productId, "1000001").getStatus() == BundleBService.Status.INVALID,
                    "purchase quantity above the specified maximum is rejected");
            BundleBService.PurchaseQuote quote = buyer.quotePurchase(productId, "1").getValue();
            buyer.setAuditWriterForTest(new BundleBService.AuditWriter() {
                public void append(Connection connection, BundleBService.AuditEvent event) throws Exception {
                    throw new Exception("storage failure");
                }
            });
            check(buyer.confirmPurchase(quote).getStatus() == BundleBService.Status.FAILURE,
                    "audit failure returns a generic purchase error");
            check(buyer.listCatalog().getValue().get(0).getStock() == 2, "failed purchase restores stock");
            check(buyer.historyForCurrentActor("").getValue().isEmpty(), "failed purchase leaves no history row");
            check(countRows(database, "SELECT COUNT(*) FROM purchase_records") == 0, "failed purchase leaves no snapshot row");
            check(countRows(database, "SELECT COUNT(*) FROM logs WHERE event = 'PURCHASE' AND \"desc\" LIKE '%outcome=FAILURE%'") == 1,
                    "failed purchase outcome is recorded outside its rolled-back transaction");
        } finally {
            Files.deleteIfExists(database);
        }
    }

    private static void legacyPriceMigrationPreservesUnrepresentableValues() throws Exception {
        Path database = Files.createTempFile("bundle-b-", ".db");
        try {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
                 java.sql.Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE product (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, stock INTEGER DEFAULT 0, price REAL DEFAULT 0.00)");
                statement.execute("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL, name TEXT NOT NULL, stock INTEGER DEFAULT 0, timestamp TEXT NOT NULL)");
                statement.execute("CREATE TABLE logs (id INTEGER PRIMARY KEY AUTOINCREMENT, event TEXT NOT NULL, username TEXT NOT NULL, desc TEXT NOT NULL, timestamp TEXT NOT NULL)");
                statement.execute("INSERT INTO product(name, stock, price) VALUES('ExactLegacy', 4, 1.25)");
                statement.execute("INSERT INTO product(name, stock, price) VALUES('UnknownLegacy', 4, 1.234)");
            }
            BundleBService service = new BundleBService(database, new FakeSecurity(3, 30, "staff"));
            service.initialize();
            List<BundleBService.CatalogProduct> products = service.listCatalog().getValue();
            check(products.get(0).getPriceMinor().longValue() == 125L, "exact legacy price migrates to minor units");
            check(products.get(1).getPriceMinor() == null, "unrepresentable legacy price remains unavailable");
        } finally {
            Files.deleteIfExists(database);
        }
    }

    private static void disabledAndAdminRolesHaveNoBundleBAccess() throws Exception {
        Path unauthenticatedDatabase = Files.createTempFile("bundle-b-", ".db");
        try {
            BundleBService unauthenticated = new BundleBService(unauthenticatedDatabase, new FakeSecurity());
            unauthenticated.initialize();
            check(unauthenticated.listCatalog().getStatus() == BundleBService.Status.DENIED,
                    "absent actor cannot read catalog");
            check(countRows(unauthenticatedDatabase,
                    "SELECT COUNT(*) FROM logs WHERE event = 'UNAUTHORIZED_READ_CATALOG'") == 1,
                    "absent actor attempt is audited");
        } finally {
            Files.deleteIfExists(unauthenticatedDatabase);
        }
        for (int role : new int[] {1, 5}) {
            Path database = Files.createTempFile("bundle-b-", ".db");
            try {
                BundleBService service = new BundleBService(database, new FakeSecurity(role, 50 + role, "blocked"));
                service.initialize();
                check(service.listCatalog().getStatus() == BundleBService.Status.DENIED,
                        "disabled and admin roles cannot access the B catalog");
                check(!service.canManageProducts() && !service.canPurchase() && !service.canViewHistory(),
                        "disabled and admin roles have no B operation privileges");
                check(countRows(database, "SELECT COUNT(*) FROM logs WHERE event = 'UNAUTHORIZED_READ_CATALOG'") == 1,
                        "unauthorized access is timestamped in the audit table");
            } finally {
                Files.deleteIfExists(database);
            }
        }
    }

    private static void purchaseConfirmationPreservesImmutableFactsAndRejectsReplay() throws Exception {
        Path database = Files.createTempFile("bundle-b-", ".db");
        try {
            BundleBService staff = new BundleBService(database, new FakeSecurity(3, 30, "staff"));
            staff.initialize();
            check(staff.addProduct("Token", "1", "12.30", new char[] {'S', 'a', 'f', 'e', '1', '!'}).isSuccess(), "catalog setup");

            BundleBService client = new BundleBService(database, new FakeSecurity(2, 20, "buyer"));
            client.initialize();
            BundleBService.CatalogProduct product = client.listCatalog().getValue().get(0);
            BundleBService.PurchaseQuote quote = client.quotePurchase(product.getId(), "1").getValue();
            check(quote.getTotalMinor() == 1230L, "confirmed total uses exact price");
            BundleBService.PurchaseQuote canceled = client.quotePurchase(product.getId(), "1").getValue();
            client.cancelPurchaseQuote(canceled);
            check(client.confirmPurchase(canceled).getStatus() == BundleBService.Status.CONFLICT,
                    "canceled confirmation quote cannot be reused");
            BundleBService.Result<BundleBService.PurchaseRecord> result = client.confirmPurchase(quote);
            check(result.isSuccess(), "confirmed purchase succeeds");
            check(result.getValue().getBuyerName().equals("buyer"), "purchase captures buyer name");
            check(result.getValue().getProductName().equals("Token"), "purchase captures product name");
            check(result.getValue().getTotalMinor().longValue() == 1230L, "purchase captures charged total");
            check(client.confirmPurchase(quote).getStatus() == BundleBService.Status.CONFLICT, "same quote cannot be replayed");

            BundleBService manager = new BundleBService(database, new FakeSecurity(4, 40, "manager"));
            manager.initialize();
            product = manager.listCatalog().getValue().get(0);
            check(manager.editProduct(product, "Token", "1", "99.99", new char[] {'S', 'a', 'f', 'e', '1', '!'}).isSuccess(), "catalog repricing");
            List<BundleBService.PurchaseRecord> records = manager.historyForCurrentActor("").getValue();
            check(records.size() == 1, "manager can view purchase history");
            check(records.get(0).getUnitPriceMinor().longValue() == 1230L, "history retains purchase-time price");
            check(records.get(0).getTotalMinor().longValue() == 1230L, "history retains purchase-time total");
            BundleBService.CatalogProduct edited = manager.listCatalog().getValue().get(0);
            check(manager.deleteProduct(edited, new char[] {'S', 'a', 'f', 'e', '1', '!'}).isSuccess(), "delete product after purchase");
            records = manager.historyForCurrentActor("").getValue();
            check(records.size() == 1 && records.get(0).getProductName().equals("Token"), "history survives product deletion");
        } finally {
            Files.deleteIfExists(database);
        }
    }

    private static void staffAddsExactPricedProductAfterBoundVerification() throws Exception {
        Path database = Files.createTempFile("bundle-b-", ".db");
        try {
            FakeSecurity security = new FakeSecurity(3, 30, "staff");
            BundleBService service = new BundleBService(database, security);
            service.initialize();
            char[] password = new char[] {'S', 'a', 'f', 'e', '1', '!'};
            BundleBService.Result<BundleBService.CatalogProduct> added = service.addProduct("Token", "4", "12.30", password);
            check(added.isSuccess(), "staff product add");
            check(added.getValue().getPriceMinor().longValue() == 1230L, "exact minor-unit price");
            check(added.getValue().getStock() == 4, "product stock");
            check(security.lastVerificationOperation == BundleBService.Operation.ADD_PRODUCT, "fresh verification is bound to the mutation");
            check(security.passwordCleared(password), "temporary password buffer is cleared");
            check(service.listCatalog().getValue().size() == 1, "added product is available through catalog");
        } finally {
            Files.deleteIfExists(database);
        }
    }

    private static void reauthenticationFailureAccountingRemainsProviderOwned() throws Exception {
        Path database = Files.createTempFile("bundle-b-", ".db");
        try {
            FakeSecurity security = new FakeSecurity(3, 30, "staff");
            security.acceptPassword = false;
            BundleBService service = new BundleBService(database, security);
            service.initialize();
            check(service.addProduct("Token", "1", "1.00", new char[] {'B', 'a', 'd'}).getStatus()
                    == BundleBService.Status.REAUTH_REQUIRED, "failed current-password verification blocks the mutation");
            check(countRows(database, "SELECT COUNT(*) FROM product") == 0, "failed current-password verification leaves catalog unchanged");
            check(countRows(database, "SELECT COUNT(*) FROM logs WHERE event LIKE 'REAUTH_PRODUCT_%'") == 0,
                    "Bundle B does not duplicate provider-owned authentication failure events");
        } finally {
            Files.deleteIfExists(database);
        }
    }

    private static void clientCanBrowseButCannotManageProducts() throws Exception {
        Path database = Files.createTempFile("bundle-b-", ".db");
        try {
            FakeSecurity security = new FakeSecurity(2, 20, "client");
            BundleBService service = new BundleBService(database, security);
            service.initialize();
            BundleBService.Result<List<BundleBService.CatalogProduct>> catalog = service.listCatalog();
            check(catalog.isSuccess() && catalog.getValue().isEmpty(), "client catalog access");
            BundleBService.Result<BundleBService.CatalogProduct> rejected =
                    service.addProduct("New", "1", "1.00", new char[] {'S', 'a', 'f', 'e', '1', '!'});
            check(rejected.getStatus() == BundleBService.Status.DENIED, "client product mutation denied");
            check(service.listCatalog().getValue().isEmpty(), "denied mutation leaves catalog unchanged");
        } finally {
            Files.deleteIfExists(database);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static long countRows(Path database, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             java.sql.Statement statement = connection.createStatement();
             java.sql.ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getLong(1) : -1L;
        }
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) { result.append(value); }
        return result.toString();
    }

    private static final class FakeSecurity implements BundleBService.SecurityGateway {
        private final BundleBService.Actor actor;
        private BundleBService.Operation lastVerificationOperation;
        private boolean acceptPassword = true;
        FakeSecurity() { actor = null; }
        FakeSecurity(int role, long userId, String username) {
            actor = new BundleBService.Actor(userId, username, role, userId * 10);
        }
        public BundleBService.Actor currentActor() { return actor; }
        public boolean authorize(BundleBService.Actor current, BundleBService.Operation operation, Integer targetId) {
            return current == actor;
        }
        public boolean verifyCurrentPassword(BundleBService.Actor current, BundleBService.Operation operation,
                Integer targetId, String requestFingerprint, char[] password) {
            lastVerificationOperation = operation;
            return acceptPassword && current == actor && operation != null && requestFingerprint != null && password.length > 0;
        }
        public void onUnrecoverableFailure() { }
        boolean passwordCleared(char[] password) {
            for (char value : password) { if (value != '\0') { return false; } }
            return true;
        }
    }
}
