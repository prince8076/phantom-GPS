package ai.omnirush.chameleon.vpn;

/**
 * Observer for {@link VpnState} transitions, delivered on the thread that
 * called {@link VpnStateStore#setState}. UI layers should hop to the main
 * thread themselves.
 */
public interface VpnStateListener {
    void onVpnStateChanged(VpnState newState, String detail);
}
