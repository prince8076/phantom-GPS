package ai.omnirush.chameleon.vpn;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plain-old-data description of one VPN connection, mirroring the JSON
 * contract produced by the Phantom GPS web dashboard (control plane) and
 * delivered to the phone over the existing QR / cloud-tunnel sync.
 *
 * <p>Clean-room implementation. Field names intentionally match the
 * dashboard's config contract so both sides can share one schema.
 * See BUILD_NOTES.md for the full JSON contract.
 */
public class VpnConfig {

    /** Transport preference, ordered like ProtonVPN's Smart protocol. */
    public enum Protocol {
        SMART,          // try WG_UDP -> WG_TCP -> WG_TLS in order
        WIREGUARD_UDP,
        WIREGUARD_TCP,
        WIREGUARD_TLS   // stealth-style TLS obfuscation, needs an obfuscation endpoint
    }

    /** NetShield-style DNS filtering level. */
    public enum NetShieldLevel {
        OFF,
        MALWARE,
        ADS_MALWARE,
        ADS_MALWARE_ADULT
    }

    /** How IPv6 traffic is handled while the tunnel is up. */
    public enum Ipv6Mode {
        BLOCK,   // capture ::/0 into the tunnel so nothing leaks outside it
        ROUTE,   // route IPv6 through the tunnel normally
        OFF      // leave IPv6 untouched (leak risk)
    }

    /** Per-app / per-IP split tunneling. */
    public enum SplitTunnelMode {
        DISABLED,
        INCLUDE_ONLY,  // only these apps/IPs use the tunnel
        EXCLUDE        // everything except these apps/IPs uses the tunnel
    }

    public static class ServerInfo {
        public String id = "";
        public String name = "";
        public String country = "";
        public String endpoint = "";   // IP or hostname of the WireGuard peer
        public int port = 51820;
        public String publicKey = "";  // base64 WireGuard public key
    }

    public static class SplitTunnelConfig {
        public SplitTunnelMode mode = SplitTunnelMode.DISABLED;
        public final List<String> apps = new ArrayList<String>(); // package names
        public final List<String> ips = new ArrayList<String>();  // CIDR strings
    }

    public static class KillSwitchConfig {
        public boolean enabled = true;
        /** True once the user has been guided through the system Always-on VPN screen. */
        public boolean setupGuidanceShown = false;
    }

    /** Short-lived tunnel credentials issued by the control hub. */
    public static class Credentials {
        public String privateKey = "";   // base64 WireGuard private key
        public String address = "";      // e.g. "10.8.0.2/32"
        public long expiresAtMillis = 0; // epoch ms; 0 = non-expiring (not recommended)
    }

    /** Secure Core multi-hop: route entry -> exit through two servers. */
    public static class SecureCoreConfig {
        public boolean enabled = false;
        public String entryId = "";  // logical server id of the entry hop
        public String exitId = "";   // logical server id of the exit hop
    }

    public int version = 1;
    public final ServerInfo server = new ServerInfo();
    public Protocol protocol = Protocol.SMART;
    public NetShieldLevel netShieldLevel = NetShieldLevel.OFF;
    public final KillSwitchConfig killSwitch = new KillSwitchConfig();
    public final SplitTunnelConfig splitTunnel = new SplitTunnelConfig();
    public final List<String> customDns = new ArrayList<String>();
    public boolean lanBypass = true;
    public Ipv6Mode ipv6Mode = Ipv6Mode.BLOCK;
    public final Credentials credentials = new Credentials();
    public final SecureCoreConfig secureCore = new SecureCoreConfig();
    /** Request a forwarded port for P2P on P2P-labeled servers (server must honor it). */
    public boolean p2pPortForward = false;
    public int mtu = 1280;

    // ------------------------------------------------------------------ JSON

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("version", version);

        JSONObject s = new JSONObject();
        s.put("id", server.id);
        s.put("name", server.name);
        s.put("country", server.country);
        s.put("endpoint", server.endpoint);
        s.put("port", server.port);
        s.put("publicKey", server.publicKey);
        o.put("server", s);

        o.put("protocol", protocol.name().toLowerCase());
        o.put("netshield", netShieldLevel.name().toLowerCase());
        o.put("lanBypass", lanBypass);
        o.put("ipv6Mode", ipv6Mode.name().toLowerCase());
        o.put("mtu", mtu);

        JSONObject ks = new JSONObject();
        ks.put("enabled", killSwitch.enabled);
        ks.put("setupGuidanceShown", killSwitch.setupGuidanceShown);
        o.put("killSwitch", ks);

        JSONObject st = new JSONObject();
        st.put("mode", splitTunnel.mode.name().toLowerCase());
        st.put("apps", new JSONArray(splitTunnel.apps));
        st.put("ips", new JSONArray(splitTunnel.ips));
        o.put("splitTunnel", st);

        o.put("customDns", new JSONArray(customDns));

        JSONObject c = new JSONObject();
        c.put("privateKey", credentials.privateKey);
        c.put("address", credentials.address);
        c.put("expiresAt", credentials.expiresAtMillis);
        o.put("credentials", c);

        JSONObject sc = new JSONObject();
        sc.put("enabled", secureCore.enabled);
        sc.put("entryId", secureCore.entryId);
        sc.put("exitId", secureCore.exitId);
        o.put("secureCore", sc);

        o.put("p2pPortForward", p2pPortForward);

