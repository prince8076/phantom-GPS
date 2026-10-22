package ai.omnirush.chameleon.vpn;

import ai.omnirush.chameleon.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Profiles tab: one-tap connection profiles (Streaming, Gaming,
 * Anti-censorship, Max security, Work) plus user-created ones.
 * Custom profiles persist in SharedPreferences as JSON.
 */
public class ProfilesActivity extends Activity {

    /** Connection profile. */
    public static class Profile {
        public String name;
        public String subtitle;
        public String icon;
        public String target;   // "fastest" | "secure-core" | "country:<id>" | "server:<id>"
        public boolean builtin;

        public Profile(String name, String subtitle, String icon,
                       String target, boolean builtin) {
            this.name = name; this.subtitle = subtitle;
            this.icon = icon; this.target = target; this.builtin = builtin;
        }

        public JSONObject toJson() throws org.json.JSONException {
            JSONObject o = new JSONObject();
            o.put("name", name); o.put("subtitle", subtitle);
            o.put("icon", icon); o.put("target", target);
            return o;
        }

        public static Profile fromJson(JSONObject o) throws org.json.JSONException {
            return new Profile(o.optString("name"), o.optString("subtitle"),
                    o.optString("icon", "📋"), o.optString("target", "fastest"), false);
        }
    }

    private static final String PREFS_PROFILES = "phantom_vpn_profiles";
    private static final String KEY_CUSTOM = "custom_profiles";

    private ListView list;
    private ProfileAdapter adapter;
    private final List<Profile> profiles = new ArrayList<Profile>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profiles);

        list = findViewById(R.id.profile_list);
        adapter = new ProfileAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view,
                                              int position, long id) {
                connectWithProfile(profiles.get(position));
            }
        });

        Button create = findViewById(R.id.btn_create_profile);
        create.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showCreateDialog(); }
        });

        BottomNavHelper.wire(this, BottomNavHelper.TAB_PROFILES);
        loadProfiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfiles();
    }

    private void loadProfiles() {
        profiles.clear();
        profiles.add(new Profile("Streaming", "Fastest streaming server",
                "\uD83C\uDFAC", "fastest", true));
        profiles.add(new Profile("Gaming", "Lowest latency server",
                "\uD83C\uDFAE", "fastest", true));
        profiles.add(new Profile("Anti-censorship", "Best route around blocks",
                "\uD83D\uDD76", "fastest", true));
        profiles.add(new Profile("Max security", "Via Secure Core multi-hop",
                "\uD83D\uDEE1", "secure-core", true));
        profiles.add(new Profile("Work", "Stable everyday server",
                "\uD83D\uDCBC", "fastest", true));
        profiles.addAll(readCustom());
        adapter.notifyDataSetChanged();
    }

    private List<Profile> readCustom() {
        List<Profile> out = new ArrayList<Profile>();
        String raw = getSharedPreferences(PREFS_PROFILES, MODE_PRIVATE)
                .getString(KEY_CUSTOM, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                out.add(Profile.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void saveCustom(List<Profile> custom) {
        try {
            JSONArray arr = new JSONArray();
            for (Profile p : custom) arr.put(p.toJson());
            getSharedPreferences(PREFS_PROFILES, MODE_PRIVATE).edit()
                    .putString(KEY_CUSTOM, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void showCreateDialog() {
        final EditText input = new EditText(this);
        input.setHint("Profile name, e.g. Travel");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF9A93B8);
        LinearLayout pad = new LinearLayout(this);
        pad.setPadding(48, 24, 48, 8);
        pad.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Create profile")
                .setView(pad)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) name = "Custom profile";
                        List<Profile> custom = readCustom();
                        custom.add(new Profile(name, "Fastest server", "📋", "fastest", false));
                        saveCustom(custom);
                        loadProfiles();
                        Toast.makeText(ProfilesActivity.this,
                                "Profile created", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void connectWithProfile(final Profile profile) {
        Toast.makeText(this, "Connecting with " + profile.name + "…",
                Toast.LENGTH_SHORT).show();
        VpnConnector.prepareConfig(this, null, new VpnConnector.PrepareCallback() {
            @Override public void onReady(VpnConfig config) {
                if ("secure-core".equals(profile.target)) {
                    config.secureCore.enabled = true;
                }
                VpnSettings.applyTo(ProfilesActivity.this, config);
                try {
                    Intent home = new Intent(ProfilesActivity.this, HomeActivity.class);
                    home.putExtra("connect_config", config.toJsonString());
                    home.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(home);
                } catch (Exception e) {
                    Toast.makeText(ProfilesActivity.this,
                            "Could not start connection: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onError(String message) {
                Toast.makeText(ProfilesActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private class ProfileAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(ProfilesActivity.this);

        @Override public int getCount() { return profiles.size(); }
        @Override public Object getItem(int position) { return profiles.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) v = inflater.inflate(R.layout.item_profile, parent, false);
            final Profile p = profiles.get(position);
            ((TextView) v.findViewById(R.id.profile_icon)).setText(p.icon);
            ((TextView) v.findViewById(R.id.profile_name)).setText(p.name);
            ((TextView) v.findViewById(R.id.profile_sub)).setText(p.subtitle);
            View more = v.findViewById(R.id.profile_more);
            if (p.builtin) {
                more.setVisibility(View.INVISIBLE);
            } else {
                more.setVisibility(View.VISIBLE);
                more.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { confirmDelete(p); }
                });
            }
            return v;
        }
    }

    private void confirmDelete(final Profile profile) {
        new AlertDialog.Builder(this)
                .setTitle("Delete profile?")
                .setMessage("Remove \"" + profile.name + "\"?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        List<Profile> custom = readCustom();
                        for (int i = 0; i < custom.size(); i++) {
                            if (custom.get(i).name.equals(profile.name)) {
                                custom.remove(i);
                                break;
                            }
                        }
                        saveCustom(custom);
                        loadProfiles();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
