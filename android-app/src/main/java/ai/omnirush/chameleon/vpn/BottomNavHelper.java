package ai.omnirush.chameleon.vpn;

import ai.omnirush.chameleon.R;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Wires the shared bottom navigation (bottom_nav.xml) for the tab activities:
 * Home · Countries · Profiles · Settings. Highlights the active tab with the
 * amethyst pill and routes taps without stacking duplicate activities.
 *
 * <p>Plain-framework helper (no AndroidX). Clean-room implementation.
 */
public final class BottomNavHelper {

    public static final int TAB_HOME = 0;
    public static final int TAB_COUNTRIES = 1;
    public static final int TAB_PROFILES = 2;
    public static final int TAB_SETTINGS = 3;

    private BottomNavHelper() {}

    public static void wire(final Activity activity, int activeTab) {
        int[] tabs = {R.id.nav_home, R.id.nav_countries, R.id.nav_profiles, R.id.nav_settings};
        int[] icons = {R.id.nav_home_icon, R.id.nav_countries_icon,
                       R.id.nav_profiles_icon, R.id.nav_settings_icon};
        int[] labels = {R.id.nav_home_label, R.id.nav_countries_label,
                        R.id.nav_profiles_label, R.id.nav_settings_label};
        final Class<?>[] targets = {HomeActivity.class, CountriesActivity.class,
                                    ProfilesActivity.class, SettingsActivity.class};

        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            LinearLayout tab = activity.findViewById(tabs[i]);
            if (tab == null) continue;
            boolean active = (i == activeTab);
            tab.setSelected(active);
            TextView icon = activity.findViewById(icons[i]);
            TextView label = activity.findViewById(labels[i]);
            int color = active ? 0xFF8B5CF6 : 0xFF9A93B8;
            if (icon != null) icon.setTextColor(color);
            if (label != null) label.setTextColor(color);
            if (!active) {
                tab.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Intent intent = new Intent(activity, targets[index]);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        activity.startActivity(intent);
                        activity.overridePendingTransition(0, 0);
                    }
                });
            } else {
                tab.setOnClickListener(null);
                tab.setClickable(false);
            }
        }
    }
}
