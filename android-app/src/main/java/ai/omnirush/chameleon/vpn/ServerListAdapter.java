package ai.omnirush.chameleon.vpn;

import ai.omnirush.chameleon.R;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ListView adapter for the hub server list on the VPN connection screen.
 * Shows flag, name, feature badges (streaming/P2P/Secure Core/Tor), latency
 * and a load bar — the same information architecture as the web fleet list.
 *
 * <p>Plain-framework adapter (no AndroidX) so it compiles with the
 * build.ps1 pipeline. Clean-room implementation.
 */
public class ServerListAdapter extends BaseAdapter {

    /** Feature flags a hub server may advertise (see SERVER_CONTRACT.md). */
    public static final String FEAT_STREAMING = "streaming";
    public static final String FEAT_P2P = "p2p";
    public static final String FEAT_SECURE_CORE = "secure-core";
    public static final String FEAT_TOR = "tor";

    private static final Map<String, String> COUNTRY_FLAGS = new HashMap<String, String>();
    static {
        COUNTRY_FLAGS.put("switzerland", "\uD83C\uDDE8\uD83C\uDDED");
        COUNTRY_FLAGS.put("japan", "\uD83C\uDDEF\uD83C\uDDF5");
        COUNTRY_FLAGS.put("iceland", "\uD83C\uDDEE\uD83C\uDDF8");
        COUNTRY_FLAGS.put("united states", "\uD83C\uDDFA\uD83C\uDDF8");
        COUNTRY_FLAGS.put("singapore", "\uD83C\uDDF8\uD83C\uDDEC");
        COUNTRY_FLAGS.put("germany", "\uD83C\uDDE9\uD83C\uDDEA");
        COUNTRY_FLAGS.put("united kingdom", "\uD83C\uDDEC\uD83C\uDDE7");
        COUNTRY_FLAGS.put("france", "\uD83C\uDDEB\uD83C\uDDF7");
        COUNTRY_FLAGS.put("netherlands", "\uD83C\uDDF3\uD83C\uDDF1");
        COUNTRY_FLAGS.put("sweden", "\uD83C\uDDF8\uD83C\uDDEA");
        COUNTRY_FLAGS.put("norway", "\uD83C\uDDF3\uD83C\uDDF4");
        COUNTRY_FLAGS.put("finland", "\uD83C\uDDEB\uD83C\uDDEE");
        COUNTRY_FLAGS.put("canada", "\uD83C\uDDE8\uD83C\uDDE6");
        COUNTRY_FLAGS.put("australia", "\uD83C\uDDE6\uD83C\uDDFA");
        COUNTRY_FLAGS.put("south korea", "\uD83C\uDDF0\uD83C\uDDF7");
        COUNTRY_FLAGS.put("uae", "\uD83C\uDDE6\uD83C\uDDEA");
        COUNTRY_FLAGS.put("brazil", "\uD83C\uDDE7\uD83C\uDDF7");
        COUNTRY_FLAGS.put("austria", "\uD83C\uDDE6\uD83C\uDDF9");
        COUNTRY_FLAGS.put("spain", "\uD83C\uDDEA\uD83C\uDDF8");
        COUNTRY_FLAGS.put("italy", "\uD83C\uDDEE\uD83C\uDDF9");
        COUNTRY_FLAGS.put("ireland", "\uD83C\uDDEE\uD83C\uDDEA");
        COUNTRY_FLAGS.put("poland", "\uD83C\uDDF5\uD83C\uDDF1");
        COUNTRY_FLAGS.put("czechia", "\uD83C\uDDE8\uD83C\uDDFF");
        COUNTRY_FLAGS.put("romania", "\uD83C\uDDF7\uD83C\uDDF4");
        COUNTRY_FLAGS.put("taiwan", "\uD83C\uDDF9\uD83C\uDDFC");
        COUNTRY_FLAGS.put("india", "\uD83C\uDDEE\uD83C\uDDF3");
        COUNTRY_FLAGS.put("portugal", "\uD83C\uDDF5\uD83C\uDDF9");
        COUNTRY_FLAGS.put("greece", "\uD83C\uDDEC\uD83C\uDDF7");
        COUNTRY_FLAGS.put("new zealand", "\uD83C\uDDF3\uD83C\uDDFF");
        COUNTRY_FLAGS.put("south africa", "\uD83C\uDDFF\uD83C\uDDE6");
        COUNTRY_FLAGS.put("thailand", "\uD83C\uDDF9\uD83C\uDDED");
    }

