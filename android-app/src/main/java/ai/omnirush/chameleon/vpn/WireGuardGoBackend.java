package ai.omnirush.chameleon.vpn;

import android.content.Context;
import android.util.Log;

import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Statistics;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import com.wireguard.config.InetEndpoint;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;
import com.wireguard.crypto.Key;
import com.wireguard.crypto.KeyPair;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link WgTunnelBackend} adapter for the open-source wireguard-android
 * tunnel engine ({@code com.wireguard.android:tunnel} Gradle artifact).
 *
 * <p>Written against the library's <b>public</b> API only
 * ({@code GoBackend}, {@code Backend}, {@code Tunnel},
 * {@code com.wireguard.config.*}, {@code com.wireguard.crypto.*}).
 * No wireguard-android sources are vendored or copied.
 *
 * <p>Notes on scope (see BUILD_NOTES.md for details):
 * <ul>
 *   <li>Stock {@code GoBackend} creates and owns the TUN interface via its
 *       own manifest-declared inner {@code VpnService}, so
 *       {@link #managesOwnInterface()} returns true and
 *       {@link PhantomVpnService} skips its own {@code Builder.establish()}.
 *   <li>Stock wireguard-go speaks WireGuard-over-UDP only, so
 *       {@link #supportsTcpTlsTransports()} returns false and the smart
 *       selector skips TCP/TLS candidates unless a TCP-encapsulation shim
 *       is added later.
 *   <li>Per-app allow/disallow split tunneling is an Android
 *       {@code VpnService.Builder} feature and cannot be applied through
 *       the backend-owned interface; it becomes available on the native
 *       builder path (future {@code NativeBuilderBackend}).
 * </ul>
 */
public class WireGuardGoBackend implements WgTunnelBackend {
    private static final String TAG = "PhantomVpn";
    private static final String TUNNEL_NAME = "phantom";
    private static final int KEEPALIVE_SECONDS = 25;

    /**
     * 0.0.0.0/0 minus RFC-1918 private ranges — used for LAN bypass so
     * local traffic never enters the tunnel (VpnService.Builder cannot
     * express exclusions, but WireGuard AllowedIPs can).
     */
    private static final String[] PUBLIC_V4_ALLOWED_IPS = {
            "0.0.0.0/5", "8.0.0.0/7", "11.0.0.0/8", "12.0.0.0/6", "16.0.0.0/4",
            "32.0.0.0/3", "64.0.0.0/2", "128.0.0.0/3", "160.0.0.0/5", "168.0.0.0/6",
            "172.0.0.0/12", "172.32.0.0/11", "172.64.0.0/10", "172.128.0.0/9",
            "173.0.0.0/8", "174.0.0.0/7", "176.0.0.0/4", "192.0.0.0/9",
            "192.128.0.0/11", "192.160.0.0/13", "192.169.0.0/16", "192.170.0.0/15",
            "192.172.0.0/14", "192.176.0.0/12", "192.192.0.0/10",
            "193.0.0.0/8", "194.0.0.0/7", "196.0.0.0/6", "200.0.0.0/5",
            "208.0.0.0/4", "224.0.0.0/3"
    };

    private final Context appContext;
    private final Backend backend;
    private final PhantomTunnel tunnel = new PhantomTunnel();

    private volatile VpnConfig activeConfig;
    private volatile SmartProtocolSelector.Candidate activeCandidate;

    public WireGuardGoBackend(Context context) throws BackendException {
        this.appContext = context.getApplicationContext();
        try {
            this.backend = new GoBackend(appContext);
        } catch (Exception e) {
            throw new BackendException("Could not create GoBackend", e);
        }
    }

    @Override public boolean managesOwnInterface() {
        return true;
    }

    @Override public boolean supportsTcpTlsTransports() {
        return false; // stock wireguard-go: UDP only
    }

    @Override
    public synchronized void connect(VpnConfig config,
                                     SmartProtocolSelector.Candidate candidate)
            throws BackendException {
        disconnectQuietly();
        Config wgConfig = buildWireGuardConfig(config, candidate);
        try {
            backend.setState(tunnel, Backend.State.UP, wgConfig);
        } catch (Exception e) {
            throw new BackendException("GoBackend failed to bring tunnel up", e);
        }
        activeConfig = config;
        activeCandidate = candidate;
        Log.i(TAG, "WireGuard tunnel requested UP via " + candidate);
    }

    @Override
    public synchronized void disconnect() {
        disconnectQuietly();
        activeConfig = null;
        activeCandidate = null;
    }

    private void disconnectQuietly() {
        try {
            backend.setState(tunnel, Backend.State.DOWN, null);
        } catch (Exception e) {
            Log.w(TAG, "GoBackend setState(DOWN) failed (ignoring)", e);
        }
    }

    @Override
    public boolean isRunning() {
        try {
            return backend.getState(tunnel) == Backend.State.UP;
        } catch (Exception e) {
            Log.w(TAG, "GoBackend getState failed", e);
            return false;
        }
    }

    @Override
    public Stats getStats() {
        try {
            Statistics s = backend.getStatistics(tunnel);
            return new Stats(s.totalRx(), s.totalTx());
        } catch (Exception e) {
            return new Stats(0, 0);
        }
    }

    @Override
    public String getVersion() {
        try {
            return "wireguard-go via GoBackend " + backend.getVersion();
        } catch (Exception e) {
            return "wireguard-go (version unknown)";
        }
    }

    // ------------------------------------------------------- config building

    private Config buildWireGuardConfig(VpnConfig cfg,
                                        SmartProtocolSelector.Candidate candidate)
            throws BackendException {
        try {
            Key privateKey = Key.fromBase64(cfg.credentials.privateKey);
            KeyPair keyPair = new KeyPair(privateKey);
            Key publicKey = Key.fromBase64(cfg.server.publicKey);

            Interface.Builder ifBuilder = new Interface.Builder()
                    .setKeyPair(keyPair)
                    .setMtu(cfg.mtu);

            // Tunnel addresses, e.g. "10.8.0.2/32".
            for (String addr : splitCsv(cfg.credentials.address)) {
                ifBuilder.addAddress(InetNetwork.parse(addr).getAddress());
            }
            // DNS through the tunnel (NetShield filtering resolvers when enabled).
            for (String dns : cfg.effectiveDnsServers()) {
                ifBuilder.addDnsServer(InetAddress.getByName(dns));
            }

            Peer.Builder peerBuilder = new Peer.Builder()
                    .setPublicKey(publicKey)
                    .setEndpoint(InetEndpoint.parse(candidate.endpoint + ":" + candidate.port))
                    .setPersistentKeepalive(KEEPALIVE_SECONDS);

            // Allowed IPs: full tunnel by default; IPv6 captured when BLOCK/ROUTE
            // so nothing leaks outside the tunnel. With LAN bypass on, the
            // RFC-1918 ranges are subtracted so local traffic stays local.
            List<String> allowed = new ArrayList<String>();
            VpnConfig.SplitTunnelConfig st = cfg.splitTunnel;
            if (st.mode == VpnConfig.SplitTunnelMode.INCLUDE_ONLY && !st.ips.isEmpty()) {
                allowed.addAll(st.ips); // only these CIDRs traverse the tunnel
            } else if (cfg.lanBypass) {
                for (String cidr : PUBLIC_V4_ALLOWED_IPS) allowed.add(cidr);
                if (cfg.ipv6Mode != VpnConfig.Ipv6Mode.OFF) {
                    allowed.add("::/0");
                }
            } else {
                allowed.add("0.0.0.0/0");
                if (cfg.ipv6Mode != VpnConfig.Ipv6Mode.OFF) {
                    allowed.add("::/0");
                }
            }
            for (String cidr : allowed) {
                peerBuilder.addAllowedIp(InetNetwork.parse(cidr));
            }

            return new Config.Builder()
                    .setInterface(ifBuilder.build())
                    .addPeer(peerBuilder.build())
                    .build();
        } catch (BackendException be) {
            throw be;
        } catch (Exception e) {
            throw new BackendException("Invalid WireGuard config: " + e.getMessage(), e);
        }
    }

    private static List<String> splitCsv(String s) {
        List<String> out = new ArrayList<String>();
        if (s == null) return out;
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    // ------------------------------------------------------------- Tunnel impl

    private static class PhantomTunnel implements Tunnel {
        @Override public String getName() {
            return TUNNEL_NAME;
        }

        @Override public void onStateChange(State newState) {
            Log.i(TAG, "GoBackend tunnel state -> " + newState);
            // PhantomVpnService polls isRunning(); this is informational.
        }
    }
}
