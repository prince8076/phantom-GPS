package ai.omnirush.chameleon;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class RotateHopWorker implements Runnable {
    private final MainActivity activity;
    private final String baseUrl;

    public RotateHopWorker(MainActivity activity, String baseUrl) {
        this.activity = activity;
        this.baseUrl = baseUrl;
    }

    @Override
    public void run() {
        if (baseUrl == null || baseUrl.trim().length() == 0) return;

        try {
            String target = baseUrl.trim();
            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                target = "https://" + target;
            }
            URL url = new URL(target + "/api/rotate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "PhantomGPS-Android/2.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            // Empty body for POST
            java.io.OutputStream os = conn.getOutputStream();
            os.write(new byte[0]);
            os.close();

            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject res = new JSONObject(sb.toString());
                if (res.has("currentNode")) {
                    JSONObject node = res.getJSONObject("currentNode");
                    String flag = node.optString("flag", "📍");
                    String cityName = flag + " " + node.optString("city", "") + ", " + node.optString("country", "");
                    double lat = node.getDouble("lat");
                    double lon = node.getDouble("lon");

                    // Set location directly on MockLocationService and update UI
                    MockLocationService.setStaticLocation(cityName, lat, lon);
                    activity.onHopCompleted(cityName, lat, lon);
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            activity.showToastOnUiThread("Hop sync notice: using on-device global node");
        }
    }
}

