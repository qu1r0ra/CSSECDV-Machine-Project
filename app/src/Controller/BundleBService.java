package Controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/** The single public operations boundary for Bundle B catalog and purchase behavior. */
public final class BundleBService {
    private static final int CLIENT = 2;
    private static final int STAFF = 3;
    private static final int MANAGER = 4;
    private static final int MAX_COUNT = 1_000_000;
    private static final long MAX_PRICE_MINOR = 100_000_000L;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9]{1,64}");
    private static final Pattern UNSIGNED_INTEGER = Pattern.compile("[0-9]{1,7}");
    private static final Pattern PRICE = Pattern.compile("[0-9]+(?:\\.[0-9]{1,2})?");
    private static final Pattern SEARCH = Pattern.compile("[A-Za-z0-9]{1,64}");

    private final Path database;
    private final SecurityGateway security;
    private final ConcurrentMap<String, PurchaseQuote> pendingQuotes = new ConcurrentHashMap<String, PurchaseQuote>();
    private AuditWriter auditWriter = new SqliteAuditWriter();

    public BundleBService(Path database, SecurityGateway security) {
        if (database == null || security == null) { throw new IllegalArgumentException("Bundle B dependencies are required"); }
        this.database = database.toAbsolutePath();
        this.security = security;
    }

    /** Creates additive compatibility columns and tables; existing rows are retained. */
    public void initialize() {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS product (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, stock INTEGER DEFAULT 0, price REAL DEFAULT 0.00)")) {
                statement.execute();
            }
            try (PreparedStatement statement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL, name TEXT NOT NULL, stock INTEGER DEFAULT 0, timestamp TEXT NOT NULL)")) {
                statement.execute();
            }
            try (PreparedStatement statement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS logs (id INTEGER PRIMARY KEY AUTOINCREMENT, event TEXT NOT NULL, username TEXT NOT NULL, desc TEXT NOT NULL, timestamp TEXT NOT NULL)")) {
                statement.execute();
            }
            addColumnIfMissing(connection, "product", "price_minor", "INTEGER");
            addColumnIfMissing(connection, "product", "version", "INTEGER NOT NULL DEFAULT 0");
            try (PreparedStatement statement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS purchase_records ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, confirmation_id TEXT NOT NULL UNIQUE, "
                        + "buyer_id INTEGER NOT NULL, buyer_name TEXT NOT NULL, product_id INTEGER NOT NULL, "
                        + "product_name TEXT NOT NULL, unit_price_minor INTEGER NOT NULL, quantity INTEGER NOT NULL, "
                        + "purchased_at_utc TEXT NOT NULL)")) {
                statement.execute();
            }
            try (PreparedStatement statement = connection.prepareStatement("CREATE INDEX IF NOT EXISTS purchase_records_buyer_id_idx ON purchase_records(buyer_id, id)")) {
                statement.execute();
            }
            migrateLegacyPrices(connection);
            connection.commit();
        } catch (Exception ex) {
            safeFallback();
            throw new IllegalStateException("Bundle B storage is unavailable");
        }
    }

    public Result<List<CatalogProduct>> listCatalog() {
        Actor actor = authorized(Operation.READ_CATALOG, null);
        if (actor == null) { return Result.failure(Status.DENIED); }
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("SELECT id, name, stock, price_minor, version FROM product ORDER BY id");
             ResultSet rows = statement.executeQuery()) {
            List<CatalogProduct> products = new ArrayList<CatalogProduct>();
            while (rows.next()) {
                long price = rows.getLong("price_minor");
                Long priceMinor = rows.wasNull() ? null : Long.valueOf(price);
                products.add(new CatalogProduct(rows.getInt("id"), rows.getString("name"), rows.getInt("stock"), priceMinor, rows.getInt("version")));
            }
            return Result.success(Collections.unmodifiableList(products));
        } catch (Exception ex) {
            safeFallback();
            return Result.failure(Status.FAILURE);
        }
    }

    public boolean canManageProducts() { return allowedForDisplay(Operation.ADD_PRODUCT); }

    public boolean canPurchase() { return allowedForDisplay(Operation.PURCHASE); }

    public boolean canViewHistory() {
        try {
            Actor actor = security.currentActor();
            Operation operation = historyOperation(actor);
            return operation != null && isAllowedRole(actor, operation) && security.authorize(actor, operation, null);
        } catch (RuntimeException ex) {
            safeFallback();
            return false;
        }
    }

    public Result<CatalogProduct> addProduct(String name, String stockText, String priceText, char[] currentPassword) {
        try {
            Actor actor = authorized(Operation.ADD_PRODUCT, null);
            if (actor == null) { return Result.failure(Status.DENIED); }
            if (!isValidProductInput(name, stockText, priceText)) { logRejected("PRODUCT_ADD", null); return Result.failure(Status.INVALID); }
            int stock = parseInteger(stockText);
            long priceMinor = parseMinor(priceText);
            VerificationResult verification = reauthenticate(actor, Operation.ADD_PRODUCT, null,
                    name + "|" + stock + "|" + priceMinor, currentPassword);
            if (verification == VerificationResult.REJECTED) {
                return Result.failure(Status.REAUTH_REQUIRED);
            }
            if (verification == VerificationResult.UNAVAILABLE) { return Result.failure(Status.FAILURE); }
            Actor fresh = authorized(Operation.ADD_PRODUCT, null);
            if (!sameSession(actor, fresh)) { return Result.failure(Status.DENIED); }
            try (Connection connection = open()) {
                connection.setAutoCommit(false);
                try {
                    int id;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO product(name, stock, price, price_minor, version) VALUES(?, ?, ?, ?, 0)", Statement.RETURN_GENERATED_KEYS)) {
                        statement.setString(1, name);
                        statement.setInt(2, stock);
                        statement.setDouble(3, priceMinor / 100.0d);
                        statement.setLong(4, priceMinor);
                        statement.executeUpdate();
                        try (ResultSet keys = statement.getGeneratedKeys()) { id = keys.next() ? keys.getInt(1) : lastInsertId(connection); }
                    }
                    auditWriter.append(connection, new AuditEvent(actor, "PRODUCT_ADD", "product:" + id, "SUCCESS", utcNow()));
                    connection.commit();
                    return Result.success(new CatalogProduct(id, name, stock, Long.valueOf(priceMinor), 0));
                } catch (SQLException ex) {
                    rollback(connection);
                    Status status = isConstraint(ex) ? Status.CONFLICT : Status.FAILURE;
                    logOutcome(actor, "PRODUCT_ADD", "input", status.name());
                    if (status == Status.FAILURE) { safeFallback(); }
                    return Result.failure(status);
                } catch (Exception ex) {
                    rollback(connection);
                    logOutcome(actor, "PRODUCT_ADD", "input", "FAILURE");
                    safeFallback();
                    return Result.failure(Status.FAILURE);
                }
            }
        } catch (Exception ex) {
            safeFallback();
            return Result.failure(Status.FAILURE);
        } finally { clear(currentPassword); }
    }

    public Result<CatalogProduct> editProduct(CatalogProduct expected, String name, String stockText,
            String priceText, char[] currentPassword) {
        try {
            Actor actor = authorized(Operation.EDIT_PRODUCT, expected == null ? null : Integer.valueOf(expected.id));
            if (actor == null) { return Result.failure(Status.DENIED); }
            if (expected == null || !isValidProductInput(name, stockText, priceText)) { logRejected("PRODUCT_EDIT", expected); return Result.failure(Status.INVALID); }
            int stock = parseInteger(stockText);
            long priceMinor = parseMinor(priceText);
            VerificationResult verification = reauthenticate(actor, Operation.EDIT_PRODUCT, Integer.valueOf(expected.id),
                    expected.version + "|" + name + "|" + stock + "|" + priceMinor, currentPassword);
            if (verification == VerificationResult.REJECTED) {
                return Result.failure(Status.REAUTH_REQUIRED);
            }
            if (verification == VerificationResult.UNAVAILABLE) { return Result.failure(Status.FAILURE); }
            Actor fresh = authorized(Operation.EDIT_PRODUCT, Integer.valueOf(expected.id));
            if (!sameSession(actor, fresh)) { return Result.failure(Status.DENIED); }
            try (Connection connection = open()) {
                connection.setAutoCommit(false);
                try {
                    int changed;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE product SET name = ?, stock = ?, price = ?, price_minor = ?, version = version + 1 WHERE id = ? AND version = ?")) {
                        statement.setString(1, name);
                        statement.setInt(2, stock);
                        statement.setDouble(3, priceMinor / 100.0d);
                        statement.setLong(4, priceMinor);
                        statement.setInt(5, expected.id);
                        statement.setInt(6, expected.version);
                        changed = statement.executeUpdate();
                    }
                    if (changed != 1) {
                        rollback(connection);
                        logOutcome(actor, "PRODUCT_EDIT", "product:" + expected.id, "CONFLICT");
                        return Result.failure(Status.CONFLICT);
                    }
                    auditWriter.append(connection, new AuditEvent(actor, "PRODUCT_EDIT", "product:" + expected.id, "SUCCESS", utcNow()));
                    connection.commit();
                    return Result.success(new CatalogProduct(expected.id, name, stock, Long.valueOf(priceMinor), expected.version + 1));
                } catch (Exception ex) {
                    rollback(connection);
                    Status status = ex instanceof SQLException && isConstraint((SQLException) ex) ? Status.CONFLICT : Status.FAILURE;
                    logOutcome(actor, "PRODUCT_EDIT", "product:" + expected.id, status.name());
                    if (status == Status.FAILURE) { safeFallback(); }
                    return Result.failure(status);
                }
            }
        } catch (Exception ex) {
            safeFallback();
            return Result.failure(Status.FAILURE);
        } finally { clear(currentPassword); }
    }

    public Result<Void> deleteProduct(CatalogProduct expected, char[] currentPassword) {
        try {
            if (expected == null || expected.id <= 0 || expected.version < 0) { logRejected("PRODUCT_DELETE", expected); return Result.failure(Status.INVALID); }
            Actor actor = authorized(Operation.DELETE_PRODUCT, Integer.valueOf(expected.id));
            if (actor == null) { return Result.failure(Status.DENIED); }
            VerificationResult verification = reauthenticate(actor, Operation.DELETE_PRODUCT,
                    Integer.valueOf(expected.id), "version:" + expected.version, currentPassword);
            if (verification == VerificationResult.REJECTED) {
                return Result.failure(Status.REAUTH_REQUIRED);
            }
            if (verification == VerificationResult.UNAVAILABLE) { return Result.failure(Status.FAILURE); }
            Actor fresh = authorized(Operation.DELETE_PRODUCT, Integer.valueOf(expected.id));
            if (!sameSession(actor, fresh)) { return Result.failure(Status.DENIED); }
            try (Connection connection = open()) {
                connection.setAutoCommit(false);
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM product WHERE id = ? AND version = ?")) {
                    statement.setInt(1, expected.id);
                    statement.setInt(2, expected.version);
                    if (statement.executeUpdate() != 1) {
                        rollback(connection);
                        logOutcome(actor, "PRODUCT_DELETE", "product:" + expected.id, "CONFLICT");
                        return Result.failure(Status.CONFLICT);
                    }
                    auditWriter.append(connection, new AuditEvent(actor, "PRODUCT_DELETE", "product:" + expected.id, "SUCCESS", utcNow()));
                    connection.commit();
                    return Result.success(null);
                } catch (Exception ex) {
                    rollback(connection);
                    logOutcome(actor, "PRODUCT_DELETE", "product:" + expected.id, "FAILURE");
                    safeFallback();
                    return Result.failure(Status.FAILURE);
                }
            }
        } catch (Exception ex) {
            safeFallback();
            return Result.failure(Status.FAILURE);
        } finally { clear(currentPassword); }
    }

    public Result<PurchaseQuote> quotePurchase(int productId, String quantityText) {
        Actor actor = authorized(Operation.PURCHASE, Integer.valueOf(productId));
        if (actor == null) { return Result.failure(Status.DENIED); }
        if (!isValidPurchaseQuantityInput(quantityText)) {
            logRejected("PURCHASE_QUOTE", Integer.valueOf(productId));
            return Result.failure(Status.INVALID);
        }
        int quantity = parseInteger(quantityText);
        if (productId <= 0) {
            logRejected("PURCHASE_QUOTE", Integer.valueOf(productId));
            return Result.failure(Status.INVALID);
        }
        PurchaseQuote quote = null;
        Status failure = null;
        boolean insufficientStock = false;
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("SELECT id, name, stock, price_minor, version FROM product WHERE id = ?")) {
            statement.setInt(1, productId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    failure = Status.NOT_FOUND;
                } else {
                    int stock = row.getInt("stock");
                    long price = row.getLong("price_minor");
                    if (row.wasNull()) {
                        failure = Status.CONFLICT;
                    } else if (stock < quantity) {
                        failure = Status.CONFLICT;
                        insufficientStock = true;
                    } else {
                        long total = Math.multiplyExact(price, (long) quantity);
                        quote = new PurchaseQuote(UUID.randomUUID().toString(), actor, productId,
                                row.getString("name"), row.getInt("version"), stock, price, quantity, total);
                    }
                }
            }
        } catch (Exception ex) {
            logOutcome(actor, "PURCHASE", "product:" + productId, "FAILURE");
            safeFallback();
            return Result.failure(Status.FAILURE);
        }
        if (failure != null) {
            if (insufficientStock) { logRejected("PURCHASE_QUOTE", Integer.valueOf(productId)); }
            else { logOutcome(actor, "PURCHASE", "product:" + productId, failure.name()); }
            return Result.failure(failure);
        }
        pendingQuotes.put(quote.confirmationId, quote);
        return Result.success(quote);
    }

    public Result<PurchaseRecord> confirmPurchase(PurchaseQuote quote) {
        if (quote == null || pendingQuotes.remove(quote.confirmationId) != quote) {
            logRejected("PURCHASE_CONFIRM_REPLAY", quote == null ? null : Integer.valueOf(quote.productId));
            return Result.failure(Status.CONFLICT);
        }
        Actor actor = authorized(Operation.PURCHASE, Integer.valueOf(quote.productId));
        if (!sameSession(quote.actor, actor)) {
            logRejected("PURCHASE_CONFIRM_DENIED", Integer.valueOf(quote.productId));
            return Result.failure(Status.DENIED);
        }
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE product SET stock = stock - ?, version = version + 1 WHERE id = ? AND version = ? AND stock = ? AND stock >= ? AND price_minor = ?")) {
                    update.setInt(1, quote.quantity);
                    update.setInt(2, quote.productId);
                    update.setInt(3, quote.productVersion);
                    update.setInt(4, quote.stockAtQuote);
                    update.setInt(5, quote.quantity);
                    update.setLong(6, quote.unitPriceMinor);
                    changed = update.executeUpdate();
                }
                if (changed != 1) {
                    rollback(connection);
                    logOutcome(actor, "PURCHASE", "confirmation:" + quote.confirmationId, "CONFLICT");
                    return Result.failure(Status.CONFLICT);
                }
                String purchasedAt = utcNow();
                long totalMinor = Math.multiplyExact(quote.unitPriceMinor, (long) quote.quantity);
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO purchase_records(confirmation_id, buyer_id, buyer_name, product_id, product_name, unit_price_minor, quantity, purchased_at_utc) VALUES(?, ?, ?, ?, ?, ?, ?, ?)")) {
                    insert.setString(1, quote.confirmationId);
                    insert.setLong(2, actor.userId);
                    insert.setString(3, actor.username);
                    insert.setInt(4, quote.productId);
                    insert.setString(5, quote.productName);
                    insert.setLong(6, quote.unitPriceMinor);
                    insert.setInt(7, quote.quantity);
                    insert.setString(8, purchasedAt);
                    insert.executeUpdate();
                }
                long id = lastInsertId(connection);
                auditWriter.append(connection, new AuditEvent(actor, "PURCHASE", "confirmation:" + quote.confirmationId,
                        "SUCCESS", purchasedAt));
                connection.commit();
                return Result.success(new PurchaseRecord(id, actor.userId, actor.username, quote.productId,
                        quote.productName, Long.valueOf(quote.unitPriceMinor), quote.quantity, Long.valueOf(totalMinor), purchasedAt, false));
            } catch (Exception ex) {
                rollback(connection);
                logOutcome(actor, "PURCHASE", "confirmation:" + quote.confirmationId, "FAILURE");
                safeFallback();
                return Result.failure(Status.FAILURE);
            }
        } catch (Exception ex) {
            safeFallback();
            return Result.failure(Status.FAILURE);
        }
    }

    /** Discards a pending quote when the user cancels the confirmation prompt. */
    public void cancelPurchaseQuote(PurchaseQuote quote) {
        if (quote != null) { pendingQuotes.remove(quote.confirmationId, quote); }
    }

    /** Returns Client-owned rows or Manager-wide rows according to the trusted current account. */
    public Result<List<PurchaseRecord>> historyForCurrentActor(String search) {
        Actor actor;
        try { actor = security.currentActor(); }
        catch (RuntimeException ex) { safeFallback(); return Result.failure(Status.FAILURE); }
        if (actor == null) { logRejected("UNAUTHORIZED_READ_HISTORY", null); return Result.failure(Status.DENIED); }
        Operation operation = historyOperation(actor);
        if (operation == null || !isAllowedRole(actor, operation)) {
            logRejected("UNAUTHORIZED_READ_HISTORY", null);
            return Result.failure(Status.DENIED);
        }
        try {
            if (!security.authorize(actor, operation, null)) {
                logRejected("UNAUTHORIZED_READ_HISTORY", null);
                return Result.failure(Status.DENIED);
            }
            if (!validSearch(search)) { logRejected("HISTORY_SEARCH", null); return Result.failure(Status.INVALID); }
            try (Connection connection = open()) {
                List<PurchaseRecord> records = new ArrayList<PurchaseRecord>();
                String sql = operation == Operation.READ_OWN_HISTORY
                        ? "SELECT id, buyer_id, buyer_name, product_id, product_name, unit_price_minor, quantity, purchased_at_utc FROM purchase_records WHERE buyer_id = ? ORDER BY purchased_at_utc DESC, id DESC"
                        : "SELECT id, buyer_id, buyer_name, product_id, product_name, unit_price_minor, quantity, purchased_at_utc FROM purchase_records ORDER BY purchased_at_utc DESC, id DESC";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    if (operation == Operation.READ_OWN_HISTORY) { statement.setLong(1, actor.userId); }
                    try (ResultSet rows = statement.executeQuery()) {
                        while (rows.next()) {
                            long price = rows.getLong("unit_price_minor");
                            long total = Math.multiplyExact(price, (long) rows.getInt("quantity"));
                            PurchaseRecord record = new PurchaseRecord(rows.getLong("id"), rows.getLong("buyer_id"),
                                    rows.getString("buyer_name"), rows.getInt("product_id"), rows.getString("product_name"),
                                    Long.valueOf(price), rows.getInt("quantity"), Long.valueOf(total), rows.getString("purchased_at_utc"), false);
                            if (matches(record, search)) { records.add(record); }
                        }
                    }
                }
                if (operation == Operation.READ_ALL_HISTORY) { readLegacyRows(connection, search, records); }
                Collections.sort(records, new Comparator<PurchaseRecord>() {
                    public int compare(PurchaseRecord left, PurchaseRecord right) {
                        int timestampOrder = right.timestamp.compareTo(left.timestamp);
                        if (timestampOrder != 0) { return timestampOrder; }
                        return Long.compare(right.id.longValue(), left.id.longValue());
                    }
                });
                return Result.success(Collections.unmodifiableList(records));
            }
        } catch (Exception ex) {
            safeFallback();
            return Result.failure(Status.FAILURE);
        }
    }

    void setAuditWriterForTest(AuditWriter writer) { this.auditWriter = writer; }

    private boolean allowedForDisplay(Operation operation) {
        try {
            Actor actor = security.currentActor();
            return actor != null && isAllowedRole(actor, operation) && security.authorize(actor, operation, null);
        } catch (RuntimeException ex) {
            safeFallback();
            return false;
        }
    }

    private Actor authorized(Operation operation, Integer target) {
        try {
            Actor actor = security.currentActor();
            if (actor == null || !isAllowedRole(actor, operation) || !security.authorize(actor, operation, target)) {
                logRejected("UNAUTHORIZED_" + operation.name(), target);
                return null;
            }
            return actor;
        } catch (RuntimeException ex) {
            safeFallback();
            return null;
        }
    }

    private static boolean isAllowedRole(Actor actor, Operation operation) {
        if (actor == null) { return false; }
        switch (operation) {
            case READ_CATALOG: return actor.role == CLIENT || actor.role == STAFF || actor.role == MANAGER;
            case ADD_PRODUCT: case EDIT_PRODUCT: case DELETE_PRODUCT: return actor.role == STAFF || actor.role == MANAGER;
            case PURCHASE: case READ_OWN_HISTORY: return actor.role == CLIENT;
            case READ_ALL_HISTORY: return actor.role == MANAGER;
            default: return false;
        }
    }

    private static Operation historyOperation(Actor actor) {
        if (actor == null) { return null; }
        if (actor.role == CLIENT) { return Operation.READ_OWN_HISTORY; }
        if (actor.role == MANAGER) { return Operation.READ_ALL_HISTORY; }
        return null;
    }

    private VerificationResult reauthenticate(Actor actor, Operation operation, Integer target,
            String fingerprint, char[] password) {
        if (password == null) { return VerificationResult.REJECTED; }
        try {
            return security.verifyCurrentPassword(actor, operation, target, fingerprint, password)
                    ? VerificationResult.VERIFIED : VerificationResult.REJECTED;
        } catch (RuntimeException ex) {
            safeFallback();
            return VerificationResult.UNAVAILABLE;
        }
        finally { clear(password); }
    }

    private static boolean sameSession(Actor original, Actor fresh) {
        return original != null && fresh != null && original.userId == fresh.userId
                && original.role == fresh.role && original.username.equals(fresh.username)
                && original.sessionGeneration == fresh.sessionGeneration;
    }

    public static boolean isValidProductInput(String name, String stock, String price) {
        if (name == null || !NAME.matcher(name).matches() || stock == null || !UNSIGNED_INTEGER.matcher(stock).matches()
                || price == null || price.length() > 10 || !PRICE.matcher(price).matches()) { return false; }
        try {
            int count = parseInteger(stock);
            long minor = parseMinor(price);
            return count <= MAX_COUNT && minor > 0 && minor <= MAX_PRICE_MINOR;
        } catch (RuntimeException ex) { return false; }
    }

    public static boolean isValidPurchaseQuantityInput(String quantity) {
        if (quantity == null || !UNSIGNED_INTEGER.matcher(quantity).matches()) { return false; }
        try {
            int count = parseInteger(quantity);
            return count >= 1 && count <= MAX_COUNT;
        } catch (RuntimeException ex) { return false; }
    }

    private static int parseInteger(String value) { return Integer.parseInt(value); }

    private static long parseMinor(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    private static boolean validSearch(String search) {
        return search != null && search.length() <= 64 && (search.length() == 0 || SEARCH.matcher(search).matches());
    }

    public static boolean isValidHistorySearchInput(String search) { return validSearch(search); }

    private static boolean matches(PurchaseRecord record, String search) {
        return search.length() == 0 || record.buyerName.contains(search) || record.productName.contains(search);
    }

    private static void readLegacyRows(Connection connection, String search, List<PurchaseRecord> records) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, username, name, stock, timestamp FROM history ORDER BY timestamp DESC, id DESC");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                PurchaseRecord legacy = new PurchaseRecord(rows.getLong("id"), null, rows.getString("username"),
                        null, rows.getString("name"), null, rows.getInt("stock"), null,
                        rows.getString("timestamp"), true);
                if (matches(legacy, search)) { records.add(legacy); }
            }
        }
    }

    private void logRejected(String event, Object target) {
        try {
            Actor actor = security.currentActor();
            String targetText = target instanceof CatalogProduct ? "product:" + ((CatalogProduct) target).id
                    : target instanceof Integer ? "product:" + target : "input";
            try (Connection connection = open()) {
                auditWriter.append(connection, new AuditEvent(actor, event, targetText, "REJECTED", utcNow()));
            }
        } catch (Exception ignored) { /* Failure logging is best-effort and cannot recurse. */ }
    }

    /** Writes a failure outcome after rollback using an independent, non-recursive audit attempt. */
    private void logOutcome(Actor actor, String event, String target, String outcome) {
        try (Connection connection = open()) {
            new SqliteAuditWriter().append(connection, new AuditEvent(actor, event, target, outcome, utcNow()));
        } catch (Exception ignored) { /* Storage may be unavailable; audit failure never retries itself. */ }
    }

    private void safeFallback() {
        try { security.onUnrecoverableFailure(); } catch (RuntimeException ignored) { }
    }

    private Connection open() throws SQLException { return DriverManager.getConnection("jdbc:sqlite:" + database.toString()); }

    private static void rollback(Connection connection) {
        try { connection.rollback(); } catch (SQLException ignored) { }
    }

    private static void clear(char[] value) { if (value != null) { Arrays.fill(value, '\0'); } }

    private static String utcNow() { return Instant.now().toString(); }

    private static int lastInsertId(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT last_insert_rowid()");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) { throw new SQLException("Insert identifier unavailable"); }
            return result.getInt(1);
        }
    }

    private static boolean isConstraint(SQLException ex) {
        for (SQLException current = ex; current != null; current = current.getNextException()) {
            if (current.getErrorCode() == 19) { return true; }
        }
        return false;
    }

    private static void addColumnIfMissing(Connection connection, String table, String column, String definition) throws SQLException {
        boolean present = false;
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet columns = statement.executeQuery()) {
            while (columns.next()) { if (column.equalsIgnoreCase(columns.getString("name"))) { present = true; break; } }
        }
        if (!present) {
            try (PreparedStatement statement = connection.prepareStatement("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition)) {
                statement.execute();
            }
        }
    }

    private static void migrateLegacyPrices(Connection connection) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT id, price FROM product WHERE price_minor IS NULL");
             ResultSet rows = select.executeQuery();
             PreparedStatement update = connection.prepareStatement("UPDATE product SET price_minor = ? WHERE id = ? AND price_minor IS NULL")) {
            while (rows.next()) {
                double raw = rows.getDouble("price");
                if (rows.wasNull() || !Double.isFinite(raw)) { continue; }
                try {
                    long minor = BigDecimal.valueOf(raw).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
                    if (minor > 0 && minor <= MAX_PRICE_MINOR) {
                        update.setLong(1, minor);
                        update.setInt(2, rows.getInt("id"));
                        update.executeUpdate();
                    }
                } catch (ArithmeticException ignored) { /* Preserve unrepresentable legacy price as unavailable. */ }
            }
        }
    }

    private static final class SqliteAuditWriter implements AuditWriter {
        public void append(Connection connection, AuditEvent event) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO logs(event, username, desc, timestamp) VALUES(?, ?, ?, ?)")) {
                statement.setString(1, event.event);
                statement.setString(2, event.actor == null ? "UNKNOWN" : event.actor.username);
                statement.setString(3, event.target + " outcome=" + event.outcome);
                statement.setString(4, event.timestamp);
                statement.executeUpdate();
            }
        }
    }

    private enum VerificationResult { VERIFIED, REJECTED, UNAVAILABLE }

    public enum Operation { READ_CATALOG, ADD_PRODUCT, EDIT_PRODUCT, DELETE_PRODUCT, PURCHASE, READ_OWN_HISTORY, READ_ALL_HISTORY }
    public enum Status { SUCCESS, INVALID, DENIED, REAUTH_REQUIRED, NOT_FOUND, CONFLICT, FAILURE }

    public interface SecurityGateway {
        /** Resolves identity, role and session generation from trusted session state. */
        Actor currentActor();
        /** Delegates current account/role/resource checks to the shared authorization provider. */
        boolean authorize(Actor actor, Operation operation, Integer targetId);
        /** Verifies the current password for exactly this pending action and clears no shared state. */
        boolean verifyCurrentPassword(Actor actor, Operation operation, Integer targetId, String requestFingerprint, char[] password);
        /** Invalidates protected state and navigates to the safe unprivileged state. */
        void onUnrecoverableFailure();
    }

    interface AuditWriter { void append(Connection connection, AuditEvent event) throws Exception; }

    public static final class Actor {
        private final long userId;
        private final String username;
        private final int role;
        private final long sessionGeneration;
        public Actor(long userId, String username, int role, long sessionGeneration) {
            this.userId = userId; this.username = username; this.role = role; this.sessionGeneration = sessionGeneration;
        }
        public long getUserId() { return userId; }
        public String getUsername() { return username; }
        public int getRole() { return role; }
        public long getSessionGeneration() { return sessionGeneration; }
    }

    public static final class AuditEvent {
        private final Actor actor;
        private final String event;
        private final String target;
        private final String outcome;
        private final String timestamp;
        AuditEvent(Actor actor, String event, String target, String outcome, String timestamp) {
            this.actor = actor; this.event = event; this.target = target; this.outcome = outcome; this.timestamp = timestamp;
        }
        public Actor getActor() { return actor; }
        public String getEvent() { return event; }
        public String getTarget() { return target; }
        public String getOutcome() { return outcome; }
        public String getTimestamp() { return timestamp; }
    }

    public static final class CatalogProduct {
        private final int id; private final String name; private final int stock; private final Long priceMinor; private final int version;
        CatalogProduct(int id, String name, int stock, Long priceMinor, int version) {
            this.id = id; this.name = name; this.stock = stock; this.priceMinor = priceMinor; this.version = version;
        }
        public int getId() { return id; }
        public String getName() { return name; }
        public int getStock() { return stock; }
        public Long getPriceMinor() { return priceMinor; }
        public int getVersion() { return version; }
        public String getPriceDisplay() { return priceMinor == null ? "Unavailable" : formatMinor(priceMinor.longValue()); }
    }

    public static final class PurchaseQuote {
        private final String confirmationId; private final Actor actor; private final int productId; private final String productName;
        private final int productVersion; private final int stockAtQuote; private final long unitPriceMinor; private final int quantity; private final long totalMinor;
        private PurchaseQuote(String confirmationId, Actor actor, int productId, String productName, int productVersion,
                int stockAtQuote, long unitPriceMinor, int quantity, long totalMinor) {
            this.confirmationId = confirmationId; this.actor = actor; this.productId = productId; this.productName = productName;
            this.productVersion = productVersion; this.stockAtQuote = stockAtQuote; this.unitPriceMinor = unitPriceMinor;
            this.quantity = quantity; this.totalMinor = totalMinor;
        }
        public String getProductName() { return productName; }
        public int getQuantity() { return quantity; }
        public long getUnitPriceMinor() { return unitPriceMinor; }
        public long getTotalMinor() { return totalMinor; }
        public String getUnitPriceDisplay() { return formatMinor(unitPriceMinor); }
        public String getTotalDisplay() { return formatMinor(totalMinor); }
    }

    public static final class PurchaseRecord {
        private final Long id; private final Long buyerId; private final String buyerName; private final Integer productId;
        private final String productName; private final Long unitPriceMinor; private final int quantity; private final Long totalMinor;
        private final String timestamp; private final boolean legacy;
        PurchaseRecord(Long id, Long buyerId, String buyerName, Integer productId, String productName,
                Long unitPriceMinor, int quantity, Long totalMinor, String timestamp, boolean legacy) {
            this.id = id; this.buyerId = buyerId; this.buyerName = buyerName; this.productId = productId;
            this.productName = productName; this.unitPriceMinor = unitPriceMinor; this.quantity = quantity;
            this.totalMinor = totalMinor; this.timestamp = timestamp; this.legacy = legacy;
        }
        public Long getId() { return id; }
        public Long getBuyerId() { return buyerId; }
        public String getBuyerName() { return buyerName; }
        public Integer getProductId() { return productId; }
        public String getProductName() { return productName; }
        public Long getUnitPriceMinor() { return unitPriceMinor; }
        public int getQuantity() { return quantity; }
        public Long getTotalMinor() { return totalMinor; }
        public String getTimestamp() { return timestamp; }
        public boolean isLegacy() { return legacy; }
        public boolean hasPrice() { return unitPriceMinor != null && totalMinor != null; }
        public String getUnitPriceDisplay() { return hasPrice() ? formatMinor(unitPriceMinor.longValue()) : "Unavailable"; }
        public String getTotalDisplay() { return hasPrice() ? formatMinor(totalMinor.longValue()) : "Unavailable"; }
    }

    public static final class Result<T> {
        private final Status status; private final T value;
        private Result(Status status, T value) { this.status = status; this.value = value; }
        static <T> Result<T> success(T value) { return new Result<T>(Status.SUCCESS, value); }
        static <T> Result<T> failure(Status status) { return new Result<T>(status, null); }
        public boolean isSuccess() { return status == Status.SUCCESS; }
        public Status getStatus() { return status; }
        public T getValue() { return value; }
        public String getSafeMessage() {
            switch (status) {
                case SUCCESS: return "Completed.";
                case INVALID: return "Please check the entered information.";
                case DENIED: return "This action is not available for the current account.";
                case REAUTH_REQUIRED: return "Password verification failed or was cancelled.";
                case NOT_FOUND: return "The selected item is no longer available.";
                case CONFLICT: return "The information changed. Refresh and try again.";
                default: return "The action could not be completed.";
            }
        }
    }

    private static String formatMinor(long minor) {
        return BigDecimal.valueOf(minor, 2).setScale(2).toPlainString();
    }
}
