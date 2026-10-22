package ai.omnirush.chameleon;

public class UiUpdateTask implements Runnable {
    private final MainActivity activity;
    private final String city;
    private final String coords;
    private final String routeStatus;
    private final String outboundIp;
    private final String cadence;
    private final String countdown;
    private final int activeUsers;
    private final String devicesSummary;

    public UiUpdateTask(MainActivity activity, String city, String coords, String routeStatus,
                        String outboundIp, String cadence, String countdown,
                        int activeUsers, String devicesSummary) {
        this.activity = activity;
        this.city = city;
        this.coords = coords;
        this.routeStatus = routeStatus;
        this.outboundIp = outboundIp;
        this.cadence = cadence;
        this.countdown = countdown;
        this.activeUsers = activeUsers;
        this.devicesSummary = devicesSummary;
    }

    @Override
    public void run() {
        activity.updateDashboardUi(city, coords, routeStatus, outboundIp, cadence, countdown, activeUsers, devicesSummary);
    }
}
