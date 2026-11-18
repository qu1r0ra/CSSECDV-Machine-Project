package Controller;

/**
 * The single site-wide authorization component. Every protected view or operation asks this class,
 * which re-validates the signed-in account against the database before consulting the role table.
 * Anything unexpected denies.
 */
public final class AccessControl {

    /** Every protected feature and the roles allowed to use it (Client 2, Staff 3, Manager 4, Admin 5). */
    public enum Feature {
        VIEW_ADMIN_HOME(5), VIEW_MANAGER_HOME(4), VIEW_STAFF_HOME(3), VIEW_CLIENT_HOME(2),
        MANAGE_USERS(5), VIEW_LOGS(5), MANAGE_LOGS(5),
        READ_CATALOG(2, 3, 4), MANAGE_PRODUCTS(3, 4), PURCHASE(2), READ_OWN_HISTORY(2), READ_ALL_HISTORY(4);

        private final int[] roles;
        Feature(int... roles) { this.roles = roles; }
        boolean allows(int role) {
            for (int r : roles) { if (r == role) { return true; } }
            return false;
        }
    }

    private final AuthService auth;

    public AccessControl(AuthService auth) {
        if (auth == null) { throw new IllegalArgumentException("auth is required"); }
        this.auth = auth;
    }

    /** Pure role table lookup; Disabled (1) and unknown roles are never allowed. */
    public static boolean roleMayUse(int role, Feature feature) {
        return feature != null && role >= 2 && role <= 5 && feature.allows(role);
    }

    /** Home panel a role belongs to, or null (Disabled/unknown roles have none). */
    public static Feature homeFor(int role) {
        switch (role) {
            case 5: return Feature.VIEW_ADMIN_HOME;
            case 4: return Feature.VIEW_MANAGER_HOME;
            case 3: return Feature.VIEW_STAFF_HOME;
            case 2: return Feature.VIEW_CLIENT_HOME;
            default: return null;
        }
    }

    /** Returns the verified actor when the current session may use the feature; otherwise null (and the denial is reported). */
    public BundleBService.Actor require(Feature feature) {
        try {
            BundleBService.Actor actor = auth.currentActor();
            if (actor != null && roleMayUse(actor.getRole(), feature)) { return actor; }
            auth.audit().record("ACCESS_DENIED", actor == null ? "UNKNOWN" : actor.getUsername(), String.valueOf(feature));
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public boolean check(Feature feature) { return require(feature) != null; }
}
