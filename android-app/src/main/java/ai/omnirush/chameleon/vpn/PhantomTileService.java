package ai.omnirush.chameleon.vpn;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

/**
 * Quick Settings tile: tap to connect with the last config, tap again to
 * disconnect. Mirrors the quick-tile concept observed in leading VPN
 * clients. Clean-room implementation.
 *
 * <p>Requires API 24+ (minSdk here is 26, so always available). Declare in
 * the manifest with {@code BIND_QUICK_SETTINGS_TILE} (see updated
 * AndroidManifest.xml).
 */
public class PhantomTileService extends TileService {
    private static final String TAG = "PhantomVpn";

    private final VpnStateListener listener = new VpnStateListener() {
        @Override public void onVpnStateChanged(VpnState newState, String detail) {
            refreshTile();
        }
    };

    @Override
    public void onStartListening() {
        super.onStartListening();
        VpnStateStore.get().addListener(listener);
        refreshTile();
    }

    @Override
    public void onStopListening() {
        VpnStateStore.get().removeListener(listener);
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        VpnState state = VpnStateStore.get().getState();
        if (state == VpnState.CONNECTED || state == VpnState.CONNECTING
                || state == VpnState.RECONNECTING || state == VpnState.SCANNING
                || state == VpnState.WAITING_FOR_NETWORK) {
            Log.i(TAG, "Tile: disconnect requested");
            startServiceCompat(PhantomVpnService.disconnectIntent(this));
        } else if (state == VpnState.DISABLED || state == VpnState.ERROR) {
            String configJson = VpnStateStore.get().getLastConfigJson(this);
            if (configJson == null || configJson.isEmpty()) {
                Log.i(TAG, "Tile: no saved config — opening app for setup");
                Intent launch = getPackageManager()
                        .getLaunchIntentForPackage(getPackageName());
                if (launch != null) startActivityAndCollapse(launch);
                return;
            }
            Log.i(TAG, "Tile: connect requested");
            Intent svc = new Intent(this, PhantomVpnService.class)
                    .setAction(PhantomVpnService.ACTION_CONNECT)
                    .putExtra(PhantomVpnService.EXTRA_CONFIG_JSON, configJson);
            startServiceCompat(svc);
        }
        // DISCONNECTING: ignore taps until it settles.
    }

    private void startServiceCompat(Intent svc) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }

    private void refreshTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        VpnState state = VpnStateStore.get().getState();
        switch (state) {
            case CONNECTED:
                tile.setState(Tile.STATE_ACTIVE);
                tile.setSubtitle("Connected");
                break;
            case CONNECTING:
            case SCANNING:
            case WAITING_FOR_NETWORK:
            case RECONNECTING:
                tile.setState(Tile.STATE_ACTIVE);
                tile.setSubtitle("Connecting…");
                break;
            case DISCONNECTING:
                tile.setState(Tile.STATE_INACTIVE);
                tile.setSubtitle("Disconnecting…");
                break;
            case DISABLED:
            case ERROR:
            default:
                tile.setState(Tile.STATE_INACTIVE);
                tile.setSubtitle(state == VpnState.ERROR ? "Error" : "Off");
                break;
        }
        tile.setLabel("Phantom VPN");
        try {
            tile.setIcon(Icon.createWithResource(this,
                    android.R.drawable.ic_lock_lock));
        } catch (Exception e) {
            // Icon is best-effort on some OEM skins.
        }
        tile.updateTile();
    }
}
