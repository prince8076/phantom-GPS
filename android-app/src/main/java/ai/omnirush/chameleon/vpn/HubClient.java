package ai.omnirush.chameleon.vpn;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client for the Phantom control hub (desktop-app/server.js) and, through
 * it, the user's own VPN server infrastructure.
 *
 * <p>Implements the client side of {@code SERVER_CONTRACT.md}:
 * <ul>
 *   <li>{@code GET /api/vpn/config} — dashboard-pushed client config</li>
 *   <li>{@code GET /api/servers} — logical server list with per-protocol endpoints</li>
 *   <li>{@code POST /api/vpn/credentials} — short-lived WireGuard credentials</li>
 *   <li>{@code GET /api/netshield/resolvers?level=...} — filtering resolver IPs</li>
 * </ul>
 *
 * <p>Also implements {@link CredentialProvider} so {@link PhantomVpnService}
 * can refresh credentials before expiry.
 *
 * <p>All network I/O is blocking — never call on the main thread.
 * Clean-room implementation; no Proton code involved.
 */
public class HubClient implements CredentialProvider {

    /** One entry from {@code GET /api/servers}. */
    public static class HubServer {
        public String id = "";
        public String country = "";
        public String city = "";
        public String name = "";
        public int load = -1;          // 0-100, -1 = unknown
        public int latencyMs = -1;     // -1 = unknown
        /** protocol name (e.g. "wireguard-udp") -> endpoint. */
        public final java.util.Map<String, Endpoint> endpoints =
                new java.util.HashMap<String, Endpoint>();
    }

    public static class Endpoint {
        public String host = "";
        public int port = 51820;
        public String publicKey = "";
        public String sni = "";        // only for wireguard-tls
    }

    private final String baseUrl;      // e.g. "http://192.168.1.5:3300"
    private final String bearerToken;  // may be empty for trusted local hub
    private final int timeoutMillis;

    public HubClient(String baseUrl) {
        this(baseUrl, "", 15000);
    }

