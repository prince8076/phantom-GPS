package ai.omnirush.chameleon;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * On-device autonomous road navigation and simulation engine.
 * Directly performs turn-by-turn road snapping on Android.
 * Uses both backend API and fallback geocoding to resolve any city, state, or country.
 */
public class PhoneRouteEngine {
    private static final String TAG = "PhoneRouteEngine";

    public static class LatLon {
        public double lat;
        public double lon;

        public LatLon(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static class RouteStatus {
        public boolean isActive = false;
        public String originName = "";
        public String destName = "";
        public String mode = "vehicle";
        public int speedKmh = 60;
        public double currentLat = 0.0;
        public double currentLon = 0.0;
        public float currentBearing = 0.0f;
        public float currentSpeedMs = 0.0f;
        public int progressPercent = 0;
        public double totalDistanceMeters = 0;
        public double coveredDistanceMeters = 0;
        public boolean isRoadSnapped = true;
    }

    private static volatile RouteStatus activeStatus = new RouteStatus();
    private static volatile boolean shouldStop = false;
    private static Thread routeSimulationThread = null;

    public static RouteStatus getStatus() {
        return activeStatus;
    }

    public static boolean isRunning() {
        return activeStatus.isActive;
    }

    public static void stopRoute() {
        shouldStop = true;
        if (routeSimulationThread != null) {
            routeSimulationThread.interrupt();
        }
        activeStatus.isActive = false;
    }

    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0; // meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return R * c;
    }

