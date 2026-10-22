package ai.omnirush.chameleon;

public class StatusPollTask implements Runnable {
    private final MainActivity activity;

    public StatusPollTask(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void run() {
        activity.triggerPoll();
    }
}

