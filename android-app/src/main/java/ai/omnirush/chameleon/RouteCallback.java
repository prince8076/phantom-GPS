package ai.omnirush.chameleon;

public class RouteCallback implements RouteListener {
    private final MainActivity activity;
    private final String destination;

    public RouteCallback(MainActivity activity, String destination) {
        this.activity = activity;
        this.destination = destination;
    }

    @Override
    public void onRouteStarted(String origin, String dest, double distKm) {
        activity.showToastOnUiThread(String.format("Route started! %.1f km along roads", distKm));
    }

    @Override
    public void onProgress(PhoneRouteEngine.RouteStatus status) {
        // MockLocationService and StatusWorker push coordinates and UI updates
    }

    @Override
    public void onCompleted() {
        activity.showToastOnUiThread("Arrived at destination: " + destination);
    }

    @Override
    public void onError(String message) {
        activity.showToastOnUiThread("Route error: " + message);
    }
}

