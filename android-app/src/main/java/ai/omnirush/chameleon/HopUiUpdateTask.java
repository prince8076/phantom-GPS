package ai.omnirush.chameleon;

public class HopUiUpdateTask implements Runnable {
    private final MainActivity activity;
    private final String cityName;
    private final double lat;
    private final double lon;

    public HopUiUpdateTask(MainActivity activity, String cityName, double lat, double lon) {
        this.activity = activity;
        this.cityName = cityName;
        this.lat = lat;
        this.lon = lon;
    }

    @Override
    public void run() {
        activity.applyHopUi(cityName, lat, lon);
    }
}

