package ai.omnirush.chameleon.vpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * Auto-reconnect after device boot, mirroring the concept observed in
 * leading VPN clients (boot receiver + persisted "was connected" flag).
 * Clean-room implementation.
 *
 * <p>If the user enabled auto-connect and the tunnel was up at shutdown,
 * the last {@link VpnConfig} is re-applied. Note: on first boot after
 * install the user must still grant VPN permission once via
 * {@code VpnService.prepare()} in the UI — the receiver cannot conjure
 * that consent.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "PhantomVpn";
    private static final String PREFS = "phantom_vpn_prefs";
    private static final String KEY_AUTO_CONNECT = "auto_connect";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (!boot) return;

        SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean autoConnect = prefs.getBoolean(KEY_AUTO_CONNECT, false);
        if (!autoConnect) {
            Log.i(TAG, "Boot: auto-connect disabled, staying idle");
            return;
        }

        VpnStateStore store = VpnStateStore.get();
        if (!store.wasConnectedAtShutdown(context)) {
            Log.i(TAG, "Boot: tunnel was not connected at shutdown");
            return;
        }

        String configJson = store.getLastConfigJson(context);
        if (configJson == null || configJson.isEmpty()) {
            Log.w(TAG, "Boot: no saved config to reconnect with");
            return;
        }

        Log.i(TAG, "Boot: auto-reconnecting VPN");
        Intent svc = new Intent(context, PhantomVpnService.class)
                .setAction(PhantomVpnService.ACTION_CONNECT)
                .putExtra(PhantomVpnService.EXTRA_CONFIG_JSON, configJson);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }
    }

    /** Called from settings UI. */
    public static void setAutoConnectEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_AUTO_CONNECT, enabled).apply();
    }

    public static boolean isAutoConnectEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_CONNECT, false);
    }
}