    public static float calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
                Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon);
        return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0);
    }

    public static LatLon geocode(String query, String serverUrl) {
        if (query == null || query.trim().length() == 0) return null;

        // 1. First check if it's already "lat, lon"
        try {
            if (query.contains(",")) {
                String[] parts = query.split(",");
                if (parts.length == 2) {
                    double lat = Double.parseDouble(parts[0].trim());
                    double lon = Double.parseDouble(parts[1].trim());
                    return new LatLon(lat, lon);
                }
            }
        } catch (Exception ignored) {}

        // 2. Query backend geocode endpoint if serverUrl is available
        if (serverUrl != null && serverUrl.trim().length() > 0) {
            try {
                String target = serverUrl.trim();
                if (!target.startsWith("http://") && !target.startsWith("https://")) {
                    target = "https://" + target;
                }
                String encoded = URLEncoder.encode(query.trim(), "UTF-8");
                URL url = new URL(target + "/api/geocode?q=" + encoded);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "PhantomGPS-Android/2.0");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    conn.disconnect();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.has("results")) {
                        JSONArray arr = json.getJSONArray("results");
                        if (arr.length() > 0) {
                            JSONObject item = arr.getJSONObject(0);
                            return new LatLon(item.getDouble("lat"), item.getDouble("lon"));
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Backend geocode error: " + e.getMessage());
            }
        }

        // 3. Fallback: Check built-in global cities
        String lowerQuery = query.toLowerCase();
        for (String[] node : MainActivity.GLOBAL_NODES) {
            String label = node[0].toLowerCase();
            if (label.contains(lowerQuery)) {
                return new LatLon(Double.parseDouble(node[1]), Double.parseDouble(node[2]));
            }
        }

        return null;
    }

    public static List<LatLon> fetchRoadGeometry(LatLon origin, LatLon dest, String mode) {
        List<LatLon> waypoints = new ArrayList<>();
        try {
            String profile = (mode.equals("foot") || mode.equals("walking") || mode.equals("bicycle")) ? "foot" : "driving";
            String urlStr = "https://router.project-osrm.org/route/v1/" + profile + "/" +
                    origin.lon + "," + origin.lat + ";" + dest.lon + "," + dest.lat +
                    "?overview=full&geometries=geojson";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "OmniRush-PhantomGPS-Android/2.0");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                JSONObject data = new JSONObject(sb.toString());
                if (data.has("routes")) {
                    JSONArray routes = data.getJSONArray("routes");
                    if (routes.length() > 0) {
                        JSONObject r0 = routes.getJSONObject(0);
                        JSONObject geom = r0.getJSONObject("geometry");
                        JSONArray coords = geom.getJSONArray("coordinates");
                        for (int i = 0; i < coords.length(); i++) {
                            JSONArray c = coords.getJSONArray(i);
                            waypoints.add(new LatLon(c.getDouble(1), c.getDouble(0)));
                        }
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "OSRM road fetch failed: " + e.getMessage());
        }

        // If road snapping failed or offline, fall back to linear interpolation
        if (waypoints.isEmpty()) {
            waypoints = generateDirectWaypoints(origin, dest, 50);
        }

        return waypoints;
    }

    private static List<LatLon> generateDirectWaypoints(LatLon origin, LatLon dest, int steps) {
        List<LatLon> pts = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            double frac = (double) i / steps;
            pts.add(new LatLon(
                    origin.lat + (dest.lat - origin.lat) * frac,
                    origin.lon + (dest.lon - origin.lon) * frac
            ));
        }
        return pts;
    }

    public static void startRouteAsync(final String originQuery, final String destQuery, final String mode, final String serverUrl, final RouteListener listener) {
        stopRoute();
        shouldStop = false;

        routeSimulationThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LatLon orig = geocode(originQuery, serverUrl);
                    LatLon dest = geocode(destQuery, serverUrl);

                    if (orig == null || dest == null) {
                        if (listener != null) listener.onError("Could not locate " + (orig == null ? originQuery : destQuery));
                        return;
                    }

                    List<LatLon> waypoints = fetchRoadGeometry(orig, dest, mode);
                    if (waypoints.isEmpty()) {
                        if (listener != null) listener.onError("Could not calculate road path");
                        return;
                    }

                    // Calculate speed
                    int speedKmh = 60;
                    if ("foot".equalsIgnoreCase(mode) || "walking".equalsIgnoreCase(mode)) speedKmh = 5;
                    else if ("bicycle".equalsIgnoreCase(mode)) speedKmh = 18;
                    else if ("flight".equalsIgnoreCase(mode)) speedKmh = 500;

                    float speedMs = (float) (speedKmh / 3.6);

                    // Compute total distance
                    double totalDist = 0;
                    for (int i = 0; i < waypoints.size() - 1; i++) {
                        totalDist += calculateDistance(waypoints.get(i).lat, waypoints.get(i).lon, waypoints.get(i + 1).lat, waypoints.get(i + 1).lon);
                    }

                    activeStatus.isActive = true;
                    activeStatus.originName = originQuery;
                    activeStatus.destName = destQuery;
                    activeStatus.mode = mode;
                    activeStatus.speedKmh = speedKmh;
                    activeStatus.currentSpeedMs = speedMs;
                    activeStatus.totalDistanceMeters = totalDist;
                    activeStatus.coveredDistanceMeters = 0;
                    activeStatus.currentLat = waypoints.get(0).lat;
                    activeStatus.currentLon = waypoints.get(0).lon;
                    activeStatus.currentBearing = 0;
                    activeStatus.progressPercent = 0;

                    if (listener != null) {
                        listener.onRouteStarted(originQuery, destQuery, totalDist / 1000.0);
                    }

                    // Step through waypoints second by second
                    int currentIndex = 0;
                    double currentLat = waypoints.get(0).lat;
                    double currentLon = waypoints.get(0).lon;

                    while (!shouldStop && currentIndex < waypoints.size() - 1) {
                        long loopStart = System.currentTimeMillis();

                        double targetLat = waypoints.get(currentIndex + 1).lat;
                        double targetLon = waypoints.get(currentIndex + 1).lon;

                        double distToNext = calculateDistance(currentLat, currentLon, targetLat, targetLon);
                        float bearing = calculateBearing(currentLat, currentLon, targetLat, targetLon);

                        if (distToNext <= speedMs) {
                            // Arrived at next waypoint
                            currentLat = targetLat;
                            currentLon = targetLon;
                            activeStatus.coveredDistanceMeters += distToNext;
                            currentIndex++;
                        } else {
                            // Move step towards target
                            double fraction = speedMs / distToNext;
                            currentLat += (targetLat - currentLat) * fraction;
                            currentLon += (targetLon - currentLon) * fraction;
                            activeStatus.coveredDistanceMeters += speedMs;
                        }

                        activeStatus.currentLat = currentLat;
                        activeStatus.currentLon = currentLon;
                        activeStatus.currentBearing = bearing;
                        int progress = (totalDist > 0) ? (int) Math.min(99, (activeStatus.coveredDistanceMeters / totalDist) * 100) : 0;
                        activeStatus.progressPercent = progress;

                        // Notify mock location service directly
                        MockLocationService.setStaticLocation(
                                "Moving (" + speedKmh + " km/h) " + progress + "%",
                                currentLat,
                                currentLon,
                                speedMs,
                                bearing
                        );

                        if (listener != null) {
                            listener.onProgress(activeStatus);
                        }

                        long elapsed = System.currentTimeMillis() - loopStart;
                        long sleepTime = Math.max(10, 1000 - elapsed);
                        Thread.sleep(sleepTime);
                    }

                    if (!shouldStop) {
                        activeStatus.progressPercent = 100;
                        activeStatus.isActive = false;
                        MockLocationService.setStaticLocation(destQuery, dest.lat, dest.lon, 0.0f, 0.0f);
                        if (listener != null) listener.onCompleted();
                    }
                } catch (InterruptedException ie) {
                    Log.d(TAG, "Route thread interrupted");
                } catch (Exception e) {
                    Log.e(TAG, "Route simulation error: " + e.getMessage());
                    if (listener != null) listener.onError("Simulation error: " + e.getMessage());
                } finally {
                    activeStatus.isActive = false;
                }
            }
        });
        routeSimulationThread.start();
    }
}