    public HubClient(String baseUrl, String bearerToken, int timeoutMillis) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        this.baseUrl = b;
        this.bearerToken = bearerToken == null ? "" : bearerToken;
        this.timeoutMillis = timeoutMillis;
    }

    // ------------------------------------------------------------ config push

    /**
     * Fetches the config most recently pushed from the dashboard.
     * Returns null when the hub has no pushed config yet (HTTP 404).
     */
    public VpnConfig fetchPushedConfig() throws Exception {
        HttpResult r = get("/api/vpn/config");
        if (r.status == 404) return null;
        checkOk(r, "fetch pushed config");
        JSONObject body = new JSONObject(r.body);
        JSONObject cfg = body.optJSONObject("config");
        if (cfg == null) throw new IllegalStateException("hub returned no config object");
        return VpnConfig.fromJson(cfg);
    }

    // ------------------------------------------------------------ server list

    /** Fetches the logical server list. Never returns null (may be empty). */
    public List<HubServer> fetchServers() throws Exception {
        HttpResult r = get("/api/servers");
        checkOk(r, "fetch server list");
        List<HubServer> out = new ArrayList<HubServer>();
        JSONObject body = new JSONObject(r.body);
        JSONArray arr = body.optJSONArray("servers");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s == null) continue;
            HubServer hs = new HubServer();
            hs.id = s.optString("id", "");
            hs.country = s.optString("country", "");
            hs.city = s.optString("city", "");
            hs.name = s.optString("name", hs.id);
            hs.load = s.optInt("load", -1);
            hs.latencyMs = s.optInt("latencyMs", -1);
            JSONObject eps = s.optJSONObject("endpoints");
            if (eps != null) {
                java.util.Iterator<String> keys = eps.keys();
                while (keys.hasNext()) {
                    String proto = keys.next();
                    JSONObject e = eps.optJSONObject(proto);
                    if (e == null) continue;
                    Endpoint ep = new Endpoint();
                    ep.host = e.optString("host", "");
                    ep.port = e.optInt("port", 51820);
                    ep.publicKey = e.optString("publicKey", "");
                    ep.sni = e.optString("sni", "");
                    hs.endpoints.put(proto, ep);
                }
            }
            out.add(hs);
        }
        return out;
    }

    /**
     * Resolves {@code cfg.server} (id and/or selection mode is carried in
     * {@code cfg.server.id}; empty id means "fastest") into a concrete
     * endpoint + public key using the hub server list and the requested
     * protocol. Mutates and returns {@code cfg}.
     *
     * @throws IllegalStateException when no usable server/endpoint is found.
     */
    public VpnConfig resolveServer(VpnConfig cfg, List<HubServer> servers) {
        if (servers == null || servers.isEmpty()) {
            throw new IllegalStateException("no servers available from hub");
        }
        String wantId = cfg.server.id == null ? "" : cfg.server.id.trim();
        HubServer pick = null;
        if (!wantId.isEmpty()) {
            for (HubServer hs : servers) {
                if (wantId.equalsIgnoreCase(hs.id)) { pick = hs; break; }
            }
        }
        if (pick == null) {
            // Fastest: lowest known latency, then lowest load.
            for (HubServer hs : servers) {
                if (pick == null) { pick = hs; continue; }
                int aLat = hs.latencyMs < 0 ? Integer.MAX_VALUE : hs.latencyMs;
                int bLat = pick.latencyMs < 0 ? Integer.MAX_VALUE : pick.latencyMs;
                if (aLat < bLat || (aLat == bLat && hs.load >= 0
                        && (pick.load < 0 || hs.load < pick.load))) {
                    pick = hs;
                }
            }
        }
        String protoKey = protocolKey(cfg.protocol);
        Endpoint ep = pick.endpoints.get(protoKey);
        if (ep == null && !pick.endpoints.isEmpty()) {
            // Fall back to any available endpoint for this server.
            ep = pick.endpoints.values().iterator().next();
        }
        if (ep == null || ep.host.isEmpty() || ep.publicKey.isEmpty()) {
            throw new IllegalStateException(
                    "server '" + pick.id + "' has no usable endpoint for " + protoKey);
        }
        cfg.server.id = pick.id;
        cfg.server.name = pick.name;
        cfg.server.country = pick.country;
        cfg.server.endpoint = ep.host;
        cfg.server.port = ep.port;
        cfg.server.publicKey = ep.publicKey;
        return cfg;
    }

    private static String protocolKey(VpnConfig.Protocol p) {
        switch (p) {
            case WIREGUARD_UDP: return "wireguard-udp";
            case WIREGUARD_TCP: return "wireguard-tcp";
            case WIREGUARD_TLS: return "wireguard-tls";
            case SMART:
            default: return "wireguard-udp"; // Smart starts on UDP; selector falls back
        }
    }

    // ------------------------------------------------------------ credentials

    /**
     * Issues (or refreshes) short-lived credentials for the given server.
     * Test-mode servers may return the client private key as well; production
     * servers should have the client generate its own keypair and send only
     * the public key (see {@link #fetchCredentials(String, String, String, String)}).
     */
    public VpnConfig.Credentials fetchCredentials(String serverId, String protocolKey)
            throws Exception {
        return fetchCredentials(serverId, protocolKey, null);
    }

    /**
     * @param clientPublicKey base64 WireGuard public key, or null when the
     *        server issues the client keypair (test mode).
     */
    public VpnConfig.Credentials fetchCredentials(
            String serverId, String protocolKey, String clientPublicKey) throws Exception {
        JSONObject req = new JSONObject();
        req.put("serverId", serverId);
        req.put("protocol", protocolKey);
        if (clientPublicKey != null && !clientPublicKey.isEmpty()) {
            req.put("publicKey", clientPublicKey);
        }
        HttpResult r = post("/api/vpn/credentials", req.toString());
        checkOk(r, "fetch credentials");
        JSONObject body = new JSONObject(r.body);
        VpnConfig.Credentials c = new VpnConfig.Credentials();
        c.privateKey = body.optString("privateKey", "");
        c.address = body.optString("clientAddress", body.optString("address", ""));
        String exp = body.optString("expiresAt", "");
        if (!exp.isEmpty()) {
            try {
                if (exp.matches("^\\d+$")) {
                    c.expiresAtMillis = Long.parseLong(exp);
                } else {
                    // ISO-8601, e.g. "2026-10-23T22:00:00Z".
                    java.text.SimpleDateFormat f =
                            new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                                    java.util.Locale.US);
                    f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    java.util.Date d = f.parse(exp);
                    if (d != null) c.expiresAtMillis = d.getTime();
                }
            } catch (Exception ignored) { /* leave as 0 = non-expiring */ }
        } else {
            c.expiresAtMillis = body.optLong("expiresAtMillis", 0);
        }
        return c;
    }

    /** CredentialProvider seam: refresh when stale. */
    @Override
    public VpnConfig refreshIfNeeded(VpnConfig config) throws Exception {
        if (config == null) throw new IllegalArgumentException("config is null");
        boolean missing = config.credentials.privateKey.isEmpty()
                || config.credentials.address.isEmpty();
        if (!missing && !config.credentialsNeedRefresh()) return config;
        VpnConfig.Credentials fresh = fetchCredentials(
                config.server.id, protocolKey(config.protocol), null);
        if (!fresh.privateKey.isEmpty()) config.credentials.privateKey = fresh.privateKey;
        if (!fresh.address.isEmpty()) config.credentials.address = fresh.address;
        if (fresh.expiresAtMillis > 0) config.credentials.expiresAtMillis = fresh.expiresAtMillis;
        return config;
    }

    // ------------------------------------------------------------ netshield

    /** Returns filtering resolver IPs for the level, or empty list on failure. */
    public List<String> fetchNetShieldResolvers(VpnConfig.NetShieldLevel level) {
        List<String> out = new ArrayList<String>();
        try {
            String q = URLEncoder.encode(level.name().toLowerCase(), "UTF-8");
            HttpResult r = get("/api/netshield/resolvers?level=" + q);
            if (r.status != 200) return out;
            JSONArray arr = new JSONObject(r.body).optJSONArray("resolvers");
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                String ip = arr.optString(i, "");
                if (!ip.isEmpty()) out.add(ip);
            }
        } catch (Exception ignored) { /* keep static fallback */ }
        return out;
    }

    // ------------------------------------------------------------ http plumbing

    private static class HttpResult {
        int status;
        String body = "";
    }

    private HttpResult get(String path) throws Exception {
        HttpURLConnection c = open(path);
        c.setRequestMethod("GET");
        return read(c);
    }

    private HttpResult post(String path, String jsonBody) throws Exception {
        HttpURLConnection c = open(path);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        OutputStream os = c.getOutputStream();
        try {
            os.write(bytes);
        } finally {
            os.close();
        }
        return read(c);
    }

    private HttpURLConnection open(String path) throws Exception {
        URL url = new URL(baseUrl + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(timeoutMillis);
        c.setReadTimeout(timeoutMillis);
        c.setRequestProperty("Accept", "application/json");
        if (!bearerToken.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        return c;
    }

    private static HttpResult read(HttpURLConnection c) throws Exception {
        HttpResult r = new HttpResult();
        r.status = c.getResponseCode();
        InputStream in = r.status >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in != null) {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            try {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                r.body = sb.toString();
            } finally {
                br.close();
            }
        }
        c.disconnect();
        return r;
    }

    private static void checkOk(HttpResult r, String action) throws Exception {
        if (r.status < 200 || r.status >= 300) {
            throw new IllegalStateException(
                    "hub failed to " + action + ": HTTP " + r.status);
        }
    }
}
