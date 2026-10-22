package ai.omnirush.chameleon;

public interface RouteListener {
    void onRouteStarted(String origin, String dest, double distKm);
    void onProgress(PhoneRouteEngine.RouteStatus status);
    void onCompleted();
    void onError(String message);
}

