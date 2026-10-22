package ai.omnirush.chameleon;

import java.util.List;

public class SuggestionUpdateTask implements Runnable {
    private final MainActivity activity;
    private final List<String> results;
    private final android.widget.ArrayAdapter<String> adapter;

    public SuggestionUpdateTask(MainActivity activity, List<String> results, android.widget.ArrayAdapter<String> adapter) {
        this.activity = activity;
        this.results = results;
        this.adapter = adapter;
    }

    @Override
    public void run() {
        activity.applyUpdatedSuggestions(results, adapter);
    }
}

