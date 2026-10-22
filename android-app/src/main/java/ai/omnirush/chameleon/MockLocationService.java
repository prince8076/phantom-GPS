package ai.omnirush.chameleon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MockLocationService extends Service {
    private static final String TAG = "PhantomGPS";
    private static final String CHANNEL_ID = "PhantomGpsChannel";

    private LocationManager locationManager;
    private boolean isRunning = false;
    private String serverUrl = "https://scores-ide-bald-cliff.trycloudflare.com";

    private static double currentLat = 47.3769;
    private static double currentLon = 8.5417;
    private static float currentSpeed = 0.0f;
    private static float currentBearing = 0.0f;
    private static String currentCity = "Zurich, Switzerland";

    // Autonomous On-Device Cadence Hopping Timer
    private static int hopIntervalSeconds = 300; // default 5 minutes
    private static int localSecondsRemaining = 300;
    private static int globalNodeIndex = 0;

    public static void setHopInterval(int seconds) {
        if (seconds >= 5) {
            hopIntervalSeconds = seconds;
            localSecondsRemaining = seconds;
        }
    }

    public static int getHopIntervalSeconds() {
        return hopIntervalSeconds;
    }

    public static int getSecondsRemaining() {
        return localSecondsRemaining;
    }

    public static void setStaticLocation(String city, double lat, double lon) {
        setStaticLocation(city, lat, lon, 0.0f, 0.0f);
    }

    public static void setStaticLocation(String city, double lat, double lon, float speed, float bearing) {
        currentCity = city;
        currentLat = lat;
        currentLon = lon;
        currentSpeed = speed;
        currentBearing = bearing;
        localSecondsRemaining = hopIntervalSeconds; // Reset timer when a new location is chosen
    }

    public static void doAutonomousHop() {
        globalNodeIndex = (globalNodeIndex + 1) % MainActivity.GLOBAL_NODES.length;
        String[] node = MainActivity.GLOBAL_NODES[globalNodeIndex];
        currentCity = node[0];
        currentLat = Double.parseDouble(node[1]);
        currentLon = Double.parseDouble(node[2]);
        currentSpeed = 0.0f;
        currentBearing = 0.0f;
        localSecondsRemaining = hopIntervalSeconds;
    }

    private Thread mockThread;

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getStringExtra("serverUrl") != null) {
            String url = intent.getStringExtra("serverUrl").trim();
            if (url.length() > 0) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                serverUrl = url;
            }
        }

        Notification notification = buildNotification("Phantom GPS Active: " + currentCity);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(101, notification);
            }
        } catch (Exception e) {
            try {
                startForeground(101, notification);
            } catch (Exception ignored) {}
        }

        if (!isRunning) {
            isRunning = true;
            setupTestProviders();
            mockThread = new Thread(new MockLocationRunner(this));
            mockThread.start();
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Phantom GPS Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Mock Location Provider for Phantom GPS");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder.setContentTitle("Phantom GPS · Active")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();
    }

    public void setupTestProviders() {
        registerSingleProvider(LocationManager.GPS_PROVIDER);
        registerSingleProvider(LocationManager.NETWORK_PROVIDER);
        registerSingleProvider("fused");
    }

    private void registerSingleProvider(String providerName) {
        try {
            try {
                locationManager.removeTestProvider(providerName);
            } catch (Exception ignored) {}

            locationManager.addTestProvider(
                    providerName,
                    false,                          // requiresNetwork
                    false,                          // requiresSatellite
                    false,                          // requiresCell
                    false,                          // hasMonetaryCost
                    true,                           // supportsAltitude
                    true,                           // supportsSpeed
                    true,                           // supportsBearing
                    Criteria.POWER_LOW,             // powerRequirement
                    Criteria.ACCURACY_FINE          // accuracy
            );
            locationManager.setTestProviderEnabled(providerName, true);
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException for " + providerName + ": " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error setting provider " + providerName + ": " + e.getMessage());
        }
    }

    public void runMockLoop() {
        while (isRunning) {
            try {
                // Step on-device countdown timer every second
                if (!PhoneRouteEngine.isRunning()) {
                    localSecondsRemaining--;
                    if (localSecondsRemaining <= 0) {
                        doAutonomousHop();
                    }
                }

                // If on-device local route is NOT active, optionally sync with server if available
                if (!PhoneRouteEngine.isRunning() && serverUrl != null && serverUrl.trim().length() > 0) {
                    fetchActiveCoords();
                }

                // Push synchronized location across all 3 Android providers
                pushLocation(LocationManager.GPS_PROVIDER, currentLat, currentLon, currentSpeed, currentBearing);
                pushLocation(LocationManager.NETWORK_PROVIDER, currentLat, currentLon, currentSpeed, currentBearing);
                pushLocation("fused", currentLat, currentLon, currentSpeed, currentBearing);

                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                break;
            } catch (Exception e) {
                Log.e(TAG, "Mock loop error: " + e.getMessage());
            }
        }
    }

    private void fetchActiveCoords() {
        try {
            URL url = new URL(serverUrl + "/api/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "PhantomGPS-Android/2.0");
            conn.setRequestProperty("Bypass-Tunnel-Reminder", "true");
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());

                // Read server's countdown seconds and interval so phone is 100% in sync
                if (json.has("rotationIntervalSeconds")) {
                    hopIntervalSeconds = json.getInt("rotationIntervalSeconds");
                }
                if (json.has("countdownSeconds")) {
                    localSecondsRemaining = json.getInt("countdownSeconds");
                }

                // Check if an active dynamic route (vehicle, walk, flight) is currently moving
                if (!PhoneRouteEngine.isRunning()) {
                    if (json.has("activeRoute") && !json.isNull("activeRoute")) {
                        JSONObject route = json.getJSONObject("activeRoute");
                        currentLat = route.getDouble("currentLat");
                        currentLon = route.getDouble("currentLon");
                        currentBearing = (float) route.optDouble("currentBearing", 0.0);
                        int speedKmh = route.optInt("speedKmh", 60);
                        currentSpeed = speedKmh / 3.6f;
                        currentCity = "Moving (" + speedKmh + " km/h) " + route.optInt("progressPercent", 0) + "%";
                    } else if (json.has("currentNode")) {
                        JSONObject node = json.getJSONObject("currentNode");
                        currentLat = node.getDouble("lat");
                        currentLon = node.getDouble("lon");
                        currentSpeed = 0.0f;
                        currentBearing = 0.0f;
                        String flag = node.optString("flag", "📍");
                        currentCity = flag + " " + node.getString("city") + ", " + node.getString("country");
                    }
                }

                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(101, buildNotification("Active: " + currentCity));
            }
            conn.disconnect();
        } catch (Exception ignored) {}
    }

    private void pushLocation(String provider, double lat, double lon, float speed, float bearing) {
        try {
            Location mock = new Location(provider);
            mock.setLatitude(lat);
            mock.setLongitude(lon);
            mock.setAltitude(25.0);
            mock.setSpeed(speed);
            mock.setBearing(bearing);
            mock.setAccuracy(2.0f);
            mock.setTime(System.currentTimeMillis());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mock.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mock.setBearingAccuracyDegrees(0.1f);
                mock.setVerticalAccuracyMeters(1.0f);
                mock.setSpeedAccuracyMetersPerSecond(0.1f);
            }

            Bundle extras = new Bundle();
            extras.putInt("satellites", 12);
            mock.setExtras(extras);

            locationManager.setTestProviderLocation(provider, mock);
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException pushing to " + provider + ": " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error pushing to " + provider + ": " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (mockThread != null) {
            mockThread.interrupt();
        }
        removeTestProviders();
        super.onDestroy();
    }

    private void removeTestProviders() {
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER);
            locationManager.removeTestProvider("fused");
        } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
