package ai.omnirush.chameleon.vpn;

/**
 * Supplies short-lived tunnel credentials, mirroring the certificate-refresh
 * concept observed in ProtonVPN's client (short-lived certs refreshed in the
 * background before expiry). The control hub issues the credentials; this
 * interface is the seam the sync layer implements.
 *
 * <p>Clean-room interface — no Proton code involved.
 */
public interface CredentialProvider {

    /**
     * Returns a config with fresh credentials if the current ones are stale
     * (see {@link VpnConfig#credentialsNeedRefresh()}), otherwise returns
     * the input unchanged. May perform network I/O — never call on the main
     * thread.
     */
    VpnConfig refreshIfNeeded(VpnConfig config) throws Exception;

    /** No-op implementation for builds without a control hub. */
    class Noop implements CredentialProvider {
        @Override public VpnConfig refreshIfNeeded(VpnConfig config) {
            return config;
        }
    }
}
