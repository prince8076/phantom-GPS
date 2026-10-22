package ai.omnirush.chameleon;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SelectNodeWorker implements Runnable {
    private final MainActivity activity;
    private final String baseUrl;
    private final String nodeId;
    private final String defaultCityName;
    private final double fallbackLat;
    private final double fallbackLon;

    public SelectNodeWorker(MainActivity activity, String baseUrl, String nodeId,
                            String defaultCityName, double fallbackLat, double fallbackLon) {
        this.activity = activity;
        this.baseUrl = baseUrl;
        this.nodeId = nodeId;
        this.defaultCityName = defaultCityName;
        this.fallbackLat = fallbackLat;
        this.fallbackLon = fallbackLon;
    }

    @Override
    public void run() {
        if (baseUrl == null || baseUrl.trim().length() == 0) return;
        try {
            String target = baseUrl.trim();
            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                target = "https://" + target;
            }
            URL url = new URL(target + "/api/select-node");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "PhantomGPS-Android/2.0");
            conn.setDoOutput(true);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            JSONObject body = new JSONObject();
            body.put("nodeId", nodeId);

            java.io.OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
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

                    MockLocationService.setStaticLocation(cityName, lat, lon);
                    activity.onHopCompleted(cityName, lat, lon);
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            // Apply on-device static location
            MockLocationService.setStaticLocation(defaultCityName, fallbackLat, fallbackLon);
            activity.onHopCompleted(defaultCityName, fallbackLat, fallbackLon);
        }
    }
}
