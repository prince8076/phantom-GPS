package ai.omnirush.chameleon.vpn;

/**
 * Connection state machine for the VPN core.
 *
 * <p>Modeled on the states observed in ProtonVPN's client
 * (CheckingAvailability / Connected / Connecting / Disabled /
 * Disconnecting / Error / Loading / Reconnecting / ScanningPorts /
 * WaitingForNetwork) but trimmed to the states our service actually
 * drives. Clean-room implementation.
 */
public enum VpnState {
    /** No tunnel and none requested. */
    DISABLED,
    /** Actively trying the first (or only) protocol candidate. */
    CONNECTING,
    /** Waiting on network availability before retrying. */
    WAITING_FOR_NETWORK,
    /** Scanning / probing the next protocol candidate. */
    SCANNING,
    /** Tunnel is up and passing traffic. */
    CONNECTED,
    /** Tunnel dropped unexpectedly; retrying with backoff. */
    RECONNECTING,
    /** User asked to disconnect; teardown in progress. */
    DISCONNECTING,
    /** Terminal failure for this attempt (bad config, auth expired, ...). */
    ERROR;

    /** True while the service should keep a foreground notification. */
    public boolean isActive() {
        return this != DISABLED && this != ERROR;
    }
}
