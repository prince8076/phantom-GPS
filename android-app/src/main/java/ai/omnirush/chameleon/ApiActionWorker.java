package ai.omnirush.chameleon;

public class ApiActionWorker implements Runnable {
    private final MainActivity activity;
    private final String urlStr;
    private final String successMsg;

    public ApiActionWorker(MainActivity activity, String urlStr, String successMsg) {
        this.activity = activity;
        this.urlStr = urlStr;
        this.successMsg = successMsg;
    }

    @Override
    public void run() {
        try {
            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.getResponseCode();
            conn.disconnect();

            if (successMsg != null) {
                activity.showToastOnUiThread(successMsg);
            }
        } catch (Exception ignored) {}
    }
}

