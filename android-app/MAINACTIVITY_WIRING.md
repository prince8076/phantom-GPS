# MainActivity wiring — Phantom VPN core

## 0. Recommended: use VpnConnector (new)

`VpnConnector.prepareConfig(context, hubBaseUrl, callback)` does the whole
fetch → resolve → credentials flow on a background thread and returns a
complete `VpnConfig` on the UI thread:

```java
import ai.omnirush.chameleon.vpn.VpnConnector;
import ai.omnirush.chameleon.vpn.VpnConfig;

VpnConnector.prepareConfig(this, "http://192.168.1.5:3300",
    new VpnConnector.PrepareCallback() {
        @Override public void onReady(VpnConfig config) {
            requestVpnConsentAndConnect(config); // section 1 below
        }
        @Override public void onError(String message) {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        }
    });
```

Pass `null` as the hub URL to reuse the last-paired hub from `HubSettings`
(set it during QR pairing via `HubSettings.setHubUrl(this, url)`).
The pieces behind it: `HubClient` (hub HTTP + `CredentialProvider`),
`HubSettings` (persisted hub URL). The manual steps below remain valid if you
prefer to wire the flow yourself.

Concise integration notes. The 21 KB `MainActivity.java` is **not** rewritten;
add the pieces below where indicated. All names refer to the new
`ai.omnirush.chameleon.vpn` package in this draft.

## 1. VPN consent (required once per install)

`VpnService.prepare()` returns an `Intent` when user consent is needed,
`null` when already granted. Wire it with an `ActivityResultLauncher`:

```java
import android.net.VpnService;
import androidx.activity.result.ActivityResultLauncher; // or use startActivityForResult on API < 30 style
import ai.omnirush.chameleon.vpn.PhantomVpnService;
import ai.omnirush.chameleon.vpn.VpnConfig;
import ai.omnirush.chameleon.vpn.VpnState;
import ai.omnirush.chameleon.vpn.VpnStateListener;
import ai.omnirush.chameleon.vpn.VpnStateStore;
import ai.omnirush.chameleon.vpn.KillSwitchManager;

private VpnConfig pendingVpnConfig; // set before requesting consent

private void requestVpnConsentAndConnect(VpnConfig config) {
    pendingVpnConfig = config;
    Intent prepare = VpnService.prepare(this);
    if (prepare != null) {
        // Launches the system "Connection request" dialog.
        vpnConsentLauncher.launch(prepare);
    } else {
        startVpnService(config);
    }
}

// with Activity Result API:
vpnConsentLauncher = registerForActivityResult(
    new ActivityResultContracts.StartActivityForResult(),
    result -> {
        if (result.getResultCode() == Activity.RESULT_OK && pendingVpnConfig != null) {
            // Optional: one-time kill-switch guidance before first connect.
            KillSwitchManager ks = new KillSwitchManager(this);
            if (pendingVpnConfig.killSwitch.enabled && !ks.wasGuidanceShown()) {
                ks.showSetupGuidance(this, () -> startVpnService(pendingVpnConfig));
            } else {
                startVpnService(pendingVpnConfig);
            }
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show();
        }
    });

private void startVpnService(VpnConfig config) {
    try {
        Intent svc = PhantomVpnService.connectIntent(this, config);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    } catch (Exception e) {
        Toast.makeText(this, "Could not start VPN: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
}
```

> Note: the existing `MainActivity extends Activity` (not `AppCompatActivity`).
> The Activity Result API needs `androidx.activity:activity`. If you prefer
> zero new dependencies, use the classic
> `startActivityForResult(prepare, REQ_VPN)` + `onActivityResult()` instead —
> same behavior, no AndroidX.

## 2. Building the VpnConfig

The dashboard pushes a config JSON over the existing QR / cloud-tunnel sync.
Parse it where the sync payload arrives (e.g. next to the existing
`StatusPollTask` handling):

```java
import org.json.JSONObject;
import ai.omnirush.chameleon.vpn.VpnConfig;

JSONObject payload = new JSONObject(syncBody); // from control hub
VpnConfig vpnConfig = VpnConfig.fromJson(payload.getJSONObject("vpn"));
// or: VpnConfig.fromJsonString(payload.getJSONObject("vpn").toString());
```

Minimal hand-built example (for a "Connect" button before sync exists):

```java
VpnConfig cfg = new VpnConfig();
cfg.server.id = "zurich";
cfg.server.name = "Zurich, Switzerland";
cfg.server.endpoint = "203.0.113.10";      // from control hub
cfg.server.port = 51820;
cfg.server.publicKey = "<base64>";          // from control hub
cfg.credentials.privateKey = "<base64>";   // from control hub
cfg.credentials.address = "10.8.0.2/32";   // from control hub
cfg.protocol = VpnConfig.Protocol.SMART;
cfg.netShieldLevel = VpnConfig.NetShieldLevel.ADS_MALWARE;
cfg.killSwitch.enabled = true;
cfg.lanBypass = true;
cfg.ipv6Mode = VpnConfig.Ipv6Mode.BLOCK;
requestVpnConsentAndConnect(cfg);
```

## 3. Observing state (status line / toggle button)

```java
private final VpnStateListener vpnListener = new VpnStateListener() {
    @Override public void onVpnStateChanged(final VpnState s, final String detail) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                tvStatus.setText("VPN: " + s + (detail.isEmpty() ? "" : " (" + detail + ")"));
                btnToggleVpn.setText(s == VpnState.CONNECTED ? "Disconnect VPN" : "Connect VPN");
            }
        });
    }
};

@Override protected void onResume() {
    super.onResume();
    VpnStateStore.get().addListener(vpnListener);
    vpnListener.onVpnStateChanged(VpnStateStore.get().getState(),
                                 VpnStateStore.get().getDetail());
}

@Override protected void onPause() {
    VpnStateStore.get().removeListener(vpnListener);
    super.onPause();
}
```

Disconnect button:

```java
startService(PhantomVpnService.disconnectIntent(this)); // API 26+: use startForegroundService on O+
```

## 4. Settings to expose (dashboard mirrors these)

- Auto-connect on boot: `BootReceiver.setAutoConnectEnabled(this, enabled)`
- Kill switch armed: flows through `VpnConfig.killSwitch.enabled`
- The per-app split-tunnel picker needs `QUERY_ALL_PACKAGES` (already in the
  new manifest) to list installed apps via `PackageManager`.

## 5. What NOT to do

- Do not call `VpnService.prepare()` from the tile or the boot receiver —
  consent UI must come from an Activity.
- Do not copy any ProtonVPN/WireGuard project source into the app; the
  wireguard-android engine arrives as a Gradle dependency (see BUILD_NOTES.md).
