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
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proton-patterned VPN connection screen: status header, hero connect
 * button, session stats, and the hub server list with load + feature badges.
 *
 * <p>Flow: tap CONNECT → {@link VpnConnector#prepareConfig} builds a complete
 * {@link VpnConfig} from the control hub (background thread) → system VPN
 * consent dialog if needed → {@link PhantomVpnService} starts. State changes
 * arrive via {@link VpnStateStore}.
 *
 * <p>Plain-framework Activity (no AndroidX) so it compiles with build.ps1.
 * Clean-room implementation.
 */
public class VpnConnectActivity extends Activity {

    private static final int REQ_VPN_CONSENT = 1001;

    private TextView statusPill;
    private TextView statusServer;
    private TextView statusDetail;
    private Button btnConnect;
    private TextView statTime;
    private TextView statDown;
    private TextView statUp;
    private TextView serverHint;
    private ListView serverList;
    private Switch switchNetshield;
    private Switch switchKillswitch;

    private ServerListAdapter adapter;
    private VpnConfig pendingConfig;
    private String selectedServerId = "";
    private boolean netshieldOn = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private long connectedAtMillis = 0;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateSessionTime();
            uiHandler.postDelayed(this, 1000);
        }
    };

    private final VpnStateListener stateListener = new VpnStateListener() {
        @Override public void onVpnStateChanged(final VpnState s, final String detail) {
            runOnUiThread(new Runnable() {
                @Override public void run() { renderState(s, detail); }
            });
        }
    };

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vpn_connect);

        statusPill = findViewById(R.id.status_pill);
        statusServer = findViewById(R.id.status_server);
        statusDetail = findViewById(R.id.status_detail);
        btnConnect = findViewById(R.id.btn_connect);
        statTime = findViewById(R.id.stat_time);
        statDown = findViewById(R.id.stat_down);
        statUp = findViewById(R.id.stat_up);
        serverHint = findViewById(R.id.server_hint);
        serverList = findViewById(R.id.server_list);
        switchNetshield = findViewById(R.id.switch_netshield);
        switchKillswitch = findViewById(R.id.switch_killswitch);

        adapter = new ServerListAdapter(this);
        serverList.setAdapter(adapter);
        serverList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view,
                                              int position, long id) {
                ServerListAdapter.Row row = adapter.getRow(position);
                selectedServerId = row.server.id;
                adapter.setSelectedId(selectedServerId);
                statusServer.setText(row.server.name.isEmpty()
                        ? row.server.id : row.server.name);
                Toast.makeText(VpnConnectActivity.this,
                        "Selected " + row.server.name, Toast.LENGTH_SHORT).show();
            }
        });

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onConnectTapped(); }
        });

        findViewById(R.id.btn_refresh_servers).setOnClickListener(
                new View.OnClickListener() {
                    @Override public void onClick(View v) { loadServers(); }
                });

        switchNetshield.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    netshieldOn = isChecked;
                    Toast.makeText(VpnConnectActivity.this,
                            isChecked ? "NetShield will apply on next connect"
                                      : "NetShield off for next connect",
                            Toast.LENGTH_SHORT).show();
                });

        renderState(VpnStateStore.get().getState(), VpnStateStore.get().getDetail());
        loadServers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        VpnStateStore.get().addListener(stateListener);
        renderState(VpnStateStore.get().getState(), VpnStateStore.get().getDetail());
    }

    @Override
    protected void onPause() {
        VpnStateStore.get().removeListener(stateListener);
        uiHandler.removeCallbacks(ticker);
        super.onPause();
    }

    // ------------------------------------------------------------ connect flow

    private void onConnectTapped() {
        VpnState state = VpnStateStore.get().getState();
        if (state == VpnState.CONNECTED || state == VpnState.CONNECTING
                || state == VpnState.RECONNECTING || state == VpnState.SCANNING) {
            // Disconnect.
            Intent i = PhantomVpnService.disconnectIntent(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
            return;
        }
        btnConnect.setEnabled(false);
        statusDetail.setText("Preparing configuration…");
        VpnConnector.prepareConfig(this, null, new VpnConnector.PrepareCallback() {
            @Override public void onReady(VpnConfig config) {
                btnConnect.setEnabled(true);
                // Apply this screen's quick toggles.
                config.netShieldLevel = netshieldOn
                        ? VpnConfig.NetShieldLevel.ADS_MALWARE
                        : VpnConfig.NetShieldLevel.OFF;
                config.killSwitch.enabled = switchKillswitch.isChecked();
                if (!selectedServerId.isEmpty()) {
                    config.server.id = selectedServerId;
                }
                requestConsentAndStart(config);
            }
            @Override public void onError(String message) {
                btnConnect.setEnabled(true);
                Toast.makeText(VpnConnectActivity.this, message,
                        Toast.LENGTH_LONG).show();
                renderState(VpnStateStore.get().getState(),
                        VpnStateStore.get().getDetail());
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
                Toast.makeText(this, "VPN permission denied",
                        Toast.LENGTH_SHORT).show();
            }
            pendingConfig = null;
        }
    }

    private void startVpnService(VpnConfig config) {
        try {
            Intent svc = PhantomVpnService.connectIntent(this, config);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not start VPN: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ server list

    private void loadServers() {
        serverHint.setText("Loading servers from the control hub…");
        final String hubUrl = HubSettings.getHubUrl(this);
        if (hubUrl.isEmpty()) {
            serverHint.setText("Pair with the control hub first (dashboard → Phone sync → QR).");
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    HubClient hub = new HubClient(hubUrl);
                    final List<HubClient.HubServer> servers = hub.fetchServers();
                    // Feature flags come from the server contract; seed the
                    // well-known ones locally until the hub serves them.
                    final Map<String, List<String>> feats = seedFeatures(servers);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            adapter.setServers(servers, feats);
                            adapter.setSelectedId(selectedServerId);
                            serverHint.setText(servers.isEmpty()
                                    ? "No servers returned by the hub."
                                    : servers.size() + " servers · tap one to select it");
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            serverHint.setText("Could not reach hub: " + e.getMessage());
                        }
                    });
                }
            }
        }, "vpn-servers").start();
    }

    /** Local seed of feature flags until GET /api/servers serves them. */
    private static Map<String, List<String>> seedFeatures(
            List<HubClient.HubServer> servers) {
        Map<String, List<String>> out = new HashMap<String, List<String>>();
        addFeat(out, "zurich", ServerListAdapter.FEAT_SECURE_CORE);
        addFeat(out, "zurich", ServerListAdapter.FEAT_P2P);
        addFeat(out, "zurich", ServerListAdapter.FEAT_TOR);
        addFeat(out, "reykjavik", ServerListAdapter.FEAT_SECURE_CORE);
        addFeat(out, "reykjavik", ServerListAdapter.FEAT_P2P);
        addFeat(out, "reykjavik", ServerListAdapter.FEAT_TOR);
        addFeat(out, "stockholm", ServerListAdapter.FEAT_SECURE_CORE);
        addFeat(out, "stockholm", ServerListAdapter.FEAT_TOR);
        String[] streaming = {"berlin", "london", "paris", "amsterdam", "newyork",
                "losangeles", "toronto", "singapore", "tokyo", "sydney", "seoul",
                "mumbai", "dublin", "frankfurt"};
        for (String id : streaming) addFeat(out, id, ServerListAdapter.FEAT_STREAMING);
        String[] p2p = {"berlin", "amsterdam", "newyork", "losangeles", "toronto",
                "singapore", "frankfurt", "vienna", "prague", "bucharest", "helsinki"};
        for (String id : p2p) addFeat(out, id, ServerListAdapter.FEAT_P2P);
        return out;
    }

    private static void addFeat(Map<String, List<String>> map, String id, String feat) {
        List<String> l = map.get(id);
        if (l == null) { l = new java.util.ArrayList<String>(); map.put(id, l); }
        if (!l.contains(feat)) l.add(feat);
    }

    // ------------------------------------------------------------ rendering

    private void renderState(VpnState state, String detail) {
        boolean connected = state == VpnState.CONNECTED;
        boolean busy = state == VpnState.CONNECTING || state == VpnState.SCANNING
                || state == VpnState.RECONNECTING || state == VpnState.WAITING_FOR_NETWORK;

        btnConnect.setSelected(connected);
        btnConnect.setText(connected ? "DISCONNECT"
                : (state == VpnState.DISCONNECTING ? "…" : "CONNECT"));

        int pillColor;
        String pillText;
        switch (state) {
            case CONNECTED:
                pillText = "● Connected"; pillColor = 0xFF5CE1B1; break;
            case CONNECTING:
            case SCANNING:
            case RECONNECTING:
            case WAITING_FOR_NETWORK:
                pillText = "● Connecting…"; pillColor = 0xFFF0B45C; break;
            case DISCONNECTING:
                pillText = "● Disconnecting…"; pillColor = 0xFFF0B45C; break;
            case ERROR:
                pillText = "● Error"; pillColor = 0xFFE0655C; break;
            case DISABLED:
            default:
                pillText = "● Disconnected"; pillColor = 0xFF8BA5A4; break;
        }
        statusPill.setText(pillText);
        statusPill.setTextColor(pillColor);

        if (detail != null && !detail.isEmpty()) {
            statusDetail.setText(detail);
        } else if (connected) {
            statusDetail.setText("Your traffic is protected");
        } else if (!busy) {
            statusDetail.setText("Your traffic is not protected");
        }

        if (connected) {
            if (connectedAtMillis == 0) connectedAtMillis = System.currentTimeMillis();
            uiHandler.removeCallbacks(ticker);
            uiHandler.post(ticker);
        } else {
            uiHandler.removeCallbacks(ticker);
            connectedAtMillis = 0;
            statTime.setText("--:--:--");
            statDown.setText("0 B");
            statUp.setText("0 B");
        }
    }

    private void updateSessionTime() {
        if (connectedAtMillis == 0) return;
        long secs = (System.currentTimeMillis() - connectedAtMillis) / 1000;
        statTime.setText(String.format("%02d:%02d:%02d",
                secs / 3600, (secs % 3600) / 60, secs % 60));
        // Data counters come from the tunnel backend; PhantomVpnService exposes
        // them via VpnStateStore detail in a future pass — placeholder for now.
    }
}
