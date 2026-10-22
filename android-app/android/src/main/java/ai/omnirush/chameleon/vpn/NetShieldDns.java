package ai.omnirush.chameleon.vpn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * NetShield-style DNS filtering configuration.
 *
 * <p>Concept observed in ProtonVPN's client: filtering is enforced at the DNS
 * layer with selectable levels (malware / ads+trackers / adult content).
 * Our clean-room version routes tunnel DNS through filtering resolvers
 * chosen per level, plus an optional on-device blocklist hook for a local
 * DNS proxy. Clean-room implementation — resolver addresses below are
 * placeholders the deployer MUST configure (see {@link #setResolverIps}).
 */
public class NetShieldDns {

    /** Default (placeholder) filtering resolvers per level. Override in production. */
    private static volatile List<String> malwareResolvers =
            Collections.unmodifiableList(Arrays.asList("94.140.14.15", "94.140.15.16"));
    private static volatile List<String> adsMalwareResolvers =
            Collections.unmodifiableList(Arrays.asList("94.140.14.15", "94.140.15.16"));
    private static volatile List<String> adultResolvers =
            Collections.unmodifiableList(Arrays.asList("94.140.14.15", "94.140.15.16"));
    private static volatile List<String> plainResolvers =
            Collections.unmodifiableList(Arrays.asList("1.1.1.1", "1.0.0.1"));

    /**
     * Points the level at real filtering-resolver IPs supplied by the
     * control hub (dashboard -> phone sync). Call once at startup.
     */
    public static void setResolverIps(VpnConfig.NetShieldLevel level, List<String> ips) {
        List<String> copy = Collections.unmodifiableList(new ArrayList<String>(ips));
        switch (level) {
            case MALWARE:          malwareResolvers = copy; break;
            case ADS_MALWARE:      adsMalwareResolvers = copy; break;
            case ADS_MALWARE_ADULT: adultResolvers = copy; break;
            case OFF:
            default:               plainResolvers = copy; break;
        }
    }

    /** DNS servers to push via VpnService.Builder.addDnsServer(). */
    public static List<String> resolverIpsFor(VpnConfig.NetShieldLevel level) {
        switch (level) {
            case MALWARE:           return malwareResolvers;
            case ADS_MALWARE:       return adsMalwareResolvers;
            case ADS_MALWARE_ADULT: return adultResolvers;
            case OFF:
            default:                return plainResolvers;
        }
    }

    public static boolean isFiltering(VpnConfig.NetShieldLevel level) {
        return level != VpnConfig.NetShieldLevel.OFF;
    }

    // ---------------------------------------------------------- blocklist hook

    /**
     * Hook point for a local DNS proxy: domains added here would be answered
     * with NXDOMAIN / 0.0.0.0 by the on-device proxy. The default
     * implementation is an in-memory set; wire it to a persistent blocklist
     * feed (synced from the dashboard) in production.
     */
    public interface DnsBlocklist {
        void blockDomain(String domain);
        void unblockDomain(String domain);
        boolean isBlocked(String domain);
        void clear();
    }

    public static class InMemoryBlocklist implements DnsBlocklist {
        private final Set<String> blocked = new HashSet<String>();

        @Override public synchronized void blockDomain(String domain) {
            blocked.add(normalize(domain));
        }

        @Override public synchronized void unblockDomain(String domain) {
            blocked.remove(normalize(domain));
        }

        @Override public synchronized boolean isBlocked(String domain) {
            String d = normalize(domain);
            // Match the domain and any parent (sub.example.com matches example.com).
            while (true) {
                if (blocked.contains(d)) return true;
                int dot = d.indexOf('.');
                if (dot < 0) return false;
                d = d.substring(dot + 1);
            }
        }

        @Override public synchronized void clear() {
            blocked.clear();
        }

        public synchronized int size() {
            return blocked.size();
        }

        private static String normalize(String domain) {
            String d = domain.trim().toLowerCase();
            while (d.endsWith(".")) d = d.substring(0, d.length() - 1);
            return d;
        }
    }
}