    /** Display model: hub server + feature flags from the server contract. */
    public static class Row {
        public final HubClient.HubServer server;
        public final List<String> features = new ArrayList<String>();
        public Row(HubClient.HubServer server) { this.server = server; }
    }

    private final LayoutInflater inflater;
    private final List<Row> rows = new ArrayList<Row>();
    private String selectedId = "";

    public ServerListAdapter(Context ctx) {
        inflater = LayoutInflater.from(ctx);
    }

    public void setServers(List<HubClient.HubServer> servers,
                           Map<String, List<String>> featuresById) {
        rows.clear();
        if (servers != null) {
            for (HubClient.HubServer s : servers) {
                Row r = new Row(s);
                if (featuresById != null && featuresById.containsKey(s.id)) {
                    r.features.addAll(featuresById.get(s.id));
                }
                rows.add(r);
            }
        }
        notifyDataSetChanged();
    }

    public void setSelectedId(String id) {
        selectedId = id == null ? "" : id;
        notifyDataSetChanged();
    }

    public Row getRow(int position) { return rows.get(position); }

    @Override public int getCount() { return rows.size(); }
    @Override public Object getItem(int position) { return rows.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = inflater.inflate(R.layout.item_server, parent, false);
        }
        Row row = rows.get(position);
        HubClient.HubServer s = row.server;

        TextView flag = v.findViewById(R.id.row_flag);
        TextView name = v.findViewById(R.id.row_name);
        TextView badges = v.findViewById(R.id.row_badges);
        TextView meta = v.findViewById(R.id.row_meta);
        ProgressBar load = v.findViewById(R.id.row_load);
        TextView loadText = v.findViewById(R.id.row_load_text);

        flag.setText(flagFor(s.country));
        name.setText(s.name.isEmpty() ? s.id : s.name);
        badges.setText(badgeText(row.features));
        badges.setVisibility(row.features.isEmpty() ? View.GONE : View.VISIBLE);

        String lat = s.latencyMs >= 0 ? s.latencyMs + " ms" : "-- ms";
        meta.setText(lat);

        if (s.load >= 0) {
            load.setProgress(Math.min(100, s.load));
            loadText.setText(s.load + "%");
        } else {
            load.setProgress(0);
            loadText.setText("--%");
        }

        boolean selected = !selectedId.isEmpty() && selectedId.equals(s.id);
        v.setBackgroundColor(selected ? 0xFF241D4D : 0xFF12121A);
        return v;
    }

    /** Flag emoji for a country name; white flag fallback. */
    public static String flagFor(String country) {
        if (country == null) return "\uD83C\uDFF3\uFE0F";
        String f = COUNTRY_FLAGS.get(country.trim().toLowerCase());
        return f != null ? f : "\uD83C\uDFF3\uFE0F";
    }

    private static String badgeText(List<String> features) {
        StringBuilder sb = new StringBuilder();
        for (String f : features) {
            if (sb.length() > 0) sb.append("  ");
            if (FEAT_STREAMING.equals(f)) sb.append("▶ Streaming");
            else if (FEAT_P2P.equals(f)) sb.append("⇄ P2P");
            else if (FEAT_SECURE_CORE.equals(f)) sb.append("🛡 Secure Core");
            else if (FEAT_TOR.equals(f)) sb.append("🧅 Tor OK");
        }
        return sb.toString();
    }
}
