package ai.omnirush.chameleon;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class SearchTextWatcher implements TextWatcher, Runnable {
    private final MainActivity activity;
    private final ArrayAdapter<String> adapter;
    private String currentQuery = "";

    public SearchTextWatcher(MainActivity activity, ArrayAdapter<String> adapter) {
        this.activity = activity;
        this.adapter = adapter;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        if (s == null) return;
        String query = s.toString().trim();
        if (query.length() >= 2 && !query.equalsIgnoreCase(currentQuery)) {
            currentQuery = query;
            new Thread(this).start();
        }
    }

    @Override
    public void run() {
        String baseUrl = activity.getServerUrl();
        if (baseUrl == null || baseUrl.trim().length() == 0) return;

        try {
            String target = baseUrl.trim();
            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                target = "https://" + target;
            }
            String encoded = URLEncoder.encode(currentQuery, "UTF-8");
            URL url = new URL(target + "/api/geocode?q=" + encoded);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "PhantomGPS-Android/2.0");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                if (json.has("results")) {
                    JSONArray arr = json.getJSONArray("results");
                    List<String> list = new ArrayList<String>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        String display = item.optString("displayName", item.optString("name", ""));
                        if (display.length() > 0 && !list.contains(display)) {
                            list.add(display);
                        }
                    }
                    if (!list.isEmpty()) {
                        activity.updateSuggestions(list, adapter);
                    }
                }
            }
            conn.disconnect();
        } catch (Exception ignored) {}
    }
}

