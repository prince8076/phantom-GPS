package ai.omnirush.chameleon.vpn;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Kill-switch management.
 *
 * <p>Concept observed in ProtonVPN's client: on Android the kill switch is
 * the OS itself — "Always-on VPN" plus "Block connections without VPN"
 * (lockdown). There is no public API to query those OS settings, so this
 * class models the expected state from our own preferences (like their
 * VpnAlwaysOn model) and guides the user to the system VPN screen to flip
 * the toggles. Enforcement while OUR tunnel is up is additionally done via
 * {@code VpnService.Builder.setBlocking(true)} in {@link PhantomVpnService}.
 * Clean-room implementation.
 */
public class KillSwitchManager {
    private static final String TAG = "PhantomVpn";
    private static final String PREFS = "phantom_vpn_killswitch";
    private static final String KEY_EXPECTED = "killswitch_expected";
    private static final String KEY_GUIDANCE_SHOWN = "guidance_shown";

    /** Intent action that opens the system VPN settings page. */
    private static final String ACTION_VPN_SETTINGS = "android.net.vpn.SETTINGS";

    private final Context appContext;

    public KillSwitchManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Whether the user wants the kill switch armed (from VpnConfig). */
    public boolean isArmed() {
        return prefs().getBoolean(KEY_EXPECTED, true);
    }

    public void setArmed(boolean armed) {
        prefs().edit().putBoolean(KEY_EXPECTED, armed).apply();
        Log.i(TAG, "Kill switch " + (armed ? "armed" : "disarmed"));
    }

    public boolean wasGuidanceShown() {
        return prefs().getBoolean(KEY_GUIDANCE_SHOWN, false);
    }

    public void markGuidanceShown() {
        prefs().edit().putBoolean(KEY_GUIDANCE_SHOWN, true).apply();
    }

    /**
     * Opens the system VPN settings so the user can enable "Always-on VPN"
     * and "Block connections without VPN" for Phantom GPS.
     * Returns false if no settings activity handles the intent.
     */
    public boolean openSystemVpnSettings() {
        try {
            Intent i = new Intent(ACTION_VPN_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(appContext.getPackageManager()) != null) {
                appContext.startActivity(i);
                return true;
            }
            // Fallback: generic Settings.
            Intent fallback = new Intent(android.provider.Settings.ACTION_SETTINGS);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(fallback);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Could not open VPN settings", e);
            return false;
        }
    }

    /**
     * Shows the one-time guidance dialog explaining the two OS toggles.
     * Call from an Activity context before first connect when the kill
     * switch is armed.
     */
    public void showSetupGuidance(Context activityContext, final Runnable onDone) {
        new AlertDialog.Builder(activityContext)
                .setTitle("Enable the kill switch")
                .setMessage("For full leak protection, Android itself must block traffic " +
                        "when the VPN drops:\n\n" +
                        "1. Tap \"Open VPN settings\" below.\n" +
                        "2. Tap the gear icon next to Phantom GPS.\n" +
                        "3. Turn ON \"Always-on VPN\".\n" +
                        "4. Turn ON \"Block connections without VPN\".\n\n" +
                        "Until then, Phantom GPS still blocks traffic while its own " +
                        "tunnel interface is active.")
                .setPositiveButton("Open VPN settings", (d, w) -> {
                    markGuidanceShown();
                    openSystemVpnSettings();
                    if (onDone != null) onDone.run();
                })
                .setNegativeButton("Later", (d, w) -> {
                    if (onDone != null) onDone.run();
                })
                .setCancelable(true)
                .show();
    }

    /**
     * Best-effort check: on API 24+ we can at least verify our own service
     * is the prepared VPN. True OS always-on/lockdown state is not exposed
     * by any public API, so this intentionally reports the *expected* state
     * (user preference), documented as such.
     */
    public boolean isLockdownExpected() {
        return isArmed() && wasGuidanceShown();
    }
}
