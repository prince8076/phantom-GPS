package ai.omnirush.chameleon.vpn;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

/**
 * One-call "connect me" orchestrator for {@code MainActivity}.
 *
 * <p>Flow (all network I/O on a background thread):
 * <ol>
 *   <li>Fetch the dashboard-pushed config from the hub
 *       ({@code GET /api/vpn/config}); if none was ever pushed, build a
 *       default config (fastest server, Smart protocol).</li>
 *   <li>Resolve the server id into a concrete WireGuard endpoint + public key
 *       via {@code GET /api/servers}.</li>
 *   <li>Ensure fresh short-lived credentials via
 *       {@code POST /api/vpn/credentials}.</li>
 *   <li>Hand the complete {@link VpnConfig} back on the UI thread.</li>
 * </ol>
 *
 * <p>The caller (MainActivity) then runs the {@code VpnService.prepare()}
 * consent flow and starts {@link PhantomVpnService} — see
 * {@code MAINACTIVITY_WIRING.md}. Consent UI must come from an Activity, so
 * this class deliberately does not start the service itself.
 *
 * <p>Clean-room implementation; no Proton code involved.
 */
public final class VpnConnector {

    /** Result of {@link #prepareConfig}. */
    public interface PrepareCallback {
        /** Called on the UI thread with a complete, ready-to-connect config. */
        void onReady(VpnConfig config);
        /** Called on the UI thread when preparation failed. */
        void onError(String message);
    }

    private VpnConnector() { /* static only */ }

    /**
     * Builds a complete {@link VpnConfig} from the hub.
     *
     * @param context  any context (used for the persisted hub URL fallback)
     * @param hubBaseUrl e.g. "http://192.168.1.5:3300". When null/empty, the
     *                 last-used URL from {@link HubSettings} is used.
     * @param callback invoked on the UI thread.
     */
    public static void prepareConfig(
            final Context context, final String hubBaseUrl,
            final PrepareCallback callback) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    VpnConfig cfg = buildConfig(context, hubBaseUrl);
                    final VpnConfig ready = cfg;
                    ui.post(new Runnable() {
                        @Override public void run() { callback.onReady(ready); }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            callback.onError(describe(e));
                        }
                    });
                }
            }
        }, "vpn-prepare").start();
    }

    // ------------------------------------------------------------- internals

    private static VpnConfig buildConfig(Context context, String hubBaseUrl)
            throws Exception {
        String url = hubBaseUrl == null || hubBaseUrl.trim().isEmpty()
                ? HubSettings.getHubUrl(context)
                : hubBaseUrl.trim();
        if (url.isEmpty()) {
            throw new IllegalStateException(
                    "no control-hub URL set — pair the phone from the dashboard first");
        }
        HubSettings.setHubUrl(context, url); // remember last good hub
        HubClient hub = new HubClient(url);

        // 1. Dashboard-pushed config, or a sensible default.
        VpnConfig cfg = hub.fetchPushedConfig();
        if (cfg == null) {
            cfg = new VpnConfig();
            cfg.protocol = VpnConfig.Protocol.SMART;
            cfg.killSwitch.enabled = true;
            cfg.lanBypass = true;
            cfg.ipv6Mode = VpnConfig.Ipv6Mode.BLOCK;
        }

        // 2. Concrete endpoint + public key for the chosen server/protocol.
        List<HubClient.HubServer> servers = hub.fetchServers();
        hub.resolveServer(cfg, servers);

        // 3. Fresh credentials (no-op when still valid).
        hub.refreshIfNeeded(cfg);

        if (cfg.server.endpoint.isEmpty() || cfg.server.publicKey.isEmpty()) {
            throw new IllegalStateException("server endpoint not resolved");
        }
        if (cfg.credentials.privateKey.isEmpty() || cfg.credentials.address.isEmpty()) {
            throw new IllegalStateException(
                    "no tunnel credentials — the hub must issue them "
                    + "(see SERVER_CONTRACT.md)");
        }

        // 4. NetShield resolvers when a filter level is active and no custom
        //    DNS overrides it; failure falls back to the static list.
        if (cfg.customDns.isEmpty()
                && cfg.netShieldLevel != VpnConfig.NetShieldLevel.OFF) {
            List<String> resolvers = hub.fetchNetShieldResolvers(cfg.netShieldLevel);
            cfg.customDns.addAll(resolvers);
        }
        return cfg;
    }

    private static String describe(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) m = e.getClass().getSimpleName();
        if (m.contains("failed to connect") || m.contains("ConnectException")
                || m.contains("UnknownHostException")) {
            return "Cannot reach the control hub — is it running and on the same network?";
        }
        return m;
    }
}
