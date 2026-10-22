package ai.omnirush.chameleon.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONException;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The VPN entry point: an Android {@link VpnService} that owns the
 * connection lifecycle, the {@link VpnState} state machine, the smart
 * protocol fallback, kill-switch enforcement and the foreground
 * notification.
 *
 * <p>The WireGuard protocol engine itself is pluggable via
 * {@link WgTunnelBackend} (bundled: {@link WireGuardGoBackend}). When the
 * backend {@link WgTunnelBackend#managesOwnInterface() manages its own
 * interface} (stock wireguard-android does), the service skips its own
 * {@code Builder.establish()} and {@link #buildTunnelInterface(VpnConfig)}
 * documents/applies the interface contract for backends that need an
 * externally created TUN fd.
 *
 * <p>Clean-room implementation. Concepts (state machine, smart fallback,
 * always-on kill switch, boot auto-connect, quick tile) were studied from
 * the public behavior of leading VPN clients; no third-party code is
 * copied here.
 */
public class PhantomVpnService extends VpnService {
    private static final String TAG = "PhantomVpn";

    public static final String ACTION_CONNECT = "ai.omnirush.chameleon.vpn.CONNECT";
    public static final String ACTION_DISCONNECT = "ai.omnirush.chameleon.vpn.DISCONNECT";
    public static final String EXTRA_CONFIG_JSON = "config_json";

    private static final String CHANNEL_ID = "phantom_vpn_channel";
    private static final int NOTIF_ID = 4201;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    /** 0.0.0.0/0 minus RFC-1918 private ranges (for LAN bypass). */
    private static final String[] PUBLIC_V4_ROUTES = {
            "0.0.0.0/5", "8.0.0.0/7", "11.0.0.0/8", "12.0.0.0/6", "16.0.0.0/4",
            "32.0.0.0/3", "64.0.0.0/2", "128.0.0.0/3", "160.0.0.0/5", "168.0.0.0/6",
            "172.0.0.0/12", "172.32.0.0/11", "172.64.0.0/10", "172.128.0.0/9",
            "173.0.0.0/8", "174.0.0.0/7", "176.0.0.0/4", "192.0.0.0/9",
            "192.128.0.0/11", "192.160.0.0/13", "192.169.0.0/16", "192.170.0.0/15",
            "192.172.0.0/14", "192.176.0.0/12", "192.192.0.0/10",
            "193.0.0.0/8", "194.0.0.0/7", "196.0.0.0/6", "200.0.0.0/5",
            "208.0.0.0/4", "224.0.0.0/3"
    };

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean connectRequested = new AtomicBoolean(false);

    private WgTunnelBackend backend;
    private SmartProtocolSelector selector;
    private CredentialProvider credentialProvider = new CredentialProvider.Noop();
    private ParcelFileDescriptor tunFd;
    private VpnConfig activeConfig;
    private ConnectivityManager.NetworkCallback networkCallback;
    private volatile boolean watchdogStop;

    // ------------------------------------------------------------ lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        selector = new SmartProtocolSelector();
        Log.i(TAG, "PhantomVpnService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        if (ACTION_CONNECT.equals(action)) {
            String json = intent.getStringExtra(EXTRA_CONFIG_JSON);
            if (json == null) {
                Log.w(TAG, "CONNECT without config");
                return START_STICKY;
            }
            try {
                final VpnConfig cfg = VpnConfig.fromJsonString(json);
                if (connectRequested.compareAndSet(false, true)) {
                    // Must foreground immediately: startForegroundService()
                    // requires startForeground() within ~5s, but smart-protocol
                    // probing can take longer.
                    startForegroundWithState();
                    updateNotification("Connecting…");
                    worker.execute(new Runnable() {
                        @Override public void run() { runConnectFlow(cfg, 0); }
                    });
                } else {
                    Log.i(TAG, "Connect already in progress / connected");
                }
            } catch (JSONException e) {
                Log.e(TAG, "Bad VpnConfig JSON", e);
                VpnStateStore.get().setState(VpnState.ERROR, "Invalid configuration");
            }
        } else if (ACTION_DISCONNECT.equals(action)) {
            worker.execute(new Runnable() {
                @Override public void run() { runUserDisconnect(); }
            });
        }
        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        Log.i(TAG, "VPN permission revoked by user/system");
        worker.execute(new Runnable() {
            @Override public void run() {
                tearDownTunnel();
                connectRequested.set(false);
                VpnStateStore.get().clearSession(PhantomVpnService.this);
                VpnStateStore.get().setState(VpnState.DISABLED, "Permission revoked");
                stopForeground(true);
                stopSelf();
            }
        });
    }

    @Override
    public void onDestroy() {
        watchdogStop = true;
        unregisterNetworkCallback();
        worker.shutdownNow();
        if (selector != null) selector.shutdown();
        tearDownTunnel();
        super.onDestroy();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    /** Allows the app (or tests) to inject a credential source. */
    public void setCredentialProvider(CredentialProvider provider) {
        this.credentialProvider = provider != null ? provider : new CredentialProvider.Noop();
    }

    // ---------------------------------------------------------- connect flow

    private void runConnectFlow(VpnConfig cfg, int attempt) {
        VpnStateStore store = VpnStateStore.get();
        try {
            if (!hasActiveNetwork()) {
                store.setState(VpnState.WAITING_FOR_NETWORK, "No network");
                waitForNetworkAndRetry(cfg);
                return;
            }

            store.setState(VpnState.CONNECTING, cfg.server.name);

            // Short-lived credential refresh (control-hub seam).
            if (cfg.credentialsNeedRefresh()) {
                store.setState(VpnState.CONNECTING, "Refreshing credentials");
                cfg = credentialProvider.refreshIfNeeded(cfg);
            }
            final VpnConfig config = cfg;

            ensureBackend();

            // Kill-switch arming is recorded; the OS-level always-on part is
            // user-guided via KillSwitchManager (see MainActivity wiring).
            new KillSwitchManager(this).setArmed(config.killSwitch.enabled);

            store.setState(VpnState.SCANNING, "Selecting protocol");
            SmartProtocolSelector.Candidate winner = selector.select(
                    config, backend.supportsTcpTlsTransports(),
                    new SmartProtocolSelector.Probe() {
                        @Override public boolean tryCandidate(SmartProtocolSelector.Candidate c) {
                            try {
                                backend.connect(config, c);
                                // Bound the handshake window; a dead peer is
                                // caught later by the watchdog.
                                long deadline = SystemClock.uptimeMillis() + 5000;
                                while (SystemClock.uptimeMillis() < deadline) {
                                    if (backend.isRunning()) return true;
                                    Thread.sleep(250);
                                }
                                backend.disconnect();
                                return false;
                            } catch (Exception e) {
                                Log.i(TAG, "Probe failed for " + c + ": " + e.getMessage());
                                try { backend.disconnect(); } catch (Exception ignored) { }
                                return false;
                            }
                        }
                    });

            if (winner == null) {
                throw new WgTunnelBackend.BackendException("No protocol candidate succeeded");
            }

            if (!backend.managesOwnInterface()) {
                tunFd = buildTunnelInterface(config);
                if (tunFd == null) {
                    throw new WgTunnelBackend.BackendException("Could not establish TUN interface");
                }
            }

            activeConfig = config;
            updateNotification("Connected — " + config.server.name);
            store.persistSession(this, config.toJsonString(), true);
            store.setState(VpnState.CONNECTED, winner.protocol.name());
            startWatchdog();
        } catch (Exception e) {
            Log.e(TAG, "Connect failed", e);
            tearDownTunnel();
            if (attempt < MAX_RECONNECT_ATTEMPTS && connectRequested.get()) {
                store.setState(VpnState.RECONNECTING, "Retry " + (attempt + 1));
                SystemClock.sleep((attempt + 1) * 2000L);
                runConnectFlow(cfg, attempt + 1);
            } else {
                connectRequested.set(false);
                store.setState(VpnState.ERROR, e.getMessage());
                stopForeground(true);
            }
        }
    }

    private void runUserDisconnect() {
        VpnStateStore.get().setState(VpnState.DISCONNECTING, "");
        tearDownTunnel();
        connectRequested.set(false);
        watchdogStop = true;
        VpnStateStore.get().clearSession(this);
        VpnStateStore.get().setState(VpnState.DISABLED, "");
        stopForeground(true);
        stopSelf();
    }

    private void tearDownTunnel() {
        watchdogStop = true;
        if (backend != null) {
            try { backend.disconnect(); } catch (Exception e) {
                Log.w(TAG, "Backend disconnect failed", e);
            }
        }
        if (tunFd != null) {
            try { tunFd.close(); } catch (Exception e) {
                Log.w(TAG, "TUN close failed", e);
            }
            tunFd = null;
        }
        activeConfig = null;
    }

    private void ensureBackend() throws WgTunnelBackend.BackendException {
        if (backend == null) {
            backend = new WireGuardGoBackend(this);
            Log.i(TAG, "Backend: " + backend.getVersion());
        }
    }

    // --------------------------------------- TUN interface (native-builder path)

    /**
     * Builds the TUN interface from {@link VpnConfig} for backends that do
     * NOT manage their own interface. Applies: addresses, routes (with
     * IPv6 capture for BLOCK/ROUTE and private-range exclusion for LAN
     * bypass), DNS (NetShield resolvers), {@code setBlocking(true)} for the
     * kill switch, and per-app allow/disallow lists for split tunneling.
     */
    private ParcelFileDescriptor buildTunnelInterface(VpnConfig cfg) {
        try {
            Builder b = new Builder();
            b.setSession("Phantom GPS");
            b.setMtu(cfg.mtu);

            for (String addr : cfg.credentials.address.split(",")) {
                String[] parts = addr.trim().split("/");
                if (parts.length == 2) {
                    b.addAddress(parts[0], Integer.parseInt(parts[1]));
                }
            }

            // Routes: full tunnel, minus RFC-1918 when LAN bypass is on.
            String[] v4Routes = cfg.lanBypass ? PUBLIC_V4_ROUTES : new String[]{"0.0.0.0/0"};
            for (String route : v4Routes) {
                String[] parts = route.split("/");
                b.addRoute(parts[0], Integer.parseInt(parts[1]));
            }
            // IPv6: capture ::/0 into the tunnel for BLOCK/ROUTE so nothing
            // leaks around it (the backend drops what it must not forward).
            if (cfg.ipv6Mode != VpnConfig.Ipv6Mode.OFF) {
                b.addRoute("::", 0);
            }

            for (String dns : cfg.effectiveDnsServers()) {
                b.addDnsServer(dns);
            }

            // Kill switch: while OUR interface is up, nothing bypasses it.
            if (cfg.killSwitch.enabled) {
                try {
                    b.setBlocking(true);
                } catch (Exception e) {
                    Log.w(TAG, "setBlocking not supported", e);
                }
            }

            // Per-app split tunneling.
            VpnConfig.SplitTunnelConfig st = cfg.splitTunnel;
            if (st.mode == VpnConfig.SplitTunnelMode.INCLUDE_ONLY) {
                for (String pkg : st.apps) {
                    try { b.addAllowedApplication(pkg); }
                    catch (PackageManager.NameNotFoundException e) {
                        Log.w(TAG, "Split-tunnel app not installed: " + pkg);
                    }
                }
                // Per-IP include: route only those CIDRs into the tunnel.
                // (Applied by replacing v4Routes above in a future revision;
                // currently documented via the backend's AllowedIPs.)
            } else if (st.mode == VpnConfig.SplitTunnelMode.EXCLUDE) {
                for (String pkg : st.apps) {
                    try { b.addDisallowedApplication(pkg); }
                    catch (PackageManager.NameNotFoundException e) {
                        Log.w(TAG, "Split-tunnel app not installed: " + pkg);
                    }
                }
            }

            // Keep the tunnel off the underlying networks list so the system
            // does not route tunnel traffic back into itself.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                b.setUnderlyingNetworks(null);
            }

            ParcelFileDescriptor fd = b.establish();
            Log.i(TAG, "TUN interface established");
            return fd;
        } catch (Exception e) {
            Log.e(TAG, "Builder.establish failed", e);
            return null;
        }
    }

    // ------------------------------------------------------- watchdog / net

    private void startWatchdog() {
        watchdogStop = false;
        final VpnConfig cfg = activeConfig;
        new Thread(new Runnable() {
            @Override public void run() {
                while (!watchdogStop) {
                    SystemClock.sleep(5000);
                    if (watchdogStop) return;
                    boolean alive = backend != null && backend.isRunning();
                    if (!alive && connectRequested.get()
                            && VpnStateStore.get().getState() == VpnState.CONNECTED) {
                        Log.i(TAG, "Watchdog: tunnel lost, reconnecting");
                        VpnStateStore.get().setState(VpnState.RECONNECTING, "Tunnel dropped");
                        runConnectFlow(cfg, 0);
                        return;
                    }
                    if (alive && VpnStateStore.get().getState() == VpnState.CONNECTED) {
                        WgTunnelBackend.Stats s = backend.getStats();
                        updateNotification("Connected — " + cfg.server.name
                                + "  ↓" + formatBytes(s.rxBytes)
                                + " ↑" + formatBytes(s.txBytes));
                    }
                }
            }
        }, "PhantomVpn-watchdog").start();
    }

    private boolean hasActiveNetwork() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm != null && cm.getActiveNetwork() != null;
        } catch (Exception e) {
            return true; // fail open; the connect attempt will surface errors
        }
    }

    private void waitForNetworkAndRetry(final VpnConfig cfg) {
        unregisterNetworkCallback();
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                VpnStateStore.get().setState(VpnState.ERROR, "No network available");
                connectRequested.set(false);
                return;
            }
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    Log.i(TAG, "Network available, retrying connect");
                    unregisterNetworkCallback();
                    if (connectRequested.get()) {
                        worker.execute(new Runnable() {
                            @Override public void run() { runConnectFlow(cfg, 0); }
                        });
                    }
                }
            };
            cm.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            Log.w(TAG, "Network callback registration failed", e);
            VpnStateStore.get().setState(VpnState.ERROR, "No network available");
            connectRequested.set(false);
        }
    }

    private void unregisterNetworkCallback() {
        if (networkCallback != null) {
            try {
                ConnectivityManager cm =
                        (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) cm.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                Log.w(TAG, "Unregister network callback failed", e);
            }
            networkCallback = null;
        }
    }

    // ---------------------------------------------------------- notification

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Phantom VPN", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Phantom GPS VPN tunnel status");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent disconnect = new Intent(this, VpnActionReceiver.class)
                .setAction(VpnActionReceiver.ACTION_DISCONNECT);
        PendingIntent pi = PendingIntent.getBroadcast(
                this, 0, disconnect,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder nb = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Phantom GPS VPN")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        null, "Disconnect", pi).build());
        return nb.build();
    }

    private void startForegroundWithState() {
        Notification n = buildNotification("Connecting…");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void updateNotification(final String text) {
        mainHandler.post(new Runnable() {
            @Override public void run() {
                try {
                    NotificationManager nm =
                            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
                } catch (Exception e) {
                    Log.w(TAG, "Notification update failed", e);
                }
            }
        });
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + "K";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + "M";
        return (bytes / (1024 * 1024 * 1024)) + "G";
    }

    // ------------------------------------------------------------------ api

    /** Convenience: build the CONNECT intent for a config. */
    public static Intent connectIntent(Context ctx, VpnConfig cfg) throws JSONException {
        return new Intent(ctx, PhantomVpnService.class)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_CONFIG_JSON, cfg.toJsonString());
    }

    public static Intent disconnectIntent(Context ctx) {
        return new Intent(ctx, PhantomVpnService.class).setAction(ACTION_DISCONNECT);
    }
}
