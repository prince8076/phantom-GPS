package ai.omnirush.chameleon;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener {

    private TextView tvCity;
    private TextView tvCoords;
    private TextView tvStatus;
    private TextView tvProtectionHint;
    private TextView tvLivePing;
    private TextView tvRouteBadge;
    private TextView tvOutboundIp;
    private TextView tvHopCadence;
    private TextView tvActiveUsersCount;
    private TextView tvCadenceCountdown;
    private TextView tvAdminBadge;
    private TextView tvDevicesList;

    private AutoCompleteTextView actvOrigin;
    private AutoCompleteTextView actvDestination;
    private Spinner spTravelMode;
    private Spinner spGlobalPool;
    private Spinner spIntervalChoice;
    private Button btnStartPhoneRoute;
    private Button btnStopPhoneRoute;
    private Button btnRandomHop;
    private Button btnApplyCity;
    private Button btnApplyInterval;
    private EditText etServerUrl;
    private Button btnToggleMock;
    private Button btnDevSettings;

    private boolean isMockActive = false;
    private Handler handler;
    private StatusPollTask pollRunnable;

    private ArrayAdapter<String> originAdapter;
    private ArrayAdapter<String> destAdapter;
    private final List<String> citySuggestions = new ArrayList<String>();

    // Comprehensive 28 global server nodes matching web app PROXY_NODES
    public static final String[][] GLOBAL_NODES = new String[][]{
            {"🇨🇭 Zurich, Switzerland", "47.3769", "8.5417", "zurich"},
            {"🇯🇵 Tokyo, Japan", "35.6762", "139.6503", "tokyo"},
            {"🇮🇸 Reykjavik, Iceland", "64.1466", "-21.9426", "reykjavik"},
            {"🇺🇸 New York, United States", "40.7128", "-74.0060", "newyork"},
            {"🇸🇬 Singapore", "1.3521", "103.8198", "singapore"},
            {"🇩🇪 Berlin, Germany", "52.5200", "13.4050", "berlin"},
            {"🇬🇧 London, United Kingdom", "51.5074", "-0.1278", "london"},
            {"🇫🇷 Paris, France", "48.8566", "2.3522", "paris"},
            {"🇳🇱 Amsterdam, Netherlands", "52.3676", "4.9041", "amsterdam"},
            {"🇸🇪 Stockholm, Sweden", "59.3293", "18.0686", "stockholm"},
            {"🇳🇴 Oslo, Norway", "59.9139", "10.7522", "oslo"},
            {"🇫🇮 Helsinki, Finland", "60.1699", "24.9384", "helsinki"},
            {"🇨🇦 Toronto, Canada", "43.6532", "-79.3832", "toronto"},
            {"🇦🇺 Sydney, Australia", "-33.8688", "151.2093", "sydney"},
            {"🇰🇷 Seoul, South Korea", "37.5665", "126.9780", "seoul"},
            {"🇦🇪 Dubai, United Arab Emirates", "25.2048", "55.2708", "dubai"},
            {"🇧🇷 São Paulo, Brazil", "-23.5505", "-46.6333", "saopaulo"},
            {"🇦🇹 Vienna, Austria", "48.2082", "16.3738", "vienna"},
            {"🇪🇸 Madrid, Spain", "40.4168", "-3.7038", "madrid"},
            {"🇮🇹 Rome, Italy", "41.9028", "12.4964", "rome"},
            {"🇮🇪 Dublin, Ireland", "53.3498", "-6.2603", "dublin"},
            {"🇵🇱 Warsaw, Poland", "52.2297", "21.0122", "warsaw"},
            {"🇨🇿 Prague, Czech Republic", "50.0755", "14.4378", "prague"},
            {"🇷🇴 Bucharest, Romania", "44.4268", "26.1025", "bucharest"},
            {"🇹🇼 Taipei, Taiwan", "25.0330", "121.5654", "taipei"},
            {"🇮🇳 Mumbai, India", "19.0760", "72.8777", "mumbai"},
            {"🇺🇸 Los Angeles, United States", "34.0522", "-118.2437", "losangeles"},
            {"🇩🇪 Frankfurt, Germany", "50.1109", "8.6821", "frankfurt"}
    };

    // Rotation interval options
    public static final String[] INTERVAL_OPTIONS = new String[]{
            "30 seconds",
            "1 minute (60s)",
            "2 minutes (120s)",
            "5 minutes (300s)",
            "10 minutes (600s)",
            "15 minutes (900s)",
            "30 minutes (1800s)",
            "1 hour (3600s)"
    };
    public static final int[] INTERVAL_SECONDS = new int[]{
            30, 60, 120, 300, 600, 900, 1800, 3600
    };

    private int currentCityIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler(Looper.getMainLooper());
        pollRunnable = new StatusPollTask(this);

        tvCity = (TextView) findViewById(R.id.tvCity);
        tvCoords = (TextView) findViewById(R.id.tvCoords);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvProtectionHint = (TextView) findViewById(R.id.tvProtectionHint);
        tvLivePing = (TextView) findViewById(R.id.tvLivePing);
        tvRouteBadge = (TextView) findViewById(R.id.tvRouteBadge);
        tvOutboundIp = (TextView) findViewById(R.id.tvOutboundIp);
        tvHopCadence = (TextView) findViewById(R.id.tvHopCadence);
        tvActiveUsersCount = (TextView) findViewById(R.id.tvActiveUsersCount);
        tvCadenceCountdown = (TextView) findViewById(R.id.tvCadenceCountdown);
        tvAdminBadge = (TextView) findViewById(R.id.tvAdminBadge);
        tvDevicesList = (TextView) findViewById(R.id.tvDevicesList);

        actvOrigin = (AutoCompleteTextView) findViewById(R.id.actvOrigin);
        actvDestination = (AutoCompleteTextView) findViewById(R.id.actvDestination);
        spTravelMode = (Spinner) findViewById(R.id.spTravelMode);
        spGlobalPool = (Spinner) findViewById(R.id.spGlobalPool);
        spIntervalChoice = (Spinner) findViewById(R.id.spIntervalChoice);
        btnStartPhoneRoute = (Button) findViewById(R.id.btnStartPhoneRoute);
        btnStopPhoneRoute = (Button) findViewById(R.id.btnStopPhoneRoute);
        btnRandomHop = (Button) findViewById(R.id.btnRandomHop);
        btnApplyCity = (Button) findViewById(R.id.btnApplyCity);
        btnApplyInterval = (Button) findViewById(R.id.btnApplyInterval);
        etServerUrl = (EditText) findViewById(R.id.etServerUrl);
        btnToggleMock = (Button) findViewById(R.id.btnToggleMock);
        btnDevSettings = (Button) findViewById(R.id.btnDevSettings);

        // Pre-fill rich suggestions list with popular global & Indian cities/states/countries
        populateInitialSuggestions();
        originAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, citySuggestions);
        destAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, citySuggestions);
        actvOrigin.setAdapter(originAdapter);
        actvOrigin.setThreshold(1);
        actvDestination.setAdapter(destAdapter);
        actvDestination.setThreshold(1);

        // Wire real-time query listener to fetch live search suggestions from backend
        setupTextSearchWatcher(actvOrigin, originAdapter);
        setupTextSearchWatcher(actvDestination, destAdapter);

        // Travel mode spinner setup
        String[] modes = new String[]{"vehicle (60 km/h)", "walking (5 km/h)", "bicycle (18 km/h)", "flight (500 km/h)"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        spTravelMode.setAdapter(modeAdapter);

        // Global pool spinner setup
        String[] poolLabels = new String[GLOBAL_NODES.length];
        for (int i = 0; i < GLOBAL_NODES.length; i++) {
            poolLabels[i] = GLOBAL_NODES[i][0];
        }
        ArrayAdapter<String> poolAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, poolLabels);
        spGlobalPool.setAdapter(poolAdapter);

        // Interval timing spinner setup
        ArrayAdapter<String> intervalAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, INTERVAL_OPTIONS);
        spIntervalChoice.setAdapter(intervalAdapter);
        spIntervalChoice.setSelection(3); // default 5 minutes (300s)

        btnStartPhoneRoute.setOnClickListener(this);
        btnStopPhoneRoute.setOnClickListener(this);
        btnRandomHop.setOnClickListener(this);
        btnApplyCity.setOnClickListener(this);
        btnApplyInterval.setOnClickListener(this);
        btnToggleMock.setOnClickListener(this);
        btnDevSettings.setOnClickListener(this);

        checkMockPermissionState();
        handler.postDelayed(pollRunnable, 1000);
    }

    private void populateInitialSuggestions() {
        for (String[] node : GLOBAL_NODES) {
            citySuggestions.add(node[0]);
        }
        citySuggestions.add("Delhi, India");
        citySuggestions.add("Uttam Nagar, Delhi, India");
        citySuggestions.add("Janakpuri, Delhi, India");
        citySuggestions.add("Connaught Place, New Delhi, India");
        citySuggestions.add("Noida, Uttar Pradesh, India");
        citySuggestions.add("Gurugram, Haryana, India");
        citySuggestions.add("Bengaluru, Karnataka, India");
        citySuggestions.add("Hyderabad, Telangana, India");
        citySuggestions.add("Mumbai, Maharashtra, India");
        citySuggestions.add("Kolkata, West Bengal, India");
        citySuggestions.add("Chennai, Tamil Nadu, India");
        citySuggestions.add("California, United States");
        citySuggestions.add("Texas, United States");
        citySuggestions.add("London, England, United Kingdom");
        citySuggestions.add("Paris, Île-de-France, France");
        citySuggestions.add("Berlin, Germany");
        citySuggestions.add("Tokyo, Japan");
        citySuggestions.add("Dubai, United Arab Emirates");
        citySuggestions.add("Sydney, New South Wales, Australia");
        citySuggestions.add("Singapore");
    }

    private void setupTextSearchWatcher(final AutoCompleteTextView actv, final ArrayAdapter<String> adapter) {
        actv.addTextChangedListener(new SearchTextWatcher(this, adapter));
    }

    public void updateSuggestions(final List<String> results, final ArrayAdapter<String> adapter) {
        runOnUiThread(new SuggestionUpdateTask(this, results, adapter));
    }

    public void applyUpdatedSuggestions(List<String> results, ArrayAdapter<String> adapter) {
        for (String r : results) {
            if (!citySuggestions.contains(r)) {
                citySuggestions.add(0, r);
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkMockPermissionState();
    }

    private boolean isMockLocationAllowed() {
        boolean isMockLocation = false;
        try {
            AppOpsManager opsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (opsManager != null) {
                int mode = opsManager.checkOpNoThrow(
                        AppOpsManager.OPSTR_MOCK_LOCATION,
                        Process.myUid(),
                        getPackageName()
                );
                isMockLocation = (mode == AppOpsManager.MODE_ALLOWED);
            }
        } catch (Exception ignored) {}
        return isMockLocation;
    }

    private void checkMockPermissionState() {
        if (!isMockLocationAllowed()) {
            btnDevSettings.setVisibility(View.VISIBLE);
            tvStatus.setText("mock permission needed in developer options");
            tvStatus.setTextColor(0xffff5c7c);
        } else {
            btnDevSettings.setVisibility(View.GONE);
            if (isMockActive) {
                tvStatus.setText("connected · system location secured");
                tvStatus.setTextColor(0xff00f2a9);
            } else {
                tvStatus.setText("tap to connect");
                tvStatus.setTextColor(0xff8a99ad);
            }
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnToggleMock) {
            toggleMockService();
        } else if (id == R.id.btnStartPhoneRoute) {
            triggerStartRoute();
        } else if (id == R.id.btnStopPhoneRoute) {
            triggerStopRoute();
        } else if (id == R.id.btnRandomHop) {
            triggerRandomHop();
        } else if (id == R.id.btnApplyCity) {
            triggerApplySelectedCity();
        } else if (id == R.id.btnApplyInterval) {
            triggerApplyInterval();
        } else if (id == R.id.btnDevSettings) {
            openDeveloperSettings();
        }
    }

    public void showToastOnUiThread(final String message) {
        runOnUiThread(new ToastRunnable(this, message));
    }

    public String getServerUrl() {
        return etServerUrl.getText().toString().trim();
    }

    public void triggerPoll() {
        new Thread(new StatusWorker(this, getServerUrl())).start();
        handler.postDelayed(pollRunnable, 2000);
    }

    public void updateDashboardUi(String city, String coords, String routeStatus,
                                   String outboundIp, String cadence, String countdown,
                                   int activeUsers, String devicesSummary) {
        tvCity.setText(city);
        tvCoords.setText(coords);
        tvOutboundIp.setText(outboundIp);
        tvHopCadence.setText(cadence);
        tvCadenceCountdown.setText(countdown);
        tvActiveUsersCount.setText(activeUsers + (activeUsers == 1 ? " device" : " devices"));
        tvAdminBadge.setText(activeUsers + " active");
        tvDevicesList.setText(devicesSummary);

        if (routeStatus != null && !routeStatus.equalsIgnoreCase("idle")) {
            tvRouteBadge.setText(routeStatus);
            tvRouteBadge.setTextColor(0xff00f2a9);
        } else {
            tvRouteBadge.setText("idle");
            tvRouteBadge.setTextColor(0xff8397a7);
        }
        tvLivePing.setText("● protected");
        tvLivePing.setTextColor(0xff00f2a9);
    }

    private void triggerStartRoute() {
        String origin = actvOrigin.getText().toString().trim();
        String dest = actvDestination.getText().toString().trim();
        String baseUrl = etServerUrl.getText().toString().trim();

        if (origin.length() == 0 || dest.length() == 0) {
            Toast.makeText(this, "please enter origin and destination", Toast.LENGTH_SHORT).show();
            return;
        }

        int pos = spTravelMode.getSelectedItemPosition();
        String mode = (pos == 1) ? "foot" : (pos == 2) ? "bicycle" : (pos == 3) ? "flight" : "vehicle";

        Toast.makeText(this, "calculating route: " + origin + " → " + dest, Toast.LENGTH_SHORT).show();
        new Thread(new RouteStartWorker(this, baseUrl, origin, dest, mode)).start();
    }

    private void triggerStopRoute() {
        PhoneRouteEngine.stopRoute();
        tvRouteBadge.setText("idle");
        tvRouteBadge.setTextColor(0xff8397a7);
        Toast.makeText(this, "route simulation stopped", Toast.LENGTH_SHORT).show();

        String baseUrl = etServerUrl.getText().toString().trim();
        if (baseUrl.length() > 0) {
            new Thread(new ApiActionWorker(this, baseUrl + "/api/route/stop", null)).start();
        }
    }

    public void onHopCompleted(String cityName, double lat, double lon) {
        runOnUiThread(new HopUiUpdateTask(this, cityName, lat, lon));
    }

    public void applyHopUi(String cityName, double lat, double lon) {
        tvCity.setText(cityName);
        tvCoords.setText("lat: " + String.format("%.4f", lat) + ", lon: " + String.format("%.4f", lon));
        Toast.makeText(this, "Hopped to " + cityName + "!", Toast.LENGTH_SHORT).show();
    }

    private void triggerRandomHop() {
        currentCityIndex = (currentCityIndex + 1) % GLOBAL_NODES.length;
        applyLocationByIndex(currentCityIndex);

        String baseUrl = etServerUrl.getText().toString().trim();
        if (baseUrl.length() > 0) {
            new Thread(new RotateHopWorker(this, baseUrl)).start();
        }
    }

    private void triggerApplySelectedCity() {
        int pos = spGlobalPool.getSelectedItemPosition();
        if (pos >= 0 && pos < GLOBAL_NODES.length) {
            currentCityIndex = pos;
            applyLocationByIndex(currentCityIndex);

            String baseUrl = etServerUrl.getText().toString().trim();
            final String nodeId = GLOBAL_NODES[pos][3];
            final String name = GLOBAL_NODES[pos][0];
            final double lat = Double.parseDouble(GLOBAL_NODES[pos][1]);
            final double lon = Double.parseDouble(GLOBAL_NODES[pos][2]);

            if (baseUrl.length() > 0) {
                new Thread(new SelectNodeWorker(this, baseUrl, nodeId, name, lat, lon)).start();
            }
        }
    }

    public String getCurrentCityText() {
        return tvCity != null ? tvCity.getText().toString() : "Zurich, Switzerland";
    }

    public String getCurrentCoordsText() {
        return tvCoords != null ? tvCoords.getText().toString() : "lat: 47.3769, lon: 8.5417";
    }

    private void triggerApplyInterval() {
        int pos = spIntervalChoice.getSelectedItemPosition();
        if (pos >= 0 && pos < INTERVAL_SECONDS.length) {
            int seconds = INTERVAL_SECONDS[pos];
            MockLocationService.setHopInterval(seconds);
            tvHopCadence.setText(seconds + "s");
            Toast.makeText(this, "Hop timing set to " + INTERVAL_OPTIONS[pos], Toast.LENGTH_SHORT).show();

            String baseUrl = etServerUrl.getText().toString().trim();
            if (baseUrl.length() > 0) {
                new Thread(new IntervalChangeWorker(baseUrl, seconds)).start();
            }
        }
    }

    private void applyLocationByIndex(int idx) {
        String[] node = GLOBAL_NODES[idx];
        String cityName = node[0];
        double lat = Double.parseDouble(node[1]);
        double lon = Double.parseDouble(node[2]);

        MockLocationService.setStaticLocation(cityName, lat, lon);
        tvCity.setText(cityName);
        tvCoords.setText("lat: " + String.format("%.4f", lat) + ", lon: " + String.format("%.4f", lon));
        Toast.makeText(this, "Teleported to " + cityName + "!", Toast.LENGTH_SHORT).show();
    }

    private void toggleMockService() {
        Intent intent = new Intent(this, MockLocationService.class);
        intent.putExtra("serverUrl", etServerUrl.getText().toString().trim());

        if (!isMockActive) {
            if (!isMockLocationAllowed()) {
                openDeveloperSettings();
                return;
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                isMockActive = true;
                btnToggleMock.setText("🛑\nconnected");
                btnToggleMock.setTextColor(0xffff5c7c);
                tvStatus.setText("connected · system location secured");
                tvStatus.setTextColor(0xff00f2a9);
                tvProtectionHint.setText("all apps on this device are reading virtual gps");
                Toast.makeText(this, "connected · location protected", Toast.LENGTH_SHORT).show();
            } catch (Exception err) {
                Toast.makeText(this, "Service start error: " + err.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            stopService(intent);
            isMockActive = false;
            btnToggleMock.setText("⚡\nconnect");
            btnToggleMock.setTextColor(0xff00f0ff);
            tvStatus.setText("disconnected");
            tvStatus.setTextColor(0xff8397a7);
            tvProtectionHint.setText("ready to secure with 1-tap");
            Toast.makeText(this, "disconnected · original gps restored", Toast.LENGTH_SHORT).show();
        }
    }

    private void openDeveloperSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "select 'phantom gps' under mock location app", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "open settings -> developer options -> mock location app", Toast.LENGTH_SHORT).show();
        }
    }
}
