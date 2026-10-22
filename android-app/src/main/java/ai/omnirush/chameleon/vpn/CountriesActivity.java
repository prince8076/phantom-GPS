package ai.omnirush.chameleon.vpn;

import ai.omnirush.chameleon.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Countries tab: filter chips (All / Secure Core / P2P / Tor) and a
 * country-grouped server list. Tapping a country connects to its fastest
 * server via the standard connect flow.
 */
public class CountriesActivity extends Activity {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_SECURE = 1;
    private static final int FILTER_P2P = 2;
    private static final int FILTER_TOR = 3;

    private TextView hint;
    private ListView list;
    private CountryListAdapter adapter;
    private final List<HubClient.HubServer> allServers = new ArrayList<HubClient.HubServer>();
    private int activeFilter = FILTER_ALL;
    private Button[] chips;

    /** Node ids known to support each capability (until the hub serves flags). */
    private static final Map<Integer, Set<String>> FILTER_NODES = new LinkedHashMap<Integer, Set<String>>();
    static {
        Set<String> secure = new HashSet<String>();
        secure.add("zurich"); secure.add("reykjavik"); secure.add("stockholm"); secure.add("oslo");
        FILTER_NODES.put(FILTER_SECURE, secure);
        Set<String> p2p = new HashSet<String>();
        p2p.add("zurich"); p2p.add("reykjavik"); p2p.add("helsinki"); p2p.add("berlin");
        p2p.add("frankfurt"); p2p.add("amsterdam"); p2p.add("newyork"); p2p.add("losangeles");
        p2p.add("toronto"); p2p.add("singapore"); p2p.add("vienna"); p2p.add("prague");
        p2p.add("bucharest");
        FILTER_NODES.put(FILTER_P2P, p2p);
        Set<String> tor = new HashSet<String>();
        tor.add("zurich"); tor.add("reykjavik"); tor.add("stockholm");
        FILTER_NODES.put(FILTER_TOR, tor);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_countries);

        hint = findViewById(R.id.countries_hint);
        list = findViewById(R.id.country_list);
        adapter = new CountryListAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view,
                                              int position, long id) {
                connectToCountry(adapter.getRow(position));
            }
        });

        chips = new Button[]{
                findViewById(R.id.chip_all), findViewById(R.id.chip_secure),
                findViewById(R.id.chip_p2p), findViewById(R.id.chip_tor)};
        for (int i = 0; i < chips.length; i++) {
            final int index = i;
            chips[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { setFilter(index); }
            });
        }
        setFilter(FILTER_ALL);

        BottomNavHelper.wire(this, BottomNavHelper.TAB_COUNTRIES);
        loadServers();
    }

    private void setFilter(int filter) {
        activeFilter = filter;
        for (int i = 0; i < chips.length; i++) {
            boolean sel = (i == filter);
            chips[i].setSelected(sel);
            chips[i].setTextColor(sel ? 0xFFFFFFFF : 0xFF9A93B8);
        }
        applyFilter();
    }

    private void applyFilter() {
        List<CountryListAdapter.CountryRow> rows = groupByCountry(filteredServers());
        adapter.setRows(rows);
        hint.setText(rows.isEmpty() ? "No countries match this filter."
                : rows.size() + (rows.size() == 1 ? " country" : " countries"));
    }

    private List<HubClient.HubServer> filteredServers() {
        if (activeFilter == FILTER_ALL) return allServers;
        Set<String> ids = FILTER_NODES.get(activeFilter);
        List<HubClient.HubServer> out = new ArrayList<HubClient.HubServer>();
        for (HubClient.HubServer s : allServers) {
            if (ids != null && ids.contains(s.id)) out.add(s);
        }
        return out;
    }

    private static List<CountryListAdapter.CountryRow> groupByCountry(
            List<HubClient.HubServer> servers) {
        Map<String, CountryListAdapter.CountryRow> map =
                new LinkedHashMap<String, CountryListAdapter.CountryRow>();
        for (HubClient.HubServer s : servers) {
            String key = s.country == null ? "" : s.country.trim().toLowerCase();
            CountryListAdapter.CountryRow row = map.get(key);
            if (row == null) {
                row = new CountryListAdapter.CountryRow(key);
                map.put(key, row);
            }
            row.servers.add(s);
        }
        return new ArrayList<CountryListAdapter.CountryRow>(map.values());
    }

    private void loadServers() {
        final String hubUrl = HubSettings.getHubUrl(this);
        if (hubUrl.isEmpty()) {
            hint.setText("Pair with the control hub first (dashboard → Phone sync → QR).");
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    HubClient hub = new HubClient(hubUrl);
                    final List<HubClient.HubServer> servers = hub.fetchServers();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            allServers.clear();
                            allServers.addAll(servers);
                            applyFilter();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            hint.setText("Could not reach hub: " + e.getMessage());
                        }
                    });
                }
            }
        }, "countries-load").start();
    }

    /** Connects to the fastest server in the tapped country. */
    private void connectToCountry(CountryListAdapter.CountryRow row) {
        if (row.servers.isEmpty()) return;
        HubClient.HubServer best = row.servers.get(0);
        for (HubClient.HubServer s : row.servers) {
            if (s.latencyMs >= 0 && (best.latencyMs < 0 || s.latencyMs < best.latencyMs)) {
                best = s;
            }
        }
        final String serverId = best.id;
        Toast.makeText(this, "Connecting via " + best.name + "…", Toast.LENGTH_SHORT).show();
        VpnConnector.prepareConfig(this, null, new VpnConnector.PrepareCallback() {
            @Override public void onReady(VpnConfig config) {
                config.server.id = serverId;
                VpnSettings.applyTo(CountriesActivity.this, config);
                try {
                    Intent home = new Intent(CountriesActivity.this, HomeActivity.class);
                    home.putExtra("connect_config", config.toJsonString());
                    home.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(home);
                } catch (Exception e) {
                    Toast.makeText(CountriesActivity.this,
                            "Could not start connection: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onError(String message) {
                Toast.makeText(CountriesActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
