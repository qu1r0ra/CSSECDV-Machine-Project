package Controller;

/** Bundle B's identity, authorization and re-authentication provider, backed by the shared auth services. */
public final class AppSecurityGateway implements BundleBService.SecurityGateway {
    private final AuthService auth;
    private final Runnable safeState;

    public AppSecurityGateway(AuthService auth, Runnable safeState) {
        if (auth == null) { throw new IllegalArgumentException("auth is required"); }
        this.auth = auth;
        this.safeState = safeState;
    }

    public BundleBService.Actor currentActor() { return auth.currentActor(); }

    public boolean authorize(BundleBService.Actor actor, BundleBService.Operation operation, Integer targetId) {
        try {
            AccessControl.Feature feature = featureFor(operation);
            BundleBService.Actor now = auth.currentActor();
            return feature != null && AuthService.sameSession(actor, now) && AccessControl.roleMayUse(now.getRole(), feature);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean verifyCurrentPassword(BundleBService.Actor actor, BundleBService.Operation operation,
            Integer targetId, String requestFingerprint, char[] password) {
        if (operation == null || requestFingerprint == null) { return false; }
        return auth.verifyCurrentPassword(actor, password);
    }

    public void onUnrecoverableFailure() {
        auth.logout();
        if (safeState != null) { safeState.run(); }
    }

    static AccessControl.Feature featureFor(BundleBService.Operation operation) {
        if (operation == null) { return null; }
        switch (operation) {
            case READ_CATALOG: return AccessControl.Feature.READ_CATALOG;
            case ADD_PRODUCT: case EDIT_PRODUCT: case DELETE_PRODUCT: return AccessControl.Feature.MANAGE_PRODUCTS;
            case PURCHASE: return AccessControl.Feature.PURCHASE;
            case READ_OWN_HISTORY: return AccessControl.Feature.READ_OWN_HISTORY;
            case READ_ALL_HISTORY: return AccessControl.Feature.READ_ALL_HISTORY;
            default: return null;
        }
    }
}
