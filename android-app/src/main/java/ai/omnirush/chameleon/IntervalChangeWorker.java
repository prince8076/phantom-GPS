package ai.omnirush.chameleon;

public class IntervalChangeWorker implements Runnable {
    private final String baseUrl;
    private final int seconds;

    public IntervalChangeWorker(String baseUrl, int seconds) {
        this.baseUrl = baseUrl;
        this.seconds = seconds;
    }

    @Override
    public void run() {
        if (baseUrl == null || baseUrl.trim().length() == 0) return;
        try {
            String target = baseUrl.trim();
            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                target = "https://" + target;
            }
            java.net.URL url = new java.net.URL(target + "/api/settings/interval");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            org.json.JSONObject body = new org.json.JSONObject();
            body.put("seconds", seconds);

            java.io.OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception ignored) {}
    }
}

