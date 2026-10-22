package ai.omnirush.chameleon.vpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Handles notification actions for the VPN foreground notification
 * (currently: Disconnect). Kept separate from the service so the
 * PendingIntent survives service restarts.
 */
public class VpnActionReceiver extends BroadcastReceiver {
    private static final String TAG = "PhantomVpn";

    public static final String ACTION_DISCONNECT =
            "ai.omnirush.chameleon.vpn.NOTIF_DISCONNECT";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (ACTION_DISCONNECT.equals(intent.getAction())) {
            Log.i(TAG, "Notification action: disconnect");
            Intent svc = PhantomVpnService.disconnectIntent(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        }
    }
}
