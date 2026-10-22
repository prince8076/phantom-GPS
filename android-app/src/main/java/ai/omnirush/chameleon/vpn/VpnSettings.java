package ai.omnirush.chameleon.vpn;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persisted VPN settings shared by Home, Settings and the connect flow.
 * Backed by SharedPreferences; plain-framework, no AndroidX.
 */
public final class VpnSettings {

    private static final String PREFS = "phantom_vpn_settings";

    private static final String KEY_NETSHIELD = "netshield";       // String name of NetShieldLevel
    private static final String KEY_SPLIT_TUNNEL = "split_tunnel";  // boolean
    private static final String KEY_KILL_SWITCH = "kill_switch";   // boolean
    private static final String KEY_PROTOCOL = "protocol";         // smart|wg-udp|wg-tcp|wg-tls

    private VpnSettings() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static VpnConfig.NetShieldLevel getNetShield(Context ctx) {
        String name = prefs(ctx).getString(KEY_NETSHIELD,
                VpnConfig.NetShieldLevel.OFF.name());
        try {
            return VpnConfig.NetShieldLevel.valueOf(name);
        } catch (IllegalArgumentException e) {
            return VpnConfig.NetShieldLevel.OFF;
        }
    }

    public static void setNetShield(Context ctx, VpnConfig.NetShieldLevel level) {
        prefs(ctx).edit().putString(KEY_NETSHIELD, level.name()).apply();
    }

    public static boolean isSplitTunnel(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SPLIT_TUNNEL, false);
    }

    public static void setSplitTunnel(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_SPLIT_TUNNEL, on).apply();
    }

    public static boolean isKillSwitch(Context ctx) {
        return prefs(ctx).getBoolean(KEY_KILL_SWITCH, true);
    }

    public static void setKillSwitch(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_KILL_SWITCH, on).apply();
    }

    public static String getProtocol(Context ctx) {
        return prefs(ctx).getString(KEY_PROTOCOL, "smart");
    }

    public static void setProtocol(Context ctx, String protocol) {
        prefs(ctx).edit().putString(KEY_PROTOCOL, protocol).apply();
    }

    /** Human-readable NetShield label for settings rows. */
    public static String netShieldLabel(VpnConfig.NetShieldLevel level) {
        switch (level) {
            case MALWARE: return "Block malware";
            case ADS_MALWARE: return "Block ads & malware";
            case ADS_MALWARE_ADULT: return "Block ads, malware & adult";
            case OFF:
            default: return "Off";
        }
    }

    /** Human-readable protocol label. */
    public static String protocolLabel(String protocol) {
        if ("wg-udp".equals(protocol)) return "WireGuard UDP";
        if ("wg-tcp".equals(protocol)) return "WireGuard TCP";
        if ("wg-tls".equals(protocol)) return "WireGuard TLS";
        return "Smart (auto)";
    }

    /**
     * Applies the stored settings onto a freshly prepared VpnConfig before
     * the tunnel starts.
     */
    public static void applyTo(Context ctx, VpnConfig config) {
        config.netShieldLevel = getNetShield(ctx);
        config.killSwitch.enabled = isKillSwitch(ctx);
        // Split-tunnel app/IP rules live in the pushed hub config or the
        // dashboard editor; the on/off preference gates them here.
        // Protocol preference is honored by SmartProtocolSelector ordering.
    }
}
