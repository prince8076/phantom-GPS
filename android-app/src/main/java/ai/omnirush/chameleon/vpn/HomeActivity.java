package ai.omnirush.chameleon.vpn;

import ai.omnirush.chameleon.R;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * VPN Home tab: protection status header, fastest-server connect card,
 * session stats and protection quick-glance — the app's main screen.
 *
 * <p>Connect flow reuses the VpnConnector pipeline (hub config → consent →
 * PhantomVpnService) with the persisted VpnSettings applied.
 */
public class HomeActivity extends Activity {

    private static final int REQ_VPN_CONSENT = 1001;

    private TextView lockIcon;
    private TextView statusTitle;
    private TextView ipPill;
    private TextView serverTitle;
    private TextView serverSub;
    private Button btnConnect;
    private LinearLayout statsRow;
    private TextView statTime;
    private TextView statProto;
    private TextView statServer;
    private TextView netshieldState;
    private TextView killswitchState;
    private TextView splittunnelState;

    private VpnConfig pendingConfig;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private long connectedAtMillis = 0;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (connectedAtMillis != 0) {
                long secs = (System.currentTimeMillis() - connectedAtMillis) / 1000;
                statTime.setText(String.format("%02d:%02d:%02d",
                        secs / 3600, (secs % 3600) / 60, secs % 60));
                uiHandler.postDelayed(this, 1000);
            }
        }
    };

    private final VpnStateListener stateListener = new VpnStateListener() {
        @Override public void onVpnStateChanged(final VpnState s, final String detail) {
            runOnUiThread(new Runnable() {
                @Override public void run() { renderState(s, detail); }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        lockIcon = findViewById(R.id.home_lock_icon);
        statusTitle = findViewById(R.id.home_status_title);
        ipPill = findViewById(R.id.home_ip_pill);
        serverTitle = findViewById(R.id.home_server_title);
        serverSub = findViewById(R.id.home_server_sub);
        btnConnect = findViewById(R.id.home_btn_connect);
        statsRow = findViewById(R.id.home_stats_row);
        statTime = findViewById(R.id.home_stat_time);
        statProto = findViewById(R.id.home_stat_proto);
        statServer = findViewById(R.id.home_stat_server);
        netshieldState = findViewById(R.id.home_netshield_state);
        killswitchState = findViewById(R.id.home_killswitch_state);
        splittunnelState = findViewById(R.id.home_splittunnel_state);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onConnectTapped(); }
        });

        BottomNavHelper.wire(this, BottomNavHelper.TAB_HOME);
        refreshProtectionCards();
        renderState(VpnStateStore.get().getState(), VpnStateStore.get().getDetail());
        handleIncomingConfig(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingConfig(intent);
    }

    /** Starts the consent flow for a config handed over by Countries/Profiles. */
    private void handleIncomingConfig(Intent intent) {
        if (intent == null || !intent.hasExtra("connect_config")) return;
        try {
            VpnConfig config = VpnConfig.fromJsonString(
                    intent.getStringExtra("connect_config"));
            intent.removeExtra("connect_config");
            requestConsentAndStart(config);
        } catch (Exception e) {
            Toast.makeText(this, "Bad connection config: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        VpnStateStore.get().addListener(stateListener);
        refreshProtectionCards();
        renderState(VpnStateStore.get().getState(), VpnStateStore.get().getDetail());
    }

    @Override
    protected void onPause() {
        VpnStateStore.get().removeListener(stateListener);
        uiHandler.removeCallbacks(ticker);
        super.onPause();
    }

    private void refreshProtectionCards() {
        netshieldState.setText(VpnSettings.netShieldLabel(VpnSettings.getNetShield(this)));
        killswitchState.setText(VpnSettings.isKillSwitch(this) ? "On" : "Off");
        splittunnelState.setText(VpnSettings.isSplitTunnel(this) ? "On" : "Off");
    }

    private void onConnectTapped() {
        VpnState state = VpnStateStore.get().getState();
        if (state == VpnState.CONNECTED || state == VpnState.CONNECTING
                || state == VpnState.RECONNECTING || state == VpnState.SCANNING) {
            Intent i = PhantomVpnService.disconnectIntent(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
            return;
        }
        btnConnect.setEnabled(false);
        VpnConnector.prepareConfig(this, null, new VpnConnector.PrepareCallback() {
            @Override public void onReady(VpnConfig config) {
                btnConnect.setEnabled(true);
                VpnSettings.applyTo(HomeActivity.this, config);
                requestConsentAndStart(config);
            }
            @Override public void onError(String message) {
                btnConnect.setEnabled(true);
                Toast.makeText(HomeActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestConsentAndStart(VpnConfig config) {
        pendingConfig = config;
        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, REQ_VPN_CONSENT);
        } else {
            startVpnService(config);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN_CONSENT) {
            if (resultCode == RESULT_OK && pendingConfig != null) {
                startVpnService(pendingConfig);
            } else {
                Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show();
            }
            pendingConfig = null;
        }
    }

    private void startVpnService(VpnConfig config) {
        try {
            Intent svc = PhantomVpnService.connectIntent(this, config);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
            else startService(svc);
        } catch (Exception e) {
            Toast.makeText(this, "Could not start VPN: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void renderState(VpnState state, String detail) {
        boolean connected = state == VpnState.CONNECTED;
        boolean busy = state == VpnState.CONNECTING || state == VpnState.SCANNING
                || state == VpnState.RECONNECTING || state == VpnState.WAITING_FOR_NETWORK;

        lockIcon.setText(connected ? "🔒" : "🔓");
        statusTitle.setText(connected ? "You are protected"
                : (busy ? "Connecting…" : "You are unprotected"));
        btnConnect.setText(connected ? "DISCONNECT"
                : (state == VpnState.DISCONNECTING ? "…" : "CONNECT"));

        if (connected) {
            ipPill.setText(detail != null && !detail.isEmpty() ? detail : "tunnel active");
            statsRow.setVisibility(View.VISIBLE);
            if (connectedAtMillis == 0) connectedAtMillis = System.currentTimeMillis();
            uiHandler.removeCallbacks(ticker);
            uiHandler.post(ticker);
        } else {
            ipPill.setText("— · —");
            statsRow.setVisibility(View.GONE);
            uiHandler.removeCallbacks(ticker);
            connectedAtMillis = 0;
            statTime.setText("--:--:--");
        }
    }
}
