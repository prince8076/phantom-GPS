package ai.omnirush.chameleon.vpn;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * "Smart protocol" candidate selection, inspired by the concept observed in
 * ProtonVPN's client (try WireGuard-UDP, then WireGuard-TCP, then
 * WireGuard-TLS/stealth, each with a timeout). Clean-room implementation.
 *
 * <p>Each candidate is probed by asking the {@link WgTunnelBackend} whether
 * it can serve that transport; the first candidate that reports success
 * within its timeout wins. Candidates the backend cannot serve (e.g.
 * WireGuard-over-TCP/TLS, which stock wireguard-go does not implement and
 * which needs a TCP-encapsulation shim) are skipped with a log line instead
 * of failing the whole attempt.
 */
public class SmartProtocolSelector {
    private static final String TAG = "PhantomVpn";

    /** One transport candidate in the fallback chain. */
    public static class Candidate {
        public final VpnConfig.Protocol protocol;
        /** Endpoint override for this candidate (port may differ per transport). */
        public final String endpoint;
        public final int port;
        /** How long to wait for this candidate before moving on. */
        public final long timeoutMillis;
        /** True when the backend reports it can actually serve this transport. */
        public final boolean supported;

        Candidate(VpnConfig.Protocol protocol, String endpoint, int port,
                  long timeoutMillis, boolean supported) {
            this.protocol = protocol;
            this.endpoint = endpoint;
            this.port = port;
            this.timeoutMillis = timeoutMillis;
            this.supported = supported;
        }

        @Override public String toString() {
            return protocol + "@" + endpoint + ":" + port;
        }
    }

    /** Decides whether a candidate is viable before we spend time probing it. */
    public interface Probe {
        /** Return true if the tunnel came up well enough to keep. */
        boolean tryCandidate(Candidate candidate) throws Exception;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Builds the ordered candidate list for a config.
     *
     * @param backendSupportsTcpTls true when a TCP/TLS encapsulation shim is
     *                              configured (stock wireguard-go: false).
     */
    public List<Candidate> buildCandidates(VpnConfig config, boolean backendSupportsTcpTls) {
        List<Candidate> out = new ArrayList<Candidate>();
        String ep = config.server.endpoint;
        int port = config.server.port;

        switch (config.protocol) {
            case SMART:
                out.add(new Candidate(VpnConfig.Protocol.WIREGUARD_UDP, ep, port, 8000, true));
                out.add(new Candidate(VpnConfig.Protocol.WIREGUARD_TCP, ep, tcpPort(port),
                        10000, backendSupportsTcpTls));
                out.add(new Candidate(VpnConfig.Protocol.WIREGUARD_TLS, ep, tlsPort(port),
                        12000, backendSupportsTcpTls));
                break;
            case WIREGUARD_UDP:
                out.add(new Candidate(VpnConfig.Protocol.WIREGUARD_UDP, ep, port, 15000, true));
                break;
            case WIREGUARD_TCP:
                out.add(new Candidate(VpnConfig.Protocol.WIREGUARD_TCP, ep, tcpPort(port),
                        15000, backendSupportsTcpTls));
                break;
            case WIREGUARD_TLS:
                out.add(new Candidate(VpnConfig.Protocol.WIREGUARD_TLS, ep, tlsPort(port),
                        15000, backendSupportsTcpTls));
                break;
        }
        return out;
    }

    /**
     * Runs the fallback chain. Returns the winning candidate, or null when
     * every candidate failed / timed out / was unsupported.
     */
    public Candidate select(VpnConfig config, boolean backendSupportsTcpTls, final Probe probe) {
        List<Candidate> candidates = buildCandidates(config, backendSupportsTcpTls);
        for (final Candidate c : candidates) {
            if (!c.supported) {
                Log.i(TAG, "SmartProtocol: skipping unsupported candidate " + c);
                continue;
            }
            Log.i(TAG, "SmartProtocol: probing " + c);
            Future<Boolean> f = executor.submit(new Callable<Boolean>() {
                @Override public Boolean call() throws Exception {
                    return probe.tryCandidate(c);
                }
            });
            try {
                Boolean ok = f.get(c.timeoutMillis, TimeUnit.MILLISECONDS);
                if (Boolean.TRUE.equals(ok)) {
                    Log.i(TAG, "SmartProtocol: candidate accepted: " + c);
                    return c;
                }
                Log.i(TAG, "SmartProtocol: candidate rejected: " + c);
            } catch (TimeoutException e) {
                Log.i(TAG, "SmartProtocol: candidate timed out: " + c);
                f.cancel(true);
            } catch (Exception e) {
                Log.i(TAG, "SmartProtocol: candidate failed: " + c + " (" + e.getMessage() + ")");
            }
        }
        return null;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------ ports
    // Conventional alternates; the control hub may override per server.

    private static int tcpPort(int udpPort) {
        return udpPort == 51820 ? 51820 : udpPort;
    }

    private static int tlsPort(int udpPort) {
        return 443;
    }
}
