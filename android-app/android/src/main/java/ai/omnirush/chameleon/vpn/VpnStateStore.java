package ai.omnirush.chameleon.vpn;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Process-wide, thread-safe holder for the current {@link VpnState}.
 *
 * <p>Any component (service, tile, activity, boot receiver) can read the
 * current state or subscribe for changes without binding to the service.
 * The last non-transient state is persisted so {@link BootReceiver} can
 * decide whether to auto-reconnect after a reboot.
 */
public class VpnStateStore {
    private static final String TAG = "PhantomVpn";
    private static final String PREFS = "phantom_vpn_state";
    private static final String KEY_LAST_STATE = "last_state";
    private static final String KEY_LAST_CONFIG = "last_config_json";

    private static volatile VpnStateStore instance;

    private final Object lock = new Object();
    private final List<VpnStateListener> listeners = new ArrayList<VpnStateListener>();
    private VpnState state = VpnState.DISABLED;
    private String detail = "";

    private VpnStateStore() { }

    public static VpnStateStore get() {
        VpnStateStore local = instance;
        if (local == null) {
            synchronized (VpnStateStore.class) {
                local = instance;
                if (local == null) {
                    instance = local = new VpnStateStore();
                }
            }
        }
        return local;
    }

    public VpnState getState() {
        synchronized (lock) {
            return state;
        }
    }

    public String getDetail() {
        synchronized (lock) {
            return detail;
        }
    }

    public void setState(VpnState newState, String detail) {
        List<VpnStateListener> snapshot;
        synchronized (lock) {
            if (state == newState) return;
            state = newState;
            this.detail = detail == null ? "" : detail;
            snapshot = new ArrayList<VpnStateListener>(listeners);
        }
        Log.i(TAG, "VPN state -> " + newState + (detail == null || detail.isEmpty() ? "" : " (" + detail + ")"));
        for (VpnStateListener l : snapshot) {
            try {
                l.onVpnStateChanged(newState, this.detail);
            } catch (RuntimeException e) {
                Log.w(TAG, "VpnStateListener threw", e);
            }
        }
    }

    public void addListener(VpnStateListener listener) {
        synchronized (lock) {
            if (!listeners.contains(listener)) listeners.add(listener);
        }
    }

    public void removeListener(VpnStateListener listener) {
        synchronized (lock) {
            listeners.remove(listener);
        }
    }

    /** Persists the config + a marker that we were connected, for boot auto-connect. */
    public void persistSession(Context ctx, String configJson, boolean wasConnected) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_LAST_CONFIG, configJson)
                .putString(KEY_LAST_STATE, wasConnected ? VpnState.CONNECTED.name() : VpnState.DISABLED.name())
                .apply();
    }

    public String getLastConfigJson(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_CONFIG, null);
    }

    public boolean wasConnectedAtShutdown(Context ctx) {
        String s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_STATE, VpnState.DISABLED.name());
        return VpnState.CONNECTED.name().equals(s);
    }

    public void clearSession(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_LAST_CONFIG)
                .putString(KEY_LAST_STATE, VpnState.DISABLED.name())
                .apply();
    }
}
