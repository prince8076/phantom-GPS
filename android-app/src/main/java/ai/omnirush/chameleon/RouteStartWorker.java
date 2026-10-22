package ai.omnirush.chameleon;

public class RouteStartWorker implements Runnable {
    private final MainActivity activity;
    private final String baseUrl;
    private final String origin;
    private final String dest;
    private final String mode;

    public RouteStartWorker(MainActivity activity, String baseUrl, String origin, String dest, String mode) {
        this.activity = activity;
        this.baseUrl = baseUrl;
        this.origin = origin;
        this.dest = dest;
        this.mode = mode;
    }

    @Override
    public void run() {
        activity.showToastOnUiThread("Snapping road route: " + origin + " -> " + dest);
        PhoneRouteEngine.startRouteAsync(origin, dest, mode, baseUrl, new RouteCallback(activity, dest));

        // If serverUrl is configured and reachable, notify it as well, but do not block the phone
        if (baseUrl != null && baseUrl.trim().length() > 0) {
            try {
                String target = baseUrl.trim();
                if (!target.startsWith("http://") && !target.startsWith("https://")) {
                    target = "https://" + target;
                }
                java.net.URL url = new java.net.URL(target + "/api/route/start");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                org.json.JSONObject body = new org.json.JSONObject();
                body.put("origin", origin);
                body.put("destination", dest);
                body.put("mode", mode);

                java.io.OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {
                // Desktop server not required!
            }
        }
    }
}
