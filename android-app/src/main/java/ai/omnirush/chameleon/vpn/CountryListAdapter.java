package ai.omnirush.chameleon.vpn;

import ai.omnirush.chameleon.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Country-grouped server list for the Countries tab.
 */
public class CountryListAdapter extends BaseAdapter {

    /** One row: a country and its servers. */
    public static class CountryRow {
        public final String country;
        public final List<HubClient.HubServer> servers = new ArrayList<HubClient.HubServer>();
        public CountryRow(String country) { this.country = country; }
        public int bestLatency() {
            int best = Integer.MAX_VALUE;
            for (HubClient.HubServer s : servers) {
                if (s.latencyMs >= 0 && s.latencyMs < best) best = s.latencyMs;
            }
            return best == Integer.MAX_VALUE ? -1 : best;
        }
    }

    private final LayoutInflater inflater;
    private final List<CountryRow> rows = new ArrayList<CountryRow>();

    public CountryListAdapter(Context ctx) {
        inflater = LayoutInflater.from(ctx);
    }

    public void setRows(List<CountryRow> newRows) {
        rows.clear();
        if (newRows != null) rows.addAll(newRows);
        notifyDataSetChanged();
    }

    public CountryRow getRow(int position) { return rows.get(position); }

    @Override public int getCount() { return rows.size(); }
    @Override public Object getItem(int position) { return rows.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) v = inflater.inflate(R.layout.item_country, parent, false);
        CountryRow row = rows.get(position);

        TextView flag = v.findViewById(R.id.country_flag);
        TextView name = v.findViewById(R.id.country_name);
        TextView sub = v.findViewById(R.id.country_sub);

        String countryName = row.country.isEmpty() ? "Unknown" : row.country;
        flag.setText(ServerListAdapter.flagFor(row.country));
        name.setText(capitalize(countryName));
        int n = row.servers.size();
        int lat = row.bestLatency();
        sub.setText(n + (n == 1 ? " server" : " servers")
                + (lat >= 0 ? " · " + lat + " ms" : ""));
        return v;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
