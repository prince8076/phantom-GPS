package ai.omnirush.chameleon.vpn;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists small VPN-client settings: chiefly the control-hub base URL the
 * phone was paired with (entered via the dashboard's QR pairing flow).
 *
 * <p>Clean-room implementation; no Proton code involved.
 */
public final class HubSettings {

    private static final String PREFS = "phantom_vpn_settings";
    private static final String KEY_HUB_URL = "hub_url";
    private static final String KEY_AUTO_CONNECT = "auto_connect";

    private HubSettings() { /* static only */ }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** e.g. "http://192.168.1.5:3300"; empty when never paired. */
    public static String getHubUrl(Context ctx) {
        return prefs(ctx).getString(KEY_HUB_URL, "");
    }

    public static void setHubUrl(Context ctx, String url) {
        prefs(ctx).edit().putString(KEY_HUB_URL,
                url == null ? "" : url.trim()).apply();
    }

    public static boolean isAutoConnectEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AUTO_CONNECT, false);
    }

    public static void setAutoConnectEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply();
    }
}