        return o;
    }

    public static VpnConfig fromJson(JSONObject o) throws JSONException {
        VpnConfig cfg = new VpnConfig();
        cfg.version = o.optInt("version", 1);

        JSONObject s = o.optJSONObject("server");
        if (s != null) {
            cfg.server.id = s.optString("id", "");
            cfg.server.name = s.optString("name", "");
            cfg.server.country = s.optString("country", "");
            cfg.server.endpoint = s.optString("endpoint", "");
            cfg.server.port = s.optInt("port", 51820);
            cfg.server.publicKey = s.optString("publicKey", "");
        }

        cfg.protocol = parseEnum(Protocol.class,
                normalizeEnum(o.optString("protocol", "smart")), Protocol.SMART);
        cfg.netShieldLevel = parseEnum(NetShieldLevel.class,
                normalizeEnum(o.optString("netshield", "off")), NetShieldLevel.OFF);

        // Dashboard nests DNS under "dns"; accept both shapes.
        JSONObject dns = o.optJSONObject("dns");
        cfg.lanBypass = dns != null ? dns.optBoolean("lanBypass", true)
                : o.optBoolean("lanBypass", true);
        String ipv6Raw = dns != null && dns.has("ipv6") ? dns.optString("ipv6", "block")
                : o.optString("ipv6Mode", "block");
        cfg.ipv6Mode = parseEnum(Ipv6Mode.class, normalizeEnum(ipv6Raw), Ipv6Mode.BLOCK);
        cfg.mtu = o.optInt("mtu", 1280);

        JSONObject ks = o.optJSONObject("killSwitch");
        if (ks != null) {
            cfg.killSwitch.enabled = ks.optBoolean("enabled", true);
            cfg.killSwitch.setupGuidanceShown = ks.optBoolean("setupGuidanceShown", false);
            // Dashboard sends "lockdown" (prompt for OS always-on + lockdown).
            // If lockdown is requested and guidance was never shown, we still owe it.
            if (ks.optBoolean("lockdown", false) && !cfg.killSwitch.setupGuidanceShown) {
                cfg.killSwitch.setupGuidanceShown = false;
            }
        }

        // Dashboard uses "splitTunneling" (with an "enabled" flag); accept both shapes.
        JSONObject st = o.optJSONObject("splitTunnel");
        if (st == null) st = o.optJSONObject("splitTunneling");
        if (st != null) {
            boolean stEnabled = st.optBoolean("enabled", true);
            String modeRaw = normalizeEnum(st.optString("mode", "disabled"));
            if ("INCLUDE".equals(modeRaw)) modeRaw = "INCLUDE_ONLY";
            cfg.splitTunnel.mode = stEnabled
                    ? parseEnum(SplitTunnelMode.class, modeRaw, SplitTunnelMode.DISABLED)
                    : SplitTunnelMode.DISABLED;
            copyStrings(st.optJSONArray("apps"), cfg.splitTunnel.apps);
            copyStrings(st.optJSONArray("ips"), cfg.splitTunnel.ips);
        }

        JSONArray customDns = o.optJSONArray("customDns");
        if (customDns == null && dns != null) customDns = dns.optJSONArray("custom");
        copyStrings(customDns, cfg.customDns);

        JSONObject c = o.optJSONObject("credentials");
        if (c != null) {
            cfg.credentials.privateKey = c.optString("privateKey", "");
            cfg.credentials.address = c.optString("address", "");
            cfg.credentials.expiresAtMillis = c.optLong("expiresAt", 0);
        }

        JSONObject sc = o.optJSONObject("secureCore");
        if (sc != null) {
            cfg.secureCore.enabled = sc.optBoolean("enabled", false);
            cfg.secureCore.entryId = sc.optString("entryId", "");
            cfg.secureCore.exitId = sc.optString("exitId", "");
        }
        // Dashboard nests these under "secureCore"/"p2p"; accept flat "p2pPortForward" too.
        JSONObject p2p = o.optJSONObject("p2p");
        cfg.p2pPortForward = p2p != null
                ? p2p.optBoolean("portForward", false)
                : o.optBoolean("p2pPortForward", false);
        return cfg;
    }

    public static VpnConfig fromJsonString(String json) throws JSONException {
        return fromJson(new JSONObject(json));
    }

    public String toJsonString() throws JSONException {
        return toJson().toString();
    }

    /** Credentials are considered stale 5 minutes before expiry. */
    public boolean credentialsNeedRefresh() {
        if (credentials.expiresAtMillis <= 0) return false;
        return System.currentTimeMillis() > credentials.expiresAtMillis - 5 * 60 * 1000L;
    }

    public List<String> effectiveDnsServers() {
        if (!customDns.isEmpty()) return Collections.unmodifiableList(customDns);
        return NetShieldDns.resolverIpsFor(netShieldLevel);
    }

    // ---------------------------------------------------------------- helpers

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * Normalizes dashboard enum spellings to the Java enum names:
     * hyphens become underscores ("wireguard-udp" -> "wireguard_udp"),
     * and dashboard aliases are mapped ("ads-trackers" -> "ads_malware",
     * "adult" -> "ads_malware_adult").
     */
    private static String normalizeEnum(String value) {
        if (value == null) return "";
        String v = value.trim().toLowerCase().replace('-', '_');
        if ("ads_trackers".equals(v)) return "ads_malware";
        if ("adult".equals(v)) return "ads_malware_adult";
        return v;
    }

    private static void copyStrings(JSONArray arr, List<String> out) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            String v = arr.optString(i, null);
            if (v != null && !v.isEmpty()) out.add(v);
        }
    }
}
