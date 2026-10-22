package ai.omnirush.chameleon;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class StatusWorker implements Runnable {
    private final MainActivity activity;
    private final String serverUrl;

    public StatusWorker(MainActivity activity, String serverUrl) {
        this.activity = activity;
        this.serverUrl = serverUrl;
    }

    @Override
    public void run() {
        // If an on-device local route is currently active on the phone, update UI from local engine directly!
        if (PhoneRouteEngine.isRunning()) {
            PhoneRouteEngine.RouteStatus rs = PhoneRouteEngine.getStatus();
            String city = "Moving: " + rs.destName;
            String coords = "Lat: " + String.format("%.4f", rs.currentLat) + ", Lon: " + String.format("%.4f", rs.currentLon);
            String routeStatus = "MOVING: " + rs.progressPercent + "% (" + rs.speedKmh + " km/h " + rs.mode + ")";
            activity.runOnUiThread(new UiUpdateTask(
                    activity, city, coords, routeStatus,
                    "185.220.101.98", "300s", "05:00", 1,
                    "• This Phone (Active Route Stepper)"
            ));
            return;
        }

        int hopSec = MockLocationService.getHopIntervalSeconds();
        int remSec = MockLocationService.getSecondsRemaining();
        int remMin = Math.max(0, remSec) / 60;
        int remSeconds = Math.max(0, remSec) % 60;
        String localCadence = hopSec + "s";
        String localCountdown = String.format("%02d:%02d", remMin, remSeconds);

        try {
            String target = serverUrl;
            if (target == null || target.trim().length() == 0) {
                // If offline, still update UI with on-device cadence & countdown!
                activity.runOnUiThread(new UiUpdateTask(
                        activity, "On-Device Roaming", "", "idle",
                        "185.220.101.98", localCadence, localCountdown, 1,
                        "• This Phone (Autonomous On-Device Roaming)"
                ));
                return;
            }
            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                target = "https://" + target;
            }
            URL url = new URL(target + "/api/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "PhantomGPS-Android/2.0");
            conn.setRequestProperty("Bypass-Tunnel-Reminder", "true");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject d = new JSONObject(sb.toString());
                String city = "Zurich, Switzerland";
                String coords = "lat: 47.3769, lon: 8.5417";
                String routeStatus = "idle";
                String outboundIp = "185.220.101.98";
                String cadence = localCadence;
                String countdown = localCountdown;
                int activeUsers = 1;
                StringBuilder devSummary = new StringBuilder();

                if (d.has("rotationIntervalSeconds")) {
                    int serverInterval = d.getInt("rotationIntervalSeconds");
                    cadence = serverInterval + "s";
                    MockLocationService.setHopInterval(serverInterval);
                }

                if (d.has("countdownSeconds")) {
                    int sec = d.getInt("countdownSeconds");
                    int m = sec / 60;
                    int s = sec % 60;
                    countdown = String.format("%02d:%02d", m, s);
                }

                if (d.has("activeRoute") && !d.isNull("activeRoute")) {
                    JSONObject r = d.getJSONObject("activeRoute");
                    int prog = r.optInt("progressPercent", 0);
                    int spd = r.optInt("speedKmh", 60);
                    String mode = r.optString("mode", "vehicle");
                    double lat = r.optDouble("currentLat", 0.0);
                    double lon = r.optDouble("currentLon", 0.0);
                    routeStatus = "MOVING: " + prog + "% (" + spd + " km/h " + mode + ")";
                    coords = "lat: " + String.format("%.4f", lat) + ", lon: " + String.format("%.4f", lon);
                    if (d.has("currentNode")) {
                        JSONObject n = d.getJSONObject("currentNode");
                        city = n.optString("city", "Route Active");
                    }
                } else if (d.has("currentNode")) {
                    JSONObject n = d.getJSONObject("currentNode");
                    String flag = n.optString("flag", "📍");
                    city = flag + " " + n.optString("city", "") + ", " + n.optString("country", "");
                    coords = "lat: " + String.format("%.4f", n.optDouble("lat", 0.0)) + ", lon: " + String.format("%.4f", n.optDouble("lon", 0.0));
                    outboundIp = n.optString("virtualIp", outboundIp);
                }

                if (d.has("verifiedPublicIp") && !d.isNull("verifiedPublicIp")) {
                    outboundIp = d.getString("verifiedPublicIp");
                }

                if (d.has("connectedUsers")) {
                    JSONArray usersArr = d.getJSONArray("connectedUsers");
                    activeUsers = Math.max(1, usersArr.length());
                    for (int i = 0; i < usersArr.length(); i++) {
                        JSONObject u = usersArr.getJSONObject(i);
                        String devType = u.optString("deviceType", "Device");
                        String ip = u.optString("ip", "");
                        String lastSeen = u.optString("lastSeenFormatted", "");
                        devSummary.append("• ").append(devType).append(" (").append(ip).append(") ").append(lastSeen).append("\n");
                    }
                }

                if (devSummary.length() == 0) {
                    devSummary.append("• Android Phone (Phantom APK) - Active & Receiving GPS");
                }

                activity.runOnUiThread(new UiUpdateTask(
                        activity, city, coords, routeStatus,
                        outboundIp, cadence, countdown, activeUsers, devSummary.toString().trim()
                ));
            }
            conn.disconnect();
        } catch (Exception ignored) {
            // Keep on-device countdown moving smoothly even if tunnel has 1-second network jitter!
            activity.runOnUiThread(new UiUpdateTask(
                    activity, activity.getCurrentCityText(), activity.getCurrentCoordsText(), "idle",
                    "185.220.101.98", localCadence, localCountdown, 1,
                    "• This Phone (Autonomous On-Device Roaming)"
            ));
        }
    }
}
