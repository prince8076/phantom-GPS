package ai.omnirush.chameleon;

public class ToastRunnable implements Runnable {
    private final MainActivity activity;
    private final String msg;

    public ToastRunnable(MainActivity activity, String msg) {
        this.activity = activity;
        this.msg = msg;
    }

    @Override
    public void run() {
        android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}

