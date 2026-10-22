package ai.omnirush.chameleon.vpn;

/**
 * Integration seam between {@link PhantomVpnService} and the WireGuard
 * protocol engine. The service drives the connection lifecycle (state
 * machine, smart-protocol fallback, kill switch, notifications) and
 * delegates the actual WireGuard handshake + crypto to an implementation
 * of this interface.
 *
 * <p>The bundled implementation is {@link WireGuardGoBackend}, written
 * against the public API of the open-source wireguard-android
 * {@code com.wireguard.android:tunnel} artifact. No wireguard-android
 * sources are vendored — it arrives as a Gradle dependency (see
 * BUILD_NOTES.md).
 */
public interface WgTunnelBackend {

    /** Thrown for backend-level failures (bad keys, endpoint unreachable...). */
    class BackendException extends Exception {
        public BackendException(String message) { super(message); }
        public BackendException(String message, Throwable cause) { super(message, cause); }
    }

    /** Simple traffic counters for the notification / dashboard. */
    class Stats {
        public final long rxBytes;
        public final long txBytes;
        public Stats(long rxBytes, long txBytes) {
            this.rxBytes = rxBytes;
            this.txBytes = txBytes;
        }
    }

    /**
     * True when the backend creates and owns the TUN interface itself
     * (stock wireguard-android GoBackend does, via its own inner VpnService).
     * When true, {@link PhantomVpnService} skips its own
     * {@code VpnService.Builder.establish()} call.
     */
    boolean managesOwnInterface();

    /** True when this backend can serve WireGuard-over-TCP/TLS candidates. */
    boolean supportsTcpTlsTransports();

    /**
     * Brings the tunnel up for the given config + winning smart-protocol
     * candidate. Must return promptly; the handshake completes async and
     * {@link #isRunning()} reflects the live state.
     */
    void connect(VpnConfig config, SmartProtocolSelector.Candidate candidate)
            throws BackendException;

    /** Tears the tunnel down. Idempotent. */
    void disconnect();

    boolean isRunning();

    /** Zeroed stats when unknown. */
    Stats getStats();

    /** Human-readable backend version, e.g. wireguard-go version string. */
    String getVersion();
}
